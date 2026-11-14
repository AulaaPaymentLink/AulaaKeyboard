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
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Hardware-backed Android Keystore Key Attestation payload (v1) sent with
 * `POST /v1/keyboard/pairing/claim`.
 *
 * How it works:
 * 1. Computes a canonical challenge `aulaa-pairing-v1:<NORMALIZED_CODE>:<DEVICE_NAME>`
 *    and its SHA-256 digest (`challengeHash`).
 * 2. Generates an ephemeral ECDSA P-256 keypair inside the device's hardware-backed
 *    `AndroidKeyStore` with `.setAttestationChallenge(challengeHash)`.
 * 3. The Android TEE / Keystore issues an X.509 certificate chain (`certChain`)
 *    rooted in the Google Hardware Attestation Root CA. Inside the leaf certificate's
 *    KeyDescription extension (`OID 1.3.6.1.4.1.11129.2.1.17`), the OS/TEE embeds:
 *      - `attestationChallenge` == `SHA256(challenge)`
 *      - `attestationApplicationId` -> `package_name` (`co.aulaa.keyboard`) and
 *        `signature_digests` (the SHA-256 digest of the APK signing certificate).
 * 4. Signs the canonical challenge with the attested private key (`signature`) to
 *    prove possession of the private key matching the leaf certificate.
 *
 * Because the APK signing certificate is embedded by the device's TEE/OS into the
 * signed X.509 extension, a third-party app ("Aulaa Widget") or an unofficial
 * recompiled fork cannot forge Aulaa's release keystore SHA-256 digest.
 */
@Serializable
data class PairingAttestationPayload(
    val version: Int = 1,
    val challenge: String,
    val signature: String,
    @SerialName("cert_chain") val certChain: List<String>,
)

object AulaaAttestation {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ATTESTATION_KEY_ALIAS = "co.aulaa.keyboard.pairing_attestation_key"

    /**
     * Normalizes a pairing code the same way the backend does:
     * uppercase, stripping hyphens and whitespace.
     */
    fun normalizePairingCode(rawCode: String): String {
        return buildString(rawCode.length) {
            for (ch in rawCode) {
                if (ch != '-' && !ch.isWhitespace()) {
                    append(ch.uppercaseChar())
                }
            }
        }
    }

    /**
     * Builds the canonical challenge string bound to this specific claim request.
     */
    fun buildCanonicalChallenge(rawCode: String, deviceName: String): String {
        val cleanCode = normalizePairingCode(rawCode)
        val cleanDevice = deviceName.trim().take(100)
        return "aulaa-pairing-v1:$cleanCode:$cleanDevice"
    }

    /**
     * Generates a hardware-attested [PairingAttestationPayload] for the given
     * [code] and [deviceName].
     */
    fun generateForPairingClaim(code: String, deviceName: String): PairingAttestationPayload {
        val canonicalChallenge = buildCanonicalChallenge(code, deviceName)
        val challengeBytes = canonicalChallenge.toByteArray(Charsets.UTF_8)
        val challengeHash = MessageDigest.getInstance("SHA-256").digest(challengeBytes)

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        runCatching { keyStore.deleteEntry(ATTESTATION_KEY_ALIAS) }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            ATTESTATION_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challengeHash)
            .build()

        keyPairGenerator.initialize(spec)
        val keyPair = keyPairGenerator.generateKeyPair()

        val certs = keyStore.getCertificateChain(ATTESTATION_KEY_ALIAS)
            ?.map { cert -> Base64.encodeToString(cert.encoded, Base64.NO_WRAP) }
            .orEmpty()

        val signatureBytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(challengeBytes)
            sign()
        }
        val signatureB64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        // Clean up the ephemeral attestation key after signing
        runCatching { keyStore.deleteEntry(ATTESTATION_KEY_ALIAS) }

        return PairingAttestationPayload(
            version = 1,
            challenge = canonicalChallenge,
            signature = signatureB64,
            certChain = certs,
        )
    }

    /**
     * Returns the SHA-256 fingerprint (lowercase hex, 64 chars) of the current APK's
     * signing certificate. Useful for displaying in Settings / Devtools so the
     * developer can register their release or debug keystore fingerprint in the
     * backend's `AULAA_KEYBOARD_ALLOWED_CERT_SHA256` configuration.
     */
    @Suppress("DEPRECATION")
    fun getApkSigningCertSha256Hex(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val pkgName = context.packageName
            val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return null
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures?.firstOrNull()?.toByteArray()
            } else {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return null

            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            digest.joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
