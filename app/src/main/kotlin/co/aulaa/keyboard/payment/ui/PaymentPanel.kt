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

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import co.aulaa.keyboard.payment.data.AulaaApi
import co.aulaa.keyboard.payment.data.AulaaApiException
import co.aulaa.keyboard.payment.data.PairingStore
import co.aulaa.keyboard.payment.data.PaymentLink
import co.aulaa.keyboard.payment.data.Product
import co.aulaa.keyboard.payment.data.Project
import co.aulaa.keyboard.payment.data.formatIdr
import co.aulaa.keyboard.payment.theme.AulaaBrandMark
import co.aulaa.keyboard.payment.theme.AulaaTheme
import dev.patrickgold.florisboard.ime.keyboard3.ImeActions
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController
import dev.patrickgold.florisboard.ime.keyboard3.touch.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard3.ui.ImeKeyboardBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.k3lp.lib.text.K3Descriptor
import org.k3lp.lib.text.K3String
import java.net.HttpURLConnection
import java.net.URL

private enum class Tab(val label: String) {
    Transaksi("Transaksi"),
    Produk("Produk"),
}

private enum class Screen { Form, Ready }

private enum class ActiveTextField {
    OrderId,
    ProductSearch,
}

private val quickNominals = listOf(50_000L, 100_000L, 150_000L, 200_000L, 500_000L)

private val LiveDotColor = Color(0xFF2196F3)
private val SandboxDotColor = Color(0xFFEF4444)

private object ProductThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(60)

    fun get(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                }
                try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    } else null
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()?.also { bmp ->
                cache.put(url, bmp)
            }
        }
    }
}

/**
 * Formats project display name according to environment:
 * - Live (`isProd == true`): `Project Name` (paired with blue dot)
 * - Sandbox (`isProd == false`): `Project Name (sandbox)` (paired with red dot)
 */
internal fun Project.displayLabel(): String {
    return if (isProd) name else "$name (sandbox)"
}

