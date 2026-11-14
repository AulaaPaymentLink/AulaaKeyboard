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

/** Base URLs for the shareable links the keyboard pastes into the chat. */
const val AULAA_PAY_BASE = "https://payment.aulaa.co/pay/"   // one-off transaction link
const val AULAA_BUY_BASE = "https://payment.aulaa.co/buy/"   // product link

/** A merchant project; needed to create a transaction link (`project_id`). */
data class Project(val id: String, val name: String)

/** A catalog product. The keyboard only lists these — creating products stays on the web. */
data class Product(
    val id: String,
    val name: String,
    val amount: Long,
    val priceLabel: String,
) {
    val buyUrl: String get() = AULAA_BUY_BASE + id
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "A"
}

/** Result of creating a one-off transaction link (Aulaa "Invoice"). */
data class PaymentLink(
    val id: String,
    val amount: Long,
    val status: String,
) {
    val payUrl: String get() = AULAA_PAY_BASE + id
}

/**
 * The exact slice of the Aulaa merchant API the keyboard is allowed to touch.
 *
 * In production every call is authorized by the scoped device token obtained via
 * pairing (see [PairingStore]); the token may ONLY do these four things — create a
 * transaction link, list projects, list products, read a transaction's status —
 * and nothing else (no payout, no settings). Implemented over OkHttp against the
 * existing endpoints:
 *   - createTransactionLink -> POST /v1/transactions {project_id, amount, order_id?}
 *   - listProjects          -> GET  /v1/projects
 *   - listProducts          -> GET  /v1/products
 *   - transactionStatus     -> GET  /v1/transactions/{id}
 */
interface AulaaApi {
    suspend fun listProjects(): List<Project>
    suspend fun createTransactionLink(projectId: String, amount: Long, orderId: String?): PaymentLink
    suspend fun listProducts(query: String?): List<Product>
    suspend fun transactionStatus(id: String): String
}

/**
 * Storage for the scoped device token minted by pairing.
 *
 * Implement with EncryptedSharedPreferences. The companion-app Activity writes the
 * token after a successful pairing/claim; the IME service reads it. Both live in
 * the same app package (`co.aulaa.keyboard`) and process sandbox, so they share
 * this store directly — no cross-app channel, no secrets baked into the APK.
 */
interface PairingStore {
    fun deviceToken(): String?
    fun isPaired(): Boolean
    fun save(token: String)
    fun clear()
}

/**
 * Offline stand-in used for Compose previews and until the pairing backend lands.
 * Returns believable sample data so the panel can be driven end-to-end without a
 * network or a real token. Swap for the OkHttp-backed implementation in wiring.
 */
class StubAulaaApi : AulaaApi {
    override suspend fun listProjects(): List<Project> {
        delay(150)
        return listOf(Project("prj_1", "Toko Lapis Rina"), Project("prj_2", "Katering Rina"))
    }

    override suspend fun createTransactionLink(projectId: String, amount: Long, orderId: String?): PaymentLink {
        delay(600)
        return PaymentLink(id = "8f3ac1", amount = amount, status = "pending")
    }

    override suspend fun listProducts(query: String?): List<Product> {
        delay(150)
        val all = listOf(
            Product("prd_1", "Kue Lapis Legit (1 box)", 150_000, "Rp150.000"),
            Product("prd_2", "Nastar Premium (500g)", 95_000, "Rp95.000"),
            Product("prd_3", "Paket Hampers Lebaran", 320_000, "Rp320.000"),
            Product("prd_4", "Brownies Panggang", 65_000, "Rp65.000"),
        )
        return if (query.isNullOrBlank()) all
        else all.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun transactionStatus(id: String): String {
        delay(150)
        return "pending"
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
