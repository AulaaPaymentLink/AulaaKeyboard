/*
 * Copyright (C) 2026 Aulaa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package co.aulaa.keyboard.payment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.aulaa.keyboard.payment.data.AulaaApi
import co.aulaa.keyboard.payment.data.AulaaApiException
import co.aulaa.keyboard.payment.data.PairingStore
import co.aulaa.keyboard.payment.data.PaymentLink
import co.aulaa.keyboard.payment.data.Product
import co.aulaa.keyboard.payment.data.Project
import co.aulaa.keyboard.payment.data.formatIdr
import co.aulaa.keyboard.payment.theme.AulaaTheme
import kotlinx.coroutines.launch

/**
 * The Aulaa payment panel shown in the keyboard's panel region (where the media /
 * emoji panel normally appears). It is self-contained: it talks to the merchant
 * backend only through [api], reads pairing state from [pairingStore], and hands
 * the finished link back through [onCommitText]. [onClose] returns to the normal
 * keyboard, and [onOpenPairing] opens the companion pairing screen if unpaired.
 */
private enum class Tab { Transaksi, Produk }
private enum class Screen { Form, Ready }

private val quickNominal = listOf(50_000L, 100_000L, 150_000L, 200_000L)

@Composable
fun PaymentPanel(
    api: AulaaApi,
    onCommitText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pairingStore: PairingStore? = null,
    onOpenPairing: (() -> Unit)? = null,
) {
    val p = AulaaTheme.palette
    val scope = rememberCoroutineScope()

    val sessionState = pairingStore?.sessionFlow?.collectAsState()
    val session = sessionState?.value
    val isPaired = pairingStore == null || !session?.deviceToken.isNullOrBlank()

    var tab by remember { mutableStateOf(Tab.Transaksi) }
    var screen by remember { mutableStateOf(Screen.Form) }

    var projects by remember(session) { mutableStateOf(session?.projects.orEmpty()) }
    var selectedProject by remember(session) { mutableStateOf(session?.projects?.firstOrNull()) }
    var amount by remember { mutableLongStateOf(150_000L) }
    var orderId by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var createdLink by remember { mutableStateOf<PaymentLink?>(null) }
    var checkingStatus by remember { mutableStateOf(false) }

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loadingData by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tokenRevoked by remember { mutableStateOf(false) }

    fun loadMerchantData(projectIdFilter: String? = selectedProject?.id) {
        if (!isPaired) return
        scope.launch {
            loadingData = true
            errorMessage = null
            runCatching { api.listProjects() }
                .onSuccess { fetched ->
                    tokenRevoked = false
                    if (fetched.isNotEmpty()) {
                        projects = fetched
                        if (selectedProject == null || fetched.none { it.id == selectedProject?.id }) {
                            selectedProject = fetched.firstOrNull()
                        }
                    }
                }
                .onFailure { err ->
                    if (err is AulaaApiException && err.isUnauthorized) {
                        tokenRevoked = true
                    } else if (projects.isEmpty()) {
                        errorMessage = err.message
                    }
                }

            val activeProjectId = projectIdFilter ?: selectedProject?.id
            runCatching { api.listProducts(projectId = activeProjectId, query = null) }
                .onSuccess { fetched ->
                    products = if (fetched.isEmpty() && activeProjectId != null) {
                        runCatching { api.listProducts(projectId = null, query = null) }.getOrDefault(emptyList())
                    } else {
                        fetched
                    }
                }
                .onFailure { err ->
                    if (err is AulaaApiException && err.isUnauthorized) {
                        tokenRevoked = true
                    }
                }
            loadingData = false
        }
    }

    LaunchedEffect(isPaired, session?.deviceToken) {
        if (isPaired) {
            tokenRevoked = false
            loadMerchantData()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(p.card, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // ---- header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 26.dp)
            Spacer(Modifier.width(9.dp))
            Text("Aulaa", color = p.foreground, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            if (session != null && isPaired && !tokenRevoked) {
                Text(
                    "  ·  ${session.merchant.name}",
                    color = p.mutedForeground,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (isPaired && !tokenRevoked) {
                IconSquare(if (loadingData) "…" else "↻", onClick = { loadMerchantData() })
                Spacer(Modifier.width(6.dp))
            }
            IconSquare("✕", onClick = onClose)
        }

        if (!isPaired || tokenRevoked) {
            UnpairedPrompt(
                isRevoked = tokenRevoked,
                onOpenPairing = onOpenPairing,
            )
            return@Column
        }

        SegTabs(
            tab = tab,
            onSelect = {
                tab = it
                errorMessage = null
                if (it == Tab.Transaksi && createdLink == null) screen = Screen.Form
            },
        )

        if (errorMessage != null) {
            ErrorBanner(
                message = errorMessage!!,
                onDismiss = { errorMessage = null },
            )
        }

        when {
            tab == Tab.Transaksi && screen == Screen.Form -> TransaksiForm(
                projects = projects,
                selectedProject = selectedProject,
                onSelectProject = { prj ->
                    selectedProject = prj
                    loadMerchantData(projectIdFilter = prj.id)
                },
                amount = amount,
                onAmount = { amount = it },
                orderId = orderId,
                onOrderId = { orderId = it },
                creating = creating,
                onCreate = {
                    val prj = selectedProject
                    if (prj == null) {
                        errorMessage = "Belum ada project aktif. Pastikan akun merchant memiliki project yang disetujui."
                        return@TransaksiForm
                    }
                    creating = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            api.createTransactionLink(prj.id, amount, orderId.ifBlank { null })
                        }.onSuccess { link ->
                            creating = false
                            createdLink = link
                            screen = Screen.Ready
                        }.onFailure { err ->
                            creating = false
                            if (err is AulaaApiException && err.isUnauthorized) {
                                tokenRevoked = true
                            } else {
                                errorMessage = err.message ?: "Gagal membuat link pembayaran."
                            }
                        }
                    }
                },
            )

            tab == Tab.Transaksi && screen == Screen.Ready -> LinkReady(
                link = createdLink!!,
                checkingStatus = checkingStatus,
                onCheckStatus = {
                    val current = createdLink ?: return@LinkReady
                    checkingStatus = true
                    scope.launch {
                        runCatching { api.transactionStatus(current.id) }
                            .onSuccess { updated ->
                                createdLink = updated.copy(
                                    amount = if (updated.amount > 0) updated.amount else current.amount,
                                )
                            }
                            .onFailure { err ->
                                errorMessage = err.message
                            }
                        checkingStatus = false
                    }
                },
                onCommit = {
                    onCommitText(createdLink!!.payUrl)
                    onClose()
                },
                onAgain = {
                    createdLink = null
                    screen = Screen.Form
                },
            )

            else -> ProdukTab(
                products = products,
                loading = loadingData,
                search = search,
                onSearch = { search = it },
                onSend = {
                    onCommitText(it.buyUrl)
                    onClose()
                },
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Unpaired / Revoked prompt                                               */
/* ----------------------------------------------------------------------- */

@Composable
private fun UnpairedPrompt(
    isRevoked: Boolean,
    onOpenPairing: (() -> Unit)?,
) {
    val p = AulaaTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (isRevoked) "Perangkat tidak lagi terhubung" else "Hubungkan dengan akun Merchant Aulaa",
            color = p.foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = if (isRevoked) {
                "Token perangkat ini telah dicabut atau kedaluwarsa. Hubungkan ulang melalui kode pairing dari Dashboard Web Aulaa."
            } else {
                "Buka Dashboard Web Aulaa → Account Settings → Keyboard devices, lalu klaim kode pairing 8 karakter untuk mulai membuat link pembayaran dari keyboard."
            },
            color = p.mutedForeground,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        if (onOpenPairing != null) {
            PrimaryButton(
                text = "Hubungkan Perangkat (Pairing)",
                enabled = true,
                onClick = onOpenPairing,
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Transaksi                                                               */
/* ----------------------------------------------------------------------- */

@Composable
private fun TransaksiForm(
    projects: List<Project>,
    selectedProject: Project?,
    onSelectProject: (Project) -> Unit,
    amount: Long,
    onAmount: (Long) -> Unit,
    orderId: String,
    onOrderId: (String) -> Unit,
    creating: Boolean,
    onCreate: () -> Unit,
) {
    val p = AulaaTheme.palette
    var showNumpad by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // project chip (cycles through projects on tap)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(BorderStroke(1.dp, p.border), RoundedCornerShape(999.dp))
                .clickable(enabled = projects.size > 1) {
                    val i = projects.indexOf(selectedProject)
                    if (i >= 0) onSelectProject(projects[(i + 1) % projects.size])
                }
                .padding(horizontal = 11.dp, vertical = 5.dp),
        ) {
            Box(Modifier.size(7.dp).background(p.brandMid, RoundedCornerShape(999.dp)))
            Spacer(Modifier.width(7.dp))
            Text(
                selectedProject?.name ?: "Belum ada project",
                color = p.foreground,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (projects.size > 1) {
                Text("  · ganti project ▾", color = p.mutedForeground, fontSize = 12.sp)
            } else {
                Text("  · project", color = p.mutedForeground, fontSize = 12.sp)
            }
        }

        // amount (tap to toggle inline numpad so custom nominals work inside the IME)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, if (showNumpad) p.brandMid else p.border),
                    RoundedCornerShape(AulaaTheme.radius),
                )
                .background(p.background, RoundedCornerShape(AulaaTheme.radius))
                .clickable { showNumpad = !showNumpad }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("NOMINAL")
                Spacer(Modifier.weight(1f))
                Text(
                    if (showNumpad) "Selesai ▴" else "Ketik nominal ▾",
                    color = p.brandDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Rp ", color = p.mutedForeground, fontSize = 16.sp)
                Text(
                    formatIdr(amount).removePrefix("Rp"),
                    color = p.foreground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        if (showNumpad) {
            InlineAmountNumpad(
                amount = amount,
                onAmount = onAmount,
            )
        }

        // quick chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            quickNominal.forEach { v ->
                val on = v == amount
                Text(
                    formatIdr(v),
                    color = if (on) p.brandDeep else p.foreground,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .border(
                            BorderStroke(1.dp, if (on) p.brandMid else p.border),
                            RoundedCornerShape(999.dp),
                        )
                        .background(
                            if (on) p.brandTint else p.background,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { onAmount(v) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (!showNumpad) {
            // order id (optional) — mirrors the dashboard field
            Column {
                Row {
                    Label("ORDER ID")
                    Text("  (opsional, otomatis jika kosong)", color = p.mutedForeground, fontSize = 11.sp)
                }
                Spacer(Modifier.height(5.dp))
                TextField(
                    value = orderId,
                    onValueChange = onOrderId,
                    placeholder = "mis. INV-2026-001",
                )
            }
        }

        PrimaryButton(
            text = if (creating) "Membuat…" else "Buat Link Pembayaran",
            enabled = !creating && selectedProject != null && amount > 0,
            onClick = onCreate,
        )
    }
}

@Composable
private fun InlineAmountNumpad(
    amount: Long,
    onAmount: (Long) -> Unit,
) {
    val p = AulaaTheme.palette
    val maxAmount = 999_999_999L

    fun appendDigits(digits: String) {
        val current = if (amount == 0L) "" else amount.toString()
        val next = (current + digits).take(9).toLongOrNull() ?: 0L
        onAmount(next.coerceAtMost(maxAmount))
    }

    val rows = listOf(
        listOf("1", "2", "3", "C"),
        listOf("4", "5", "6", "000"),
        listOf("7", "8", "9", "⌫"),
        listOf("0"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { key ->
                    val isAction = key == "C" || key == "⌫" || key == "000"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(if (isAction) p.muted else p.key, RoundedCornerShape(7.dp))
                            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(7.dp))
                            .clickable {
                                when (key) {
                                    "C" -> onAmount(0L)
                                    "⌫" -> onAmount(amount / 10L)
                                    "000" -> if (amount > 0L) appendDigits("000")
                                    else -> appendDigits(key)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            color = if (isAction) p.brandDeep else p.keyForeground,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Link siap                                                               */
/* ----------------------------------------------------------------------- */

@Composable
private fun LinkReady(
    link: PaymentLink,
    checkingStatus: Boolean,
    onCheckStatus: () -> Unit,
    onCommit: () -> Unit,
    onAgain: () -> Unit,
) {
    val p = AulaaTheme.palette
    val statusLabel = when (link.status.lowercase()) {
        "paid", "settled", "completed" -> "Lunas ✓"
        "expired", "cancelled", "canceled" -> "Kedaluwarsa"
        else -> "Menunggu pembayaran"
    }

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier.size(38.dp).background(p.brandTint, RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = p.brandDeep, fontSize = 18.sp) }
            Text("Link pembayaran siap", color = p.foreground, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Status: $statusLabel",
                    color = p.mutedForeground,
                    fontSize = 12.sp,
                )
                Text(
                    text = if (checkingStatus) "Mengecek…" else "Cek status ↻",
                    color = p.brandDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(enabled = !checkingStatus) { onCheckStatus() },
                )
            }
        }

        // link card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                link.payUrl.removePrefix("https://"),
                color = p.foreground,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(formatIdr(link.amount), color = p.mutedForeground, fontSize = 12.sp)
        }

        PrimaryButton(text = "Tempel ke chat", enabled = true, onClick = onCommit)
        OutlineButton(text = "Buat link lagi", onClick = onAgain)
    }
}

/* ----------------------------------------------------------------------- */
/* Produk                                                                  */
/* ----------------------------------------------------------------------- */

@Composable
private fun ProdukTab(
    products: List<Product>,
    loading: Boolean,
    search: String,
    onSearch: (String) -> Unit,
    onSend: (Product) -> Unit,
) {
    val p = AulaaTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        TextField(
            value = search,
            onValueChange = onSearch,
            placeholder = "Cari produk…",
        )
        val shown = if (search.isBlank()) products
        else products.filter { it.name.contains(search, ignoreCase = true) }

        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (loading) "Memuat katalog produk…" else "Belum ada produk yang cocok.",
                    color = p.mutedForeground,
                    fontSize = 12.5.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 210.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shown, key = { it.id }) { prod ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).background(p.brandMid, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(prod.initial, color = p.onBrand, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                prod.name,
                                color = p.foreground,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val stockSubtitle = if (prod.stock != null) " · Stok ${prod.stock}" else ""
                            Text(
                                "${prod.priceLabel}$stockSubtitle",
                                color = p.mutedForeground,
                                fontSize = 11.5.sp,
                            )
                        }
                        Text(
                            "Kirim",
                            color = p.brandDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .border(BorderStroke(1.dp, p.brandMid), RoundedCornerShape(8.dp))
                                .background(p.brandTint, RoundedCornerShape(8.dp))
                                .clickable { onSend(prod) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Text(
            "Buat produk baru tetap di dashboard web",
            color = p.mutedForeground,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ----------------------------------------------------------------------- */
/* Shared bits                                                             */
/* ----------------------------------------------------------------------- */

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val p = AulaaTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(p.muted, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(8.dp))
            .clickable { onDismiss() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = message,
            color = p.foreground,
            fontSize = 11.5.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text("✕", color = p.mutedForeground, fontSize = 11.5.sp)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = AulaaTheme.palette.mutedForeground, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun BrandMark(size: Dp) {
    val p = AulaaTheme.palette
    Box(
        Modifier
            .size(size)
            .background(Brush.linearGradient(listOf(p.brandLight, p.brandDeep)), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) { Text("a", color = p.onBrand, fontSize = (size.value * 0.6).sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun IconSquare(glyph: String, onClick: () -> Unit) {
    val p = AulaaTheme.palette
    Box(
        Modifier
            .size(28.dp)
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(7.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = p.mutedForeground, fontSize = 13.sp) }
}

@Composable
private fun SegTabs(tab: Tab, onSelect: (Tab) -> Unit) {
    val p = AulaaTheme.palette
    Row(
        modifier = Modifier
            .background(p.muted, RoundedCornerShape(9.dp))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Text(
                t.name,
                color = if (on) p.foreground else p.mutedForeground,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(if (on) p.card else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onSelect(t) }
                    .padding(horizontal = 15.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val p = AulaaTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(p.background, RoundedCornerShape(AulaaTheme.radius))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                color = p.mutedForeground,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = p.foreground, fontSize = 13.sp),
            cursorBrush = SolidColor(p.brandDeep),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val p = AulaaTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                if (enabled) Brush.linearGradient(listOf(p.brandMid, p.brandDeep))
                else SolidColor(p.muted),
                RoundedCornerShape(AulaaTheme.radius),
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) p.onBrand else p.mutedForeground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    val p = AulaaTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(p.background, RoundedCornerShape(AulaaTheme.radius))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.foreground, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) }
}