@Composable
fun ProjectEnvironmentDot(isProd: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (isProd) LiveDotColor else SandboxDotColor),
    )
}

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
    val imeController = LocalImeController.current

    val sessionState = pairingStore?.sessionFlow?.collectAsState()
    val session = sessionState?.value
    val isPaired = pairingStore == null || !session?.deviceToken.isNullOrBlank()

    var tab by remember { mutableStateOf(Tab.Transaksi) }
    var screen by remember { mutableStateOf(Screen.Form) }
    var activeTextField by remember { mutableStateOf<ActiveTextField?>(null) }
    var projectDropdownOpen by remember { mutableStateOf(false) }

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

    val currentOrderId by rememberUpdatedState(orderId)
    val currentSearch by rememberUpdatedState(search)

    // Route keyboard key presses directly into Order ID or Product Search when active
    DisposableEffect(imeController, activeTextField) {
        val field = activeTextField
        if (field != null) {
            imeController.setEmitInterceptor { emitted ->
                when (emitted) {
                    is K3String -> {
                        val raw = emitted.toText()
                        if (raw != "\n" && raw != "\r") {
                            when (field) {
                                ActiveTextField.OrderId -> {
                                    val filtered = raw.filter {
                                        it.isLetterOrDigit() || it == '-' || it == '_' || it == '.'
                                    }
                                    orderId = (currentOrderId + filtered).take(64)
                                }
                                ActiveTextField.ProductSearch -> {
                                    search = (currentSearch + raw).take(80)
                                }
                            }
                            val shift = imeController.snapshotState().flags.inputShiftState
                            if (shift == InputShiftState.SHIFTED_MANUAL || shift == InputShiftState.SHIFTED_AUTOMATIC) {
                                scope.launch {
                                    imeController.updateState {
                                        state = state.copy(
                                            touchLayerId = InputShiftState.UNSHIFTED.correspondingLayerId,
                                            flags = state.flags.withInputShiftState(InputShiftState.UNSHIFTED),
                                        )
                                    }
                                }
                            }
                        } else {
                            activeTextField = null
                        }
                    }
                    is K3Descriptor -> {
                        when (emitted) {
                            ImeActions.Backspace -> {
                                when (field) {
                                    ActiveTextField.OrderId -> {
                                        if (currentOrderId.isNotEmpty()) {
                                            orderId = currentOrderId.dropLast(1)
                                        }
                                    }
                                    ActiveTextField.ProductSearch -> {
                                        if (currentSearch.isNotEmpty()) {
                                            search = currentSearch.dropLast(1)
                                        }
                                    }
                                }
                            }
                            ImeActions.BackspaceWord -> {
                                when (field) {
                                    ActiveTextField.OrderId -> {
                                        orderId = currentOrderId.trimEnd().substringBeforeLast(' ', "")
                                    }
                                    ActiveTextField.ProductSearch -> {
                                        search = currentSearch.trimEnd().substringBeforeLast(' ', "")
                                    }
                                }
                            }
                            ImeActions.Enter,
                            ImeActions.ShowAulaaPanel,
                            ImeActions.ShowTextPanel -> {
                                activeTextField = null
                            }
                        }
                    }
                }
            }
        }
        onDispose {
            imeController.setEmitInterceptor(null)
        }
    }

    fun loadProducts(projectIdFilter: String? = selectedProject?.id, searchQuery: String = search) {
        if (!isPaired) return
        scope.launch {
            loadingData = true
            val activeProjectId = projectIdFilter ?: selectedProject?.id
            val cleanQuery = searchQuery.trim().ifBlank { null }
            runCatching { api.listProducts(projectId = activeProjectId, query = cleanQuery) }
                .onSuccess { fetched ->
                    products = if (fetched.isEmpty() && activeProjectId != null && cleanQuery == null) {
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
            val cleanQuery = search.trim().ifBlank { null }
            runCatching { api.listProducts(projectId = activeProjectId, query = cleanQuery) }
                .onSuccess { fetched ->
                    products = if (fetched.isEmpty() && activeProjectId != null && cleanQuery == null) {
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

    LaunchedEffect(isPaired, selectedProject?.id, search) {
        if (!isPaired || tokenRevoked) return@LaunchedEffect
        if (search.isNotBlank()) {
            delay(250)
        }
        loadProducts(projectIdFilter = selectedProject?.id, searchQuery = search)
    }

    fun triggerCreateLink() {
        val prj = selectedProject
        if (prj == null) {
            errorMessage = "Belum ada project aktif. Pastikan akun merchant memiliki project yang disetujui."
            return
        }
        if (amount <= 0L) {
            errorMessage = "Masukkan nominal pembayaran terlebih dahulu."
            return
        }
        activeTextField = null
        projectDropdownOpen = false
        creating = true
        errorMessage = null
        scope.launch {
            runCatching {
                api.createTransactionLink(prj.id, amount, orderId.trim().ifBlank { null })
            }.onSuccess { link ->
                creating = false
                createdLink = link
                screen = Screen.Ready
                if (link.expiresAt.isNullOrBlank()) {
                    runCatching { api.transactionStatus(link.id) }
                        .onSuccess { updated ->
                            createdLink = updated.copy(
                                amount = if (updated.amount > 0) updated.amount else link.amount,
                                orderId = updated.orderId ?: link.orderId,
                            )
                        }
                }
            }.onFailure { err ->
                creating = false
                if (err is AulaaApiException && err.isUnauthorized) {
                    tokenRevoked = true
                } else {
                    errorMessage = err.message ?: "Gagal membuat link pembayaran."
                }
            }
        }
    }

    fun triggerCheckStatus() {
        val current = createdLink ?: return
        checkingStatus = true
        scope.launch {
            runCatching { api.transactionStatus(current.id) }
                .onSuccess { updated ->
                    createdLink = updated.copy(
                        amount = if (updated.amount > 0) updated.amount else current.amount,
                        orderId = updated.orderId ?: current.orderId,
                        expiresAt = updated.expiresAt ?: current.expiresAt,
                    )
                }
                .onFailure { err ->
                    errorMessage = err.message
                }
            checkingStatus = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(p.card, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(top = 8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            val isWideScreen = maxWidth >= 540.dp

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ---- Compact Single Header Row: Brand + Tabs + Refresh + Close ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AulaaBrandMark(size = 20.dp, showContainer = false)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Aulaa",
                            color = p.foreground,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))

                        if (isPaired && !tokenRevoked) {
                            SleekTabs(
                                tab = tab,
                                onSelect = {
                                    tab = it
                                    activeTextField = null
                                    projectDropdownOpen = false
                                    errorMessage = null
                                    if (it == Tab.Transaksi && createdLink == null) screen = Screen.Form
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            IconSquare(if (loadingData) "…" else "↻", onClick = { loadMerchantData() })
                            Spacer(Modifier.width(5.dp))
                        }
                        IconSquare("✕", onClick = onClose)
                    }

                    if (!isPaired || tokenRevoked) {
                        UnpairedPrompt(
                            isRevoked = tokenRevoked,
                            onOpenPairing = onOpenPairing,
                        )
                        Spacer(Modifier.height(6.dp))
                        return@BoxWithConstraints
                    }

                    if (errorMessage != null) {
                        ErrorBanner(
                            message = errorMessage!!,
                            onDismiss = { errorMessage = null },
                        )
                    }

                    when (tab) {
                        Tab.Transaksi -> {
                            if (isWideScreen) {
                                TransaksiWideFoldableLayout(
                                    selectedProject = selectedProject,
                                    projectDropdownOpen = projectDropdownOpen,
                                    onToggleProjectDropdown = {
                                        projectDropdownOpen = !projectDropdownOpen
                                    },
                                    amount = amount,
                                    onAmount = { amount = it },
                                    orderId = orderId,
                                    isEditingOrderId = activeTextField == ActiveTextField.OrderId,
                                    onToggleOrderIdEdit = {
                                        projectDropdownOpen = false
                                        activeTextField = if (activeTextField == ActiveTextField.OrderId) null else ActiveTextField.OrderId
                                    },
                                    onClearOrderId = { orderId = "" },
                                    creating = creating,
                                    onCreate = { triggerCreateLink() },
                                    screen = screen,
                                    createdLink = createdLink,
                                    checkingStatus = checkingStatus,
                                    onCheckStatus = { triggerCheckStatus() },
                                    onCommit = {
                                        createdLink?.let {
                                            onCommitText(it.payUrl)
                                            onClose()
                                        }
                                    },
                                    onAgain = {
                                        createdLink = null
                                        screen = Screen.Form
                                    },
                                )
                            } else {
                                if (screen == Screen.Ready && createdLink != null) {
                                    LinkReady(
                                        link = createdLink!!,
                                        checkingStatus = checkingStatus,
                                        onCheckStatus = { triggerCheckStatus() },
                                        onCommit = {
                                            onCommitText(createdLink!!.payUrl)
                                            onClose()
                                        },
                                        onAgain = {
                                            createdLink = null
                                            screen = Screen.Form
                                        },
                                    )
                                } else {
                                    TransaksiCompactLayout(
                                        selectedProject = selectedProject,
                                        projectDropdownOpen = projectDropdownOpen,
                                        onToggleProjectDropdown = {
                                            projectDropdownOpen = !projectDropdownOpen
                                        },
                                        amount = amount,
                                        onAmount = { amount = it },
                                        orderId = orderId,
                                        isEditingOrderId = activeTextField == ActiveTextField.OrderId,
                                        onToggleOrderIdEdit = {
                                            projectDropdownOpen = false
                                            activeTextField = if (activeTextField == ActiveTextField.OrderId) null else ActiveTextField.OrderId
                                        },
                                        onClearOrderId = { orderId = "" },
                                        creating = creating,
                                        onCreate = { triggerCreateLink() },
                                    )
                                }
                            }
                        }

                        Tab.Produk -> {
                            ProdukTab(
                                isWideScreen = isWideScreen,
                                isKeyboardOpen = activeTextField == ActiveTextField.ProductSearch,
                                products = products,
                                loading = loadingData,
                                search = search,
                                onToggleSearch = {
                                    projectDropdownOpen = false
                                    activeTextField = if (activeTextField == ActiveTextField.ProductSearch) null else ActiveTextField.ProductSearch
                                },
                                onClearSearch = { search = "" },
                                onSend = { prod ->
                                    activeTextField = null
                                    onCommitText(prod.buyUrl)
                                    onClose()
                                },
                            )
                        }
                    }
                }

                // ---- In-Panel Floating Project Dropdown Overlay (scrollable, never increases IME height, never uses PopupWindow) ----
                if (projectDropdownOpen && projects.isNotEmpty() && tab == Tab.Transaksi) {
                    // Dismiss scrim inside the panel
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(9f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { projectDropdownOpen = false },
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 72.dp)
                            .fillMaxWidth(if (isWideScreen) 0.52f else 1f)
                            .heightIn(max = 160.dp)
                            .zIndex(10f)
                            .shadow(10.dp, RoundedCornerShape(AulaaTheme.radius))
                            .background(p.card, RoundedCornerShape(AulaaTheme.radius))
                            .border(BorderStroke(1.dp, p.brandMid), RoundedCornerShape(AulaaTheme.radius))
                            .verticalScroll(rememberScrollState()),
                    ) {
                        projects.forEachIndexed { index, prj ->
                            val isSelected = prj.id == selectedProject?.id
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(p.border),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) p.muted else Color.Transparent)
                                    .clickable {
                                        projectDropdownOpen = false
                                        selectedProject = prj
                                        loadMerchantData(projectIdFilter = prj.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                ProjectEnvironmentDot(isProd = prj.isProd)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = prj.displayLabel(),
                                    color = p.foreground,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        color = p.brandLight,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-place keyboard shown directly underneath when typing Order ID or Product Search
        if (activeTextField != null) {
            Spacer(Modifier.height(4.dp))
            ImeKeyboardBox()
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Compact Project Selector Trigger & Order ID Trigger                     */
/* ----------------------------------------------------------------------- */

@Composable
private fun ProjectSelectorTrigger(
    selectedProject: Project?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AulaaTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(p.background, RoundedCornerShape(8.dp))
            .border(
                BorderStroke(1.dp, if (expanded) p.brandMid else p.border),
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
    ) {
        if (selectedProject != null) {
            ProjectEnvironmentDot(isProd = selectedProject.isProd)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = selectedProject?.displayLabel() ?: "Pilih project",
            color = p.foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (expanded) "▴" else "▾",
            color = p.mutedForeground,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun InlineOrderIdField(
    orderId: String,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AulaaTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(p.background, RoundedCornerShape(8.dp))
            .border(
                BorderStroke(1.dp, if (isEditing) p.brandMid else p.border),
                RoundedCornerShape(8.dp),
            )
            .clickable { onToggleEdit() }
            .padding(horizontal = 9.dp),
    ) {
        Text(
            text = when {
                isEditing && orderId.isEmpty() -> "|"
                isEditing -> "$orderId|"
                orderId.isNotEmpty() -> orderId
                else -> "Invoice (opsional)"
            },
            color = if (orderId.isEmpty() && !isEditing) p.mutedForeground else p.foreground,
            fontSize = 12.sp,
            fontWeight = if (orderId.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (orderId.isNotEmpty()) {
            Text(
                text = "✕",
                color = p.mutedForeground,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { onClear() }
                    .padding(horizontal = 4.dp),
            )
        }
        if (isEditing) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = "OK",
                color = p.onBrand,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(p.brandDeep, RoundedCornerShape(5.dp))
                    .clickable { onToggleEdit() }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Nominal Display + Quick Chips + Sleek Compact Numpad                    */
/* ----------------------------------------------------------------------- */

@Composable
private fun NominalBox(
    amount: Long,
    showNumpad: Boolean = false,
    onToggleNumpad: (() -> Unit)? = null,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AulaaTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(p.background, RoundedCornerShape(8.dp))
            .border(
                BorderStroke(1.dp, if (showNumpad) p.brandMid else p.border),
                RoundedCornerShape(8.dp),
            )
            .then(if (onToggleNumpad != null) Modifier.clickable { onToggleNumpad() } else Modifier)
            .padding(horizontal = 10.dp),
    ) {
        Text("Rp ", color = p.mutedForeground, fontSize = 13.5.sp)
        Text(
            text = if (amount > 0L) formatIdr(amount).removePrefix("Rp").trim() else "0",
            color = if (amount > 0L) p.foreground else p.mutedForeground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (showNumpad && amount > 0L) {
            Text(
                text = "Reset",
                color = p.mutedForeground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .border(BorderStroke(1.dp, p.border), RoundedCornerShape(5.dp))
                    .clickable { onReset() }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (onToggleNumpad != null) {
            Text(
                text = if (showNumpad) "Selesai ▴" else "Ketik nominal ▾",
                color = p.brandLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun QuickNominalChips(
    amount: Long,
    onSelectPreset: (Long) -> Unit,
) {
    val p = AulaaTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        quickNominals.forEach { preset ->
            val on = preset == amount
            Text(
                text = formatIdr(preset),
                color = if (on) p.foreground else p.mutedForeground,
                fontSize = 11.5.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .background(if (on) p.muted else p.background, RoundedCornerShape(999.dp))
                    .border(
                        BorderStroke(1.dp, if (on) p.brandMid else p.border),
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onSelectPreset(preset) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SleekAmountNumpad(
    amount: Long,
    isPresetSelected: Boolean,
    onAmountChange: (newAmount: Long, fromPreset: Boolean) -> Unit,
    keyHeightDp: Int = 27,
    modifier: Modifier = Modifier,
) {
    val p = AulaaTheme.palette
    val maxAmount = 999_999_999L

    fun appendDigits(digits: String) {
        val base = if (isPresetSelected || amount == 0L) "" else amount.toString()
        val next = (base + digits).take(9).toLongOrNull() ?: 0L
        onAmountChange(next.coerceAtMost(maxAmount), false)
    }

    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("000", "0", "⌫"),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { key ->
                    val isAction = key == "⌫" || key == "000"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeightDp.dp)
                            .background(
                                if (isAction) p.muted else p.key,
                                RoundedCornerShape(6.dp),
                            )
                            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(6.dp))
                            .clickable {
                                when (key) {
                                    "⌫" -> onAmountChange(amount / 10L, false)
                                    "000" -> if (amount > 0L) appendDigits("000")
                                    else -> appendDigits(key)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            color = if (isAction) p.mutedForeground else p.keyForeground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Version 1: Compact Phone / Cover Screen Layout (~265dp total height)    */
/* ----------------------------------------------------------------------- */

@Composable
private fun TransaksiCompactLayout(
    selectedProject: Project?,
    projectDropdownOpen: Boolean,
    onToggleProjectDropdown: () -> Unit,
    amount: Long,
    onAmount: (Long) -> Unit,
    orderId: String,
    isEditingOrderId: Boolean,
    onToggleOrderIdEdit: () -> Unit,
    onClearOrderId: () -> Unit,
    creating: Boolean,
    onCreate: () -> Unit,
) {
    var isPresetSelected by remember { mutableStateOf(true) }
    var showNumpad by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Project + Invoice on the SAME compact row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProjectSelectorTrigger(
                selectedProject = selectedProject,
                expanded = projectDropdownOpen,
                onClick = onToggleProjectDropdown,
                modifier = Modifier.weight(1f),
            )
            InlineOrderIdField(
                orderId = orderId,
                isEditing = isEditingOrderId,
                onToggleEdit = onToggleOrderIdEdit,
                onClear = onClearOrderId,
                modifier = Modifier.weight(1f),
            )
        }

        if (!isEditingOrderId) {
            NominalBox(
                amount = amount,
                showNumpad = showNumpad,
                onToggleNumpad = { showNumpad = !showNumpad },
                onReset = {
                    isPresetSelected = false
                    onAmount(0L)
                },
            )

            if (showNumpad) {
                SleekAmountNumpad(
                    amount = amount,
                    isPresetSelected = isPresetSelected,
                    onAmountChange = { newAmount, fromPreset ->
                        isPresetSelected = fromPreset
                        onAmount(newAmount)
                    },
                    keyHeightDp = 30,
                )
            }

            QuickNominalChips(
                amount = amount,
                onSelectPreset = { preset ->
                    isPresetSelected = true
                    onAmount(preset)
                },
            )

            PrimaryButton(
                text = if (creating) "Membuat…" else "Buat Link Pembayaran",
                enabled = !creating && selectedProject != null && amount > 0L,
                onClick = onCreate,
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Version 2: Wide / Foldable 2-Pane Layout (Z Fold 7 Inner Screen)        */
/* ----------------------------------------------------------------------- */

@Composable
private fun TransaksiWideFoldableLayout(
    selectedProject: Project?,
    projectDropdownOpen: Boolean,
    onToggleProjectDropdown: () -> Unit,
    amount: Long,
    onAmount: (Long) -> Unit,
    orderId: String,
    isEditingOrderId: Boolean,
    onToggleOrderIdEdit: () -> Unit,
    onClearOrderId: () -> Unit,
    creating: Boolean,
    onCreate: () -> Unit,
    screen: Screen,
    createdLink: PaymentLink?,
    checkingStatus: Boolean,
    onCheckStatus: () -> Unit,
    onCommit: () -> Unit,
    onAgain: () -> Unit,
) {
    var isPresetSelected by remember { mutableStateOf(false) }

    if (isEditingOrderId) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProjectSelectorTrigger(
                selectedProject = selectedProject,
                expanded = projectDropdownOpen,
                onClick = onToggleProjectDropdown,
                modifier = Modifier.weight(1f),
            )
            InlineOrderIdField(
                orderId = orderId,
                isEditing = true,
                onToggleEdit = onToggleOrderIdEdit,
                onClear = onClearOrderId,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left Pane: Project + Order ID + Nominal + Quick Chips + Create
        Column(
            modifier = Modifier.weight(1.08f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProjectSelectorTrigger(
                    selectedProject = selectedProject,
                    expanded = projectDropdownOpen,
                    onClick = onToggleProjectDropdown,
                    modifier = Modifier.weight(1f),
                )
                InlineOrderIdField(
                    orderId = orderId,
                    isEditing = false,
                    onToggleEdit = onToggleOrderIdEdit,
                    onClear = onClearOrderId,
                    modifier = Modifier.weight(1f),
                )
            }

            NominalBox(
                amount = amount,
                showNumpad = true,
                onReset = {
                    isPresetSelected = false
                    onAmount(0L)
                },
            )

            QuickNominalChips(
                amount = amount,
                onSelectPreset = { preset ->
                    isPresetSelected = true
                    onAmount(preset)
                },
            )

            PrimaryButton(
                text = if (creating) "Membuat…" else "Buat Link Pembayaran",
                enabled = !creating && selectedProject != null && amount > 0L,
                onClick = onCreate,
            )
        }

        // Right Pane: Sleek Numpad or Link Ready
        Box(modifier = Modifier.weight(0.92f)) {
            if (screen == Screen.Ready && createdLink != null) {
                LinkReady(
                    link = createdLink,
                    checkingStatus = checkingStatus,
                    onCheckStatus = onCheckStatus,
                    onCommit = onCommit,
                    onAgain = onAgain,
                )
            } else {
                SleekAmountNumpad(
                    amount = amount,
                    isPresetSelected = isPresetSelected,
                    onAmountChange = { newAmount, fromPreset ->
                        isPresetSelected = fromPreset
                        onAmount(newAmount)
                    },
                    keyHeightDp = 36,
                )
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Link Pembayaran Siap                                                    */
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.background, RoundedCornerShape(AulaaTheme.radius))
                .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(p.brandTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = p.brandLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Link pembayaran siap",
                    color = p.foreground,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (checkingStatus) "Mengecek…" else "Cek status ↻",
                    color = p.brandLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(enabled = !checkingStatus) { onCheckStatus() },
                )
            }

            Text(
                text = "Status: $statusLabel",
                color = p.mutedForeground,
                fontSize = 11.sp,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(p.border),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = link.payUrl.removePrefix("https://"),
                    color = p.foreground,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatIdr(link.amount),
                    color = p.foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val metaParts = buildList {
                link.orderId?.takeIf { it.isNotBlank() }?.let { add("Invoice: $it") }
                link.expiresAt?.takeIf { it.isNotBlank() }?.let { add("Berlaku s/d ${formatTimestampShort(it)}") }
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString("  ·  "),
                    color = p.mutedForeground,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlineButton(
                text = "Buat lagi",
                onClick = onAgain,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Tempel ke chat",
                enabled = true,
                onClick = onCommit,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

private fun formatTimestampShort(raw: String): String {
    val clean = raw.trim()
    if (clean.length >= 16 && clean[4] == '-' && clean[7] == '-' && (clean[10] == 'T' || clean[10] == ' ')) {
        return "${clean.substring(0, 10)} ${clean.substring(11, 16)}"
    }
    return clean
}

/* ----------------------------------------------------------------------- */
/* Katalog Produk Tab (With Product Images & In-Place Search)              */
/* ----------------------------------------------------------------------- */

@Composable
private fun ProdukTab(
    isWideScreen: Boolean,
    isKeyboardOpen: Boolean,
    products: List<Product>,
    loading: Boolean,
    search: String,
    onToggleSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onSend: (Product) -> Unit,
) {
    val p = AulaaTheme.palette
    val maxListHeight = if (isKeyboardOpen) 96.dp else 185.dp

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(p.background, RoundedCornerShape(8.dp))
                .border(
                    BorderStroke(1.dp, if (isKeyboardOpen) p.brandMid else p.border),
                    RoundedCornerShape(8.dp),
                )
                .clickable { onToggleSearch() }
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = when {
                    isKeyboardOpen && search.isEmpty() -> "|"
                    isKeyboardOpen -> "$search|"
                    search.isNotEmpty() -> search
                    else -> "Cari produk…"
                },
                color = if (search.isEmpty() && !isKeyboardOpen) p.mutedForeground else p.foreground,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (search.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = p.mutedForeground,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clickable { onClearSearch() }
                        .padding(horizontal = 4.dp),
                )
            }
            if (isKeyboardOpen) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "OK",
                    color = p.onBrand,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(p.brandDeep, RoundedCornerShape(5.dp))
                        .clickable { onToggleSearch() }
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (loading) "Memuat katalog produk…" else "Belum ada produk yang cocok.",
                    color = p.mutedForeground,
                    fontSize = 12.sp,
                )
            }
        } else if (isWideScreen) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(products, key = { it.id }) { prod ->
                    ProductRow(prod = prod, onSend = { onSend(prod) })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(products, key = { it.id }) { prod ->
                    ProductRow(prod = prod, onSend = { onSend(prod) })
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    prod: Product,
    onSend: () -> Unit,
) {
    val p = AulaaTheme.palette
    var thumbnail by remember(prod.imageUrl) {
        mutableStateOf(prod.imageUrl?.let { ProductThumbnailCache.get(it) })
    }
    LaunchedEffect(prod.imageUrl) {
        val url = prod.imageUrl
        if (!url.isNullOrBlank() && thumbnail == null) {
            thumbnail = ProductThumbnailCache.load(url)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(p.background, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(8.dp))
            .clickable { onSend() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(p.muted)
                .border(BorderStroke(1.dp, p.border), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = prod.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = prod.initial,
                    color = p.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prod.name,
                color = p.foreground,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val stockSubtitle = if (prod.stock != null) " · Stok ${prod.stock}" else ""
            Text(
                text = "${prod.priceLabel}$stockSubtitle",
                color = p.mutedForeground,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Kirim",
            color = p.foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(p.muted, RoundedCornerShape(6.dp))
                .border(BorderStroke(1.dp, p.border), RoundedCornerShape(6.dp))
                .clickable { onSend() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/* ----------------------------------------------------------------------- */
/* Unpaired / Revoked Prompt & Shared Sleek Components                     */
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
            .background(p.background, RoundedCornerShape(AulaaTheme.radius))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(AulaaTheme.radius))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = if (isRevoked) "Perangkat tidak lagi terhubung" else "Hubungkan dengan akun Merchant Aulaa",
            color = p.foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (isRevoked) {
                "Token perangkat ini telah dicabut atau kedaluwarsa. Hubungkan ulang melalui kode pairing atau scan QR dari Dashboard Web Aulaa."
            } else {
                "Buka Dashboard Web Aulaa → Account Settings → Keyboard devices, lalu scan QR atau klaim kode pairing 8 karakter."
            },
            color = p.mutedForeground,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
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

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val p = AulaaTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(p.muted, RoundedCornerShape(7.dp))
            .border(BorderStroke(1.dp, Color(0x66EF4444)), RoundedCornerShape(7.dp))
            .clickable { onDismiss() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(
            text = message,
            color = p.foreground,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text("✕", color = p.mutedForeground, fontSize = 11.sp)
    }
}

@Composable
private fun IconSquare(glyph: String, onClick: () -> Unit) {
    val p = AulaaTheme.palette
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(p.background, RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = p.mutedForeground, fontSize = 12.sp)
    }
}

@Composable
private fun SleekTabs(
    tab: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AulaaTheme.palette
    Row(
        modifier = modifier
            .background(p.background, RoundedCornerShape(7.dp))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(7.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Box(
                modifier = Modifier
                    .background(if (on) p.muted else Color.Transparent, RoundedCornerShape(5.dp))
                    .clickable { onSelect(t) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = t.label,
                    color = if (on) p.foreground else p.mutedForeground,
                    fontSize = 11.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AulaaTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                brush = if (enabled) {
                    Brush.linearGradient(listOf(p.brandMid, p.brandDeep))
                } else {
                    SolidColor(p.muted)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) p.onBrand else p.mutedForeground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AulaaTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(p.background, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = p.foreground,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
