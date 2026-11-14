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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Default API and checkout base URLs. */
const val DEFAULT_API_BASE_URL = "https://api.aulaa.co"
const val DEFAULT_PAY_BASE_URL = "https://payment.aulaa.co"

/** Legacy constant aliases kept for source compatibility. */
const val AULAA_PAY_BASE = "$DEFAULT_PAY_BASE_URL/pay/"   // one-off transaction link
const val AULAA_BUY_BASE = "$DEFAULT_PAY_BASE_URL/buy/"   // product link

/** A merchant project; needed to create a transaction link (`project_id`). */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val status: String = "approved",
    @SerialName("is_prod") val isProd: Boolean = true,
)

/** A catalog product. The keyboard only lists these — creating products stays on the web. */
@Serializable
data class Product(
    val id: String,
    @SerialName("project_id") val projectId: String? = null,
    val name: String,
    val amount: Long,
    val priceLabel: String,
    @SerialName("is_active") val isActive: Boolean = true,
    val stock: Int? = null,
    val payBaseUrl: String = DEFAULT_PAY_BASE_URL,
) {
    val buyUrl: String get() = "${payBaseUrl.trimEnd('/')}/buy/$id"
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "A"
}

/** Result of creating or reading a one-off transaction link (Aulaa "Invoice"). */
@Serializable
data class PaymentLink(
    val id: String,
    val amount: Long,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    val payBaseUrl: String = DEFAULT_PAY_BASE_URL,
) {
    val payUrl: String get() = "${payBaseUrl.trimEnd('/')}/pay/$id"
}

/** Device metadata returned by `POST /v1/keyboard/pairing/claim`. */
@Serializable
data class PairedDeviceInfo(
    val id: String,
    val name: String,
)

/** Merchant metadata returned by `POST /v1/keyboard/pairing/claim`. */
@Serializable
data class PairedMerchantInfo(
    val id: String,
    val name: String,
)

/** Complete session minted by `POST /v1/keyboard/pairing/claim`. */
@Serializable
data class PairingSession(
    @SerialName("device_token") val deviceToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scopes: List<String> = listOf(
        "transactions:create",
        "transactions:read",
        "projects:read",
        "products:read",
    ),
    val device: PairedDeviceInfo,
    val merchant: PairedMerchantInfo,
    val projects: List<Project> = emptyList(),
    @SerialName("pay_base_url") val payBaseUrl: String = DEFAULT_PAY_BASE_URL,
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
)

/** Structured exception thrown by [AulaaApi] implementations. */
class AulaaApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message) {
    val isUnauthorized: Boolean get() = statusCode == 401
    val isForbidden: Boolean get() = statusCode == 403
    val isRateLimited: Boolean get() = statusCode == 429
}

/**
 * The exact slice of the Aulaa merchant API the keyboard is allowed to touch.
 *
 * Every authenticated call carries `Authorization: Bearer <device_token>` obtained
 * via [claimPairing] (`POST /v1/keyboard/pairing/claim`). The token is scoped
 * server-side to four capabilities:
 *   - [createTransactionLink] -> `POST /v1/transactions` `{project_id, amount, order_id?}` (`transactions:create`)
 *   - [transactionStatus]     -> `GET  /v1/transactions/{id}` (`transactions:read`)
 *   - [listProjects]          -> `GET  /v1/projects` (`projects:read`)
 *   - [listProducts]          -> `GET  /v1/products?project_id=...` (`products:read`)
 */
interface AulaaApi {
    suspend fun claimPairing(code: String, deviceName: String): PairingSession
    suspend fun listProjects(): List<Project>
    suspend fun createTransactionLink(projectId: String, amount: Long, orderId: String?): PaymentLink
    suspend fun listProducts(projectId: String? = null, query: String? = null): List<Product>
    suspend fun transactionStatus(id: String): PaymentLink
}

/**
 * Storage for the scoped device token and pairing metadata minted by pairing.
 *
 * The companion-app Settings screen writes the session after a successful
 * `POST /v1/keyboard/pairing/claim`; the IME service reads it. Both live in the
 * same app package (`co.aulaa.keyboard`) and process sandbox, sharing the same
 * Android Keystore encrypted preferences.
 */
interface PairingStore {
    val sessionFlow: StateFlow<PairingSession?>
    val apiBaseUrlFlow: StateFlow<String>

