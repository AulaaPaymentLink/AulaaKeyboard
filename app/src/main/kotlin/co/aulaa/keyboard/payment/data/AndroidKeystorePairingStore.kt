/*
 * Copyright (C) 2026 Aulaa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package co.aulaa.keyboard.payment.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed encrypted [PairingStore] using Android Keystore AES-256-GCM +
 * private [SharedPreferences].
 *
 * No secret is baked into the APK: the AES key is generated on-device inside the
 * Android Keystore (`co.aulaa.keyboard.pairing_aes_key`) and never leaves the
 * secure hardware / keystore process. Both `FlorisAppActivity` and `FlorisImeService`
 * run in the `co.aulaa.keyboard` package and share this singleton instance.
 */
class AndroidKeystorePairingStore(context: Context) : PairingStore {
    private val appContext: Context = context.applicationContext ?: context

    private val prefs: SharedPreferences by lazy {
        val storageCtx = runCatching {
            appContext.createDeviceProtectedStorageContext()
        }.getOrDefault(appContext)
        storageCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _apiBaseUrlFlow = MutableStateFlow(loadApiBaseUrl())
    override val apiBaseUrlFlow: StateFlow<String> = _apiBaseUrlFlow.asStateFlow()

    private val _sessionFlow = MutableStateFlow(loadEncryptedSession())
    override val sessionFlow: StateFlow<PairingSession?> = _sessionFlow.asStateFlow()

    override fun setApiBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/').ifBlank { DEFAULT_API_BASE_URL }
        prefs.edit().putString(KEY_API_BASE_URL, normalized).apply()
        _apiBaseUrlFlow.value = normalized
        val current = _sessionFlow.value
        if (current != null && current.apiBaseUrl != normalized) {
            saveSession(current.copy(apiBaseUrl = normalized))
        }
    }

    override fun saveSession(session: PairingSession) {
        val normalized = session.copy(
            payBaseUrl = session.payBaseUrl.trim().trimEnd('/').ifBlank { DEFAULT_PAY_BASE_URL },
            apiBaseUrl = session.apiBaseUrl.trim().trimEnd('/').ifBlank { apiBaseUrl() },
        )
        val rawJson = json.encodeToString(normalized)
        val encrypted = encrypt(rawJson)
        if (encrypted != null) {
            prefs.edit().putString(KEY_ENCRYPTED_SESSION, encrypted).apply()
        }
        _sessionFlow.value = normalized
    }

    override fun updateProjects(projects: List<Project>) {
        val current = _sessionFlow.value ?: return
        saveSession(current.copy(projects = projects))
    }

    override fun save(token: String) {
        val current = _sessionFlow.value
        val updated = current?.copy(deviceToken = token) ?: PairingSession(
            deviceToken = token,
            device = PairedDeviceInfo("local", "Android"),
            merchant = PairedMerchantInfo("local", "Merchant"),
            apiBaseUrl = apiBaseUrl(),
        )
        saveSession(updated)
    }

    override fun clear() {
        prefs.edit().remove(KEY_ENCRYPTED_SESSION).apply()
        _sessionFlow.value = null
    }

    private fun loadApiBaseUrl(): String {
        return runCatching {
            prefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL)
                ?.trim()
                ?.trimEnd('/')
                ?.ifBlank { DEFAULT_API_BASE_URL }
                ?: DEFAULT_API_BASE_URL
        }.getOrDefault(DEFAULT_API_BASE_URL)
    }

    private fun loadEncryptedSession(): PairingSession? {
        return runCatching {
            val blob = prefs.getString(KEY_ENCRYPTED_SESSION, null) ?: return null
            val decrypted = decrypt(blob) ?: return null
            json.decodeFromString<PairingSession>(decrypted)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + ciphertext.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decrypt(encoded: String): String? {
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 2) return null
            val ivSize = combined[0].toInt() and 0xFF
            if (combined.size <= 1 + ivSize) return null
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val ciphertext = combined.copyOfRange(1 + ivSize, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "co.aulaa.keyboard.pairing_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "aulaa_keyboard_pairing"
        private const val KEY_ENCRYPTED_SESSION = "encrypted_session_v1"
        private const val KEY_API_BASE_URL = "api_base_url"
    }
}
