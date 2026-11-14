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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Production HTTP client implementing the final Aulaa Keyboard API contract.
 *
 * Uses Android's built-in TLS-enabled [HttpURLConnection] and [kotlinx.serialization.json]
 * so no extra network libraries or secrets are required.
 */
class HttpAulaaApi(
    private val pairingStore: PairingStore,
) : AulaaApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun claimPairing(code: String, deviceName: String): PairingSession {
        val cleanCode = code.trim()
        val cleanDeviceName = deviceName.trim().take(100)
        if (cleanCode.isBlank()) {
            throw AulaaApiException(400, "Kode pairing wajib diisi.")
        }
        if (cleanDeviceName.isBlank()) {
            throw AulaaApiException(400, "Nama perangkat wajib diisi (maks 100 karakter).")
        }

        val bodyJson = buildJsonObject {
            put("code", cleanCode)
            put("device_name", cleanDeviceName)
        }.toString()

        val response = request(
            method = "POST",
            path = "/v1/keyboard/pairing/claim",
            body = bodyJson,
            authenticated = false,
            isPairingClaim = true,
        )

        val root = response.asObjectOrData()
        val token = root.string("device_token")
            ?: throw AulaaApiException(500, "Respons server tidak memuat device_token.")
        val tokenType = root.string("token_type") ?: "Bearer"
        val scopes = (root["scopes"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: listOf("transactions:create", "transactions:read", "projects:read", "products:read")

        val deviceObj = root["device"] as? JsonObject
        val device = PairedDeviceInfo(
            id = deviceObj?.string("id") ?: "",
            name = deviceObj?.string("name") ?: cleanDeviceName,
        )

        val merchantObj = root["merchant"] as? JsonObject
        val merchant = PairedMerchantInfo(
            id = merchantObj?.string("id") ?: "",
            name = merchantObj?.string("name") ?: "Merchant Aulaa",
        )

        val projects = (root["projects"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.toProject() }
            ?: emptyList()

        val payBaseUrl = root.string("pay_base_url")
            ?.trim()
            ?.trimEnd('/')
            ?.ifBlank { DEFAULT_PAY_BASE_URL }
            ?: DEFAULT_PAY_BASE_URL

        val session = PairingSession(
            deviceToken = token,
            tokenType = tokenType,
            scopes = scopes,
            device = device,
            merchant = merchant,
            projects = projects,
            payBaseUrl = payBaseUrl,
            apiBaseUrl = pairingStore.apiBaseUrl(),
        )
        pairingStore.saveSession(session)
        return session
    }

    override suspend fun listProjects(): List<Project> {
        val response = request(
            method = "GET",
            path = "/v1/projects",
            authenticated = true,
        )
        val array = response.asArray()
        val projects = array.mapNotNull { (it as? JsonObject)?.toProject() }
        if (projects.isNotEmpty()) {
            pairingStore.updateProjects(projects)
        }
        return projects
    }

    override suspend fun createTransactionLink(
        projectId: String,
        amount: Long,
        orderId: String?,
    ): PaymentLink {
        val bodyJson = buildJsonObject {
            put("project_id", projectId)
            put("amount", amount)
            if (!orderId.isNullOrBlank()) {
                put("order_id", orderId.trim())
            }
            // payment_method intentionally omitted so the customer chooses on checkout page
        }.toString()

        val response = request(
            method = "POST",
            path = "/v1/transactions",
            body = bodyJson,
            authenticated = true,
        )
        val obj = response.asObjectOrData()
        return obj.toPaymentLink(defaultAmount = amount, payBaseUrl = pairingStore.payBaseUrl())
    }

    override suspend fun transactionStatus(id: String): PaymentLink {
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val response = request(
            method = "GET",
            path = "/v1/transactions/$encodedId",
            authenticated = true,
        )
        val obj = response.asObjectOrData()
        return obj.toPaymentLink(defaultAmount = 0L, payBaseUrl = pairingStore.payBaseUrl())
    }

    override suspend fun listProducts(projectId: String?, query: String?): List<Product> {
        val path = if (!projectId.isNullOrBlank()) {
            val encodedPrj = URLEncoder.encode(projectId, "UTF-8")
            "/v1/products?project_id=$encodedPrj"
        } else {
            "/v1/products"
        }

        val response = request(
            method = "GET",
            path = path,
            authenticated = true,
        )
        val payBaseUrl = pairingStore.payBaseUrl()
        val products = response.asArray()
            .mapNotNull { (it as? JsonObject)?.toProduct(payBaseUrl) }
            .filter { it.isActive }

        return if (query.isNullOrBlank()) {
            products
        } else {
            products.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        authenticated: Boolean = true,
        isPairingClaim: Boolean = false,
    ): JsonElement = withContext(Dispatchers.IO) {
        val baseUrl = pairingStore.apiBaseUrl().trimEnd('/')
        val url = URL("$baseUrl$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (authenticated) {
                val token = pairingStore.deviceToken()
                    ?: throw AulaaApiException(401, "Keyboard belum terhubung. Silakan lakukan pairing terlebih dahulu.")
                setRequestProperty("Authorization", "Bearer $token")
            }
        }

        try {
            if (body != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val rawText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()

            if (status !in 200..299) {
                val serverMessage = extractErrorMessage(rawText)
                val friendly = mapHttpError(status, serverMessage, isPairingClaim)
                throw AulaaApiException(status, friendly)
            }

            if (rawText.isBlank()) {
                JsonObject(emptyMap())
            } else {
                json.parseToJsonElement(rawText)
            }
        } catch (e: AulaaApiException) {
            throw e
        } catch (e: Exception) {
            throw AulaaApiException(
                statusCode = 0,
                message = "Gagal terhubung ke server Aulaa (${e.localizedMessage ?: "cek koneksi internet"}).",
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun extractErrorMessage(rawText: String): String? {
        if (rawText.isBlank()) return null
        return runCatching {
            val el = json.parseToJsonElement(rawText)
            val obj = el as? JsonObject ?: return@runCatching null
            obj.string("message")
                ?: obj.string("error")
                ?: (obj["error"] as? JsonObject)?.string("message")
        }.getOrNull()
    }

    private fun mapHttpError(status: Int, serverMessage: String?, isPairingClaim: Boolean): String {
        if (isPairingClaim) {
            return when (status) {
                400 -> serverMessage ?: "Data perangkat atau format kode tidak valid (400)."
                401 -> "Kode pairing salah, sudah dipakai, atau sudah kedaluwarsa (401)."
                403 -> "Akun merchant dibekukan atau batas 10 perangkat aktif telah tercapai (403)."
                429 -> "Terlalu banyak percobaan klaim gagal dari IP ini. Coba lagi nanti (429)."
                else -> serverMessage ?: "Gagal klaim pairing (HTTP $status)."
            }
        }
        return when (status) {
            400 -> serverMessage ?: "Permintaan tidak valid (400)."
            401 -> "Token perangkat dicabut atau tidak sah (401). Silakan hubungkan ulang perangkat."
            403 -> serverMessage ?: "Akses ditolak untuk cakupan token ini (403)."
            429 -> "Terlalu banyak permintaan. Silakan tunggu sebentar (429)."
            else -> serverMessage ?: "Terjadi kesalahan server (HTTP $status)."
        }
    }

    private fun JsonElement.asObjectOrData(): JsonObject {
        val obj = this as? JsonObject ?: return JsonObject(emptyMap())
        return (obj["data"] as? JsonObject)
            ?: (obj["invoice"] as? JsonObject)
            ?: (obj["transaction"] as? JsonObject)
            ?: obj
    }

    private fun JsonElement.asArray(): JsonArray {
        if (this is JsonArray) return this
        val obj = this as? JsonObject ?: return JsonArray(emptyList())
        return (obj["data"] as? JsonArray)
            ?: (obj["projects"] as? JsonArray)
            ?: (obj["products"] as? JsonArray)
            ?: (obj["items"] as? JsonArray)
            ?: JsonArray(emptyList())
    }

    private fun JsonObject.toProject(): Project? {
        val id = string("id") ?: return null
        val name = string("name") ?: id
        val status = string("status") ?: "approved"
        val isProd = boolean("is_prod") ?: true
        return Project(id = id, name = name, status = status, isProd = isProd)
    }

    private fun JsonObject.toPaymentLink(defaultAmount: Long, payBaseUrl: String): PaymentLink {
        val id = string("id") ?: throw AulaaApiException(500, "Respons transaksi tidak memiliki id.")
        val amount = long("amount") ?: defaultAmount
        val status = string("status") ?: "pending"
        return PaymentLink(
            id = id,
            amount = amount,
            status = status,
            expiresAt = string("expires_at"),
            paidAt = string("paid_at"),
            orderId = string("order_id"),
            payBaseUrl = payBaseUrl,
        )
    }

    private fun JsonObject.toProduct(payBaseUrl: String): Product? {
        val id = string("id") ?: return null
        val name = string("name") ?: return null
        val price = long("price") ?: long("amount") ?: 0L
        val isActive = boolean("is_active") ?: true
        val stock = int("stock")
        return Product(
            id = id,
            projectId = string("project_id"),
            name = name,
            amount = price,
            priceLabel = formatIdr(price),
            isActive = isActive,
            stock = stock,
            payBaseUrl = payBaseUrl,
        )
    }

    private fun JsonObject.string(key: String): String? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.contentOrNull?.takeIf { it != "null" }
    }

    private fun JsonObject.long(key: String): Long? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.longOrNull ?: prim.doubleOrNull?.toLong() ?: prim.contentOrNull?.toLongOrNull()
    }

    private fun JsonObject.int(key: String): Int? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.intOrNull ?: prim.longOrNull?.toInt() ?: prim.contentOrNull?.toIntOrNull()
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.booleanOrNull ?: prim.contentOrNull?.toBooleanStrictOrNull()
    }
}