    fun session(): PairingSession? = sessionFlow.value
    fun deviceToken(): String? = session()?.deviceToken
    fun payBaseUrl(): String = session()?.payBaseUrl?.ifBlank { DEFAULT_PAY_BASE_URL } ?: DEFAULT_PAY_BASE_URL
    fun apiBaseUrl(): String = apiBaseUrlFlow.value
    fun setApiBaseUrl(url: String)
    fun isPaired(): Boolean = !deviceToken().isNullOrBlank()
    fun saveSession(session: PairingSession)
    fun updateProjects(projects: List<Project>)
    fun save(token: String)
    fun clear()
}

/**
 * In-memory [PairingStore] for Compose previews and unit tests.
 */
class InMemoryPairingStore(
    initialSession: PairingSession? = null,
    initialApiBaseUrl: String = DEFAULT_API_BASE_URL,
) : PairingStore {
    override val sessionFlow = MutableStateFlow(initialSession)
    override val apiBaseUrlFlow = MutableStateFlow(initialApiBaseUrl)

    override fun setApiBaseUrl(url: String) {
        apiBaseUrlFlow.value = url.trim().trimEnd('/').ifBlank { DEFAULT_API_BASE_URL }
    }

    override fun saveSession(session: PairingSession) {
        sessionFlow.value = session
    }

    override fun updateProjects(projects: List<Project>) {
        val current = sessionFlow.value ?: return
        sessionFlow.value = current.copy(projects = projects)
    }

    override fun save(token: String) {
        val current = sessionFlow.value
        sessionFlow.value = current?.copy(deviceToken = token) ?: PairingSession(
            deviceToken = token,
            device = PairedDeviceInfo("dev_local", "Android Device"),
            merchant = PairedMerchantInfo("mrc_local", "Merchant"),
        )
    }

    override fun clear() {
        sessionFlow.value = null
    }
}

/**
 * Offline stand-in used for Compose previews.
 */
class StubAulaaApi : AulaaApi {
    override suspend fun claimPairing(code: String, deviceName: String): PairingSession {
        delay(300)
        return PairingSession(
            deviceToken = "aulakb_" + "0123456789abcdef".repeat(4),
            device = PairedDeviceInfo(id = "dev_preview", name = deviceName.ifBlank { "Pixel 8 Pro" }),
            merchant = PairedMerchantInfo(id = "mrc_preview", name = "Toko Lapis Rina"),
            projects = listOf(
                Project("prj_1", "Toko Lapis Rina", status = "approved", isProd = true),
                Project("prj_2", "Katering Rina", status = "approved", isProd = true),
            ),
            payBaseUrl = DEFAULT_PAY_BASE_URL,
        )
    }

    override suspend fun listProjects(): List<Project> {
        delay(150)
        return listOf(
            Project("prj_1", "Toko Lapis Rina", status = "approved", isProd = true),
            Project("prj_2", "Katering Rina", status = "approved", isProd = true),
        )
    }

    override suspend fun createTransactionLink(projectId: String, amount: Long, orderId: String?): PaymentLink {
        delay(450)
        return PaymentLink(
            id = "8f3ac1",
            amount = amount,
            status = "pending",
            orderId = orderId,
        )
    }

    override suspend fun listProducts(projectId: String?, query: String?): List<Product> {
        delay(150)
        val all = listOf(
            Product("prd_1", "prj_1", "Kue Lapis Legit (1 box)", 150_000, "Rp150.000", stock = 12),
            Product("prd_2", "prj_1", "Nastar Premium (500g)", 95_000, "Rp95.000", stock = 25),
            Product("prd_3", "prj_1", "Paket Hampers Lebaran", 320_000, "Rp320.000", stock = 5),
            Product("prd_4", "prj_2", "Brownies Panggang", 65_000, "Rp65.000", stock = 18),
        )
        val byProject = if (projectId.isNullOrBlank()) all else all.filter { it.projectId == projectId }
        return if (query.isNullOrBlank()) byProject
        else byProject.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun transactionStatus(id: String): PaymentLink {
        delay(150)
        return PaymentLink(id = id, amount = 150_000, status = "pending")
    }
}

/** Formats an IDR amount as "Rp1.234.567" (dot thousands, matching the dashboard). */
fun formatIdr(amount: Long): String {
    val digits = amount.coerceAtLeast(0).toString()
    val sb = StringBuilder()
    val firstGroup = digits.length % 3
    for (i in digits.indices) {
        if (i != 0 && (i - firstGroup) % 3 == 0) sb.append('.')
        sb.append(digits[i])
    }
    return "Rp$sb"
}
