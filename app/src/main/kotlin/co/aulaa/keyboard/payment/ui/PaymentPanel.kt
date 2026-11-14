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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.aulaa.keyboard.payment.data.AulaaApi
import co.aulaa.keyboard.payment.data.PaymentLink
import co.aulaa.keyboard.payment.data.Product
import co.aulaa.keyboard.payment.data.Project
import co.aulaa.keyboard.payment.data.formatIdr
import co.aulaa.keyboard.payment.theme.AulaaTheme
import kotlinx.coroutines.launch

/**
 * The Aulaa payment panel shown in the keyboard's panel region (where the media /
 * emoji panel normally appears). It is fully self-contained: it talks to the
 * merchant backend only through [api], and hands the finished link back through
 * [onCommitText] — the single seam the host IME implements with the editor's
 * `commitText` (see INTEGRATION.md). [onClose] returns to the normal keyboard.
 *
 * Nothing here imports FlorisBoard internals, so it renders in isolation and in
 * Compose previews.
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
) {
    val p = AulaaTheme.palette
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Tab.Transaksi) }
    var screen by remember { mutableStateOf(Screen.Form) }

    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<Project?>(null) }
    var amount by remember { mutableStateOf(150_000L) }
    var orderId by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var createdLink by remember { mutableStateOf<PaymentLink?>(null) }

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    // Load projects + products once.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        projects = runCatching { api.listProjects() }.getOrDefault(emptyList())
        selectedProject = projects.firstOrNull()
        products = runCatching { api.listProducts(null) }.getOrDefault(emptyList())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(p.card, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // ---- header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 26.dp)
            Spacer(Modifier.width(9.dp))
            Text("Aulaa", color = p.foreground, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            IconSquare("✕", onClick = onClose)
        }

        SegTabs(
            tab = tab,
            onSelect = { tab = it; if (it == Tab.Transaksi && createdLink == null) screen = Screen.Form },
        )

        when {
            tab == Tab.Transaksi && screen == Screen.Form -> TransaksiForm(
                projects = projects,
                selectedProject = selectedProject,
                onSelectProject = { selectedProject = it },
                amount = amount,
                onAmount = { amount = it },
                orderId = orderId,
                onOrderId = { orderId = it },
                creating = creating,
                onCreate = {
                    val prj = selectedProject ?: return@TransaksiForm
                    creating = true
                    scope.launch {
                        val link = runCatching {
                            api.createTransactionLink(prj.id, amount, orderId.ifBlank { null })
                        }.getOrNull()
                        creating = false
                        if (link != null) { createdLink = link; screen = Screen.Ready }
                    }
                },
            )

            tab == Tab.Transaksi && screen == Screen.Ready -> LinkReady(
                link = createdLink!!,
                onCommit = { onCommitText(createdLink!!.payUrl); onClose() },
                onAgain = { createdLink = null; screen = Screen.Form },
            )

            else -> ProdukTab(
                api = api,
                products = products,
                onProducts = { products = it },
                search = search,
                onSearch = { search = it },
                onSend = { onCommitText(it.buyUrl); onClose() },
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
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        // project chip (cycles through projects on tap — a dropdown in the real build)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(BorderStroke(1.dp, p.border), RoundedCornerShape(999.dp))
                .clickable(enabled = projects.size > 1) {
                    val i = projects.indexOf(selectedProject)
                    if (i >= 0) onSelectProject(projects[(i + 1) % projects.size])
                }
                .padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            Box(Modifier.size(7.dp).background(p.brandMid, RoundedCornerShape(999.dp)))
            Spacer(Modifier.width(7.dp))
            Text(selectedProject?.name ?: "Pilih project", color = p.foreground, fontSize = 12.5.sp)
            Text("  · project ▾", color = p.mutedForeground, fontSize = 12.5.sp)
        }

        // amount
        Column {
            Label("NOMINAL")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Rp ", color = p.mutedForeground, fontSize = 17.sp)
                Text(
                    formatIdr(amount).removePrefix("Rp"),
                    color = p.foreground, fontSize = 30.sp, fontWeight = FontWeight.Normal,
                )
            }
        }

        // quick chips
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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

        // order id (optional) — mirrors the dashboard field
        Column {
            Row {
                Label("ORDER ID")
                Text("  (opsional, referensi kamu)", color = p.mutedForeground, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            TextField(
                value = orderId,
                onValueChange = onOrderId,
                placeholder = "mis. pesanan-bunga · otomatis jika kosong",
            )
        }

        PrimaryButton(
            text = if (creating) "Membuat…" else "Buat Link Pembayaran",
            enabled = !creating && selectedProject != null && amount > 0,
            onClick = onCreate,
        )
    }
}

/* ----------------------------------------------------------------------- */
/* Link siap                                                               */
/* ----------------------------------------------------------------------- */

@Composable
private fun LinkReady(link: PaymentLink, onCommit: () -> Unit, onAgain: () -> Unit) {
    val p = AulaaTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.size(42.dp).background(p.brandTint, RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = p.brandDeep, fontSize = 20.sp) }
            Text("Link pembayaran siap", color = p.foreground, fontSize = 16.sp)
            Text(
                "Bagikan ke pelanggan — mereka pilih sendiri metode bayarnya (QRIS, VA, e-wallet).",
                color = p.mutedForeground, fontSize = 12.sp,
            )
        }

        // link card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Text(
                link.payUrl.removePrefix("https://"),
                color = p.foreground, fontSize = 12.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
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
    api: AulaaApi,
    products: List<Product>,
    onProducts: (List<Product>) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    onSend: (Product) -> Unit,
) {
    val p = AulaaTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextField(
            value = search,
            onValueChange = {
                onSearch(it)
                // Filtering handled client-side here; the OkHttp impl can query the server.
            },
            placeholder = "Cari produk…",
        )
        val shown = if (search.isBlank()) products
        else products.filter { it.name.contains(search, ignoreCase = true) }

        LazyColumn(
            modifier = Modifier.heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(shown, key = { it.id }) { prod ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp, horizontal = 7.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).background(p.brandMid, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(prod.initial, color = p.onBrand, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(prod.name, color = p.foreground, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(prod.priceLabel, color = p.mutedForeground, fontSize = 11.5.sp)
                    }
                    Text(
                        "Kirim",
                        color = p.brandDeep, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .border(BorderStroke(1.dp, p.brandMid), RoundedCornerShape(8.dp))
                            .background(p.brandTint, RoundedCornerShape(8.dp))
                            .clickable { onSend(prod) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Text(
            "Buat produk baru tetap di dashboard web",
            color = p.mutedForeground, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ----------------------------------------------------------------------- */
/* Shared bits                                                             */
/* ----------------------------------------------------------------------- */

@Composable
private fun Label(text: String) {
    Text(text, color = AulaaTheme.palette.mutedForeground, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
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
                fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(if (on) p.card else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
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
            .height(40.dp)
            .background(p.background, RoundedCornerShape(AulaaTheme.radius))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = p.mutedForeground, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = p.foreground, fontSize = 13.5.sp),
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
            .height(46.dp)
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
            fontSize = 14.sp, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    val p = AulaaTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(p.background, RoundedCornerShape(AulaaTheme.radius))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}
