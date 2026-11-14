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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.k3lp.lib.text.K3Descriptor
import org.k3lp.lib.text.K3String

private enum class Tab(val label: String) {
    Transaksi("Buat Transaksi"),
    Produk("Katalog Produk"),
}

private enum class Screen { Form, Ready }

private enum class ActiveTextField {
    OrderId,
    ProductSearch,
}

private val quickNominals = listOf(50_000L, 100_000L, 150_000L, 200_000L, 500_000L)

private val LiveDotColor = Color(0xFF2196F3)
private val SandboxDotColor = Color(0xFFEF4444)

private val AulaaLightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF1A7EC2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F0FF),
    onSecondaryContainer = Color(0xFF001E31),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFF4F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

private val AulaaDarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF003060),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF43BAE8),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
)

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
            .size(8.dp)
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
    val isDark = AulaaTheme.palette.card.red < 0.3f
    val colorScheme = if (isDark) AulaaDarkColorScheme else AulaaLightColorScheme

    MaterialTheme(colorScheme = colorScheme) {
        PaymentPanelMaterialContent(
            api = api,
            onCommitText = onCommitText,
            onClose = onClose,
            modifier = modifier,
            pairingStore = pairingStore,
            onOpenPairing = onOpenPairing,
        )
    }
}

@Composable
private fun PaymentPanelMaterialContent(
    api: AulaaApi,
    onCommitText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pairingStore: PairingStore? = null,
    onOpenPairing: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val imeController = LocalImeController.current

    val sessionState = pairingStore?.sessionFlow?.collectAsState()
    val session = sessionState?.value
    val isPaired = pairingStore == null || !session?.deviceToken.isNullOrBlank()

    var tab by remember { mutableStateOf(Tab.Transaksi) }
    var screen by remember { mutableStateOf(Screen.Form) }
    var activeTextField by remember { mutableStateOf<ActiveTextField?>(null) }

    var projects by remember(session) { mutableStateOf(session?.projects.orEmpty()) }
    var selectedProject by remember(session) { mutableStateOf(session?.projects?.firstOrNull()) }
    var amount by remember { mutableLongStateOf(0L) }
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

    // Route keyboard key presses directly into Order ID or Product Search when active!
    DisposableEffect(imeController, activeTextField) {
        val field = activeTextField
        if (field != null) {
            imeController.setEmitInterceptor { emitted ->
                when (emitted) {
                    is K3String -> {
                        val raw = emitted.raw
                        if (raw != "\n" && raw != "\r") {
                            when (field) {
                                ActiveTextField.OrderId -> {
                                    // Order ID only allows alphanumeric, '-', '_', '.' up to 64 chars
                                    val filtered = raw.filter {
                                        it.isLetterOrDigit() || it == '-' || it == '_' || it == '.'
                                    }
                                    orderId = (currentOrderId + filtered).take(64)
                                }
                                ActiveTextField.ProductSearch -> {
                                    search = (currentSearch + raw).take(80)
                                }
                            }
                            // Auto-unshift after typing a character if shift was one-shot
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

    // When user is actively typing Order ID or Product Search, show the inline keyboard mode
    if (activeTextField != null) {
        InlineKeyboardInputView(
            activeField = activeTextField!!,
            orderId = orderId,
            search = search,
            products = products,
            loadingProducts = loadingData,
            onClear = {
                when (activeTextField!!) {
                    ActiveTextField.OrderId -> orderId = ""
                    ActiveTextField.ProductSearch -> search = ""
                }
            },
            onSendProduct = { prod ->
                activeTextField = null
                onCommitText(prod.buyUrl)
                onClose()
            },
            onDone = { activeTextField = null },
        )
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val isWideScreen = maxWidth >= 540.dp

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ---- Top Bar: Brand, Project Dropdown (on wide screen) or Merchant Info, Refresh, Close ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AulaaBrandMark(size = 24.dp, showContainer = false)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Aulaa Payment",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (session != null && isPaired && !tokenRevoked) {
                                Text(
                                    text = " · ${session.merchant.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    if (isPaired && !tokenRevoked) {
                        // Segmented tabs right in the header on wide screens (Z Fold 7 inner screen / Tablet)
                        if (isWideScreen) {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.height(34.dp),
                            ) {
                                Tab.entries.forEachIndexed { index, t ->
                                    SegmentedButton(
                                        selected = tab == t,
                                        onClick = {
                                            tab = t
                                            errorMessage = null
                                            if (t == Tab.Transaksi && createdLink == null) screen = Screen.Form
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Tab.entries.size),
                                    ) {
                                        Text(
                                            text = t.label,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { loadMerchantData() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sinkronkan",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kembali ke keyboard",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!isPaired || tokenRevoked) {
                    UnpairedPromptMaterial(
                        isRevoked = tokenRevoked,
                        onOpenPairing = onOpenPairing,
                    )
                    return@Column
                }

                // Segmented tabs row on compact screens (cover screen / phone portrait)
                if (!isWideScreen) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                    ) {
                        Tab.entries.forEachIndexed { index, t ->
                            SegmentedButton(
                                selected = tab == t,
                                onClick = {
                                    tab = t
                                    errorMessage = null
                                    if (t == Tab.Transaksi && createdLink == null) screen = Screen.Form
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = Tab.entries.size),
                            ) {
                                Text(
                                    text = t.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    ErrorBannerMaterial(
                        message = errorMessage!!,
                        onDismiss = { errorMessage = null },
                    )
                }

                when (tab) {
                    Tab.Transaksi -> {
                        if (isWideScreen) {
                            // =========================================================================
                            // VERSION 2: WIDE / FOLDABLE 2-PANE LAYOUT (Samsung Z Fold 7 Inner Screen)
                            // =========================================================================
                            TransaksiWideFoldableLayout(
                                projects = projects,
                                selectedProject = selectedProject,
                                onSelectProject = { prj ->
                                    selectedProject = prj
                                    loadMerchantData(projectIdFilter = prj.id)
                                },
                                amount = amount,
                                onAmount = { amount = it },
                                orderId = orderId,
                                onTapOrderId = { activeTextField = ActiveTextField.OrderId },
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
                            // =========================================================================
                            // VERSION 1: COMPACT PHONE / COVER SCREEN LAYOUT
                            // =========================================================================
                            if (screen == Screen.Ready && createdLink != null) {
                                LinkReadyMaterial(
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
                                TransaksiCompactPhoneLayout(
                                    projects = projects,
                                    selectedProject = selectedProject,
                                    onSelectProject = { prj ->
                                        selectedProject = prj
                                        loadMerchantData(projectIdFilter = prj.id)
                                    },
                                    amount = amount,
                                    onAmount = { amount = it },
                                    orderId = orderId,
                                    onTapOrderId = { activeTextField = ActiveTextField.OrderId },
                                    onClearOrderId = { orderId = "" },
                                    creating = creating,
                                    onCreate = { triggerCreateLink() },
                                )
                            }
                        }
                    }

                    Tab.Produk -> {
                        ProdukTabMaterial(
                            isWideScreen = isWideScreen,
                            products = products,
                            loading = loadingData,
                            search = search,
                            onTapSearch = { activeTextField = ActiveTextField.ProductSearch },
                            onClearSearch = { search = "" },
                            onSend = { prod ->
                                onCommitText(prod.buyUrl)
                                onClose()
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Inline Keyboard Input Mode (for Order ID / Nama Invoice & Cari Produk)  */
/* ----------------------------------------------------------------------- */

@Composable
private fun InlineKeyboardInputView(
    activeField: ActiveTextField,
    orderId: String,
    search: String,
    products: List<Product>,
    loadingProducts: Boolean,
    onClear: () -> Unit,
    onSendProduct: (Product) -> Unit,
    onDone: () -> Unit,
) {
    val isOrderId = activeField == ActiveTextField.OrderId
    val currentValue = if (isOrderId) orderId else search
    val label = if (isOrderId) "Order ID / Nama Invoice (opsional)" else "Cari Produk di Katalog"
    val placeholder = if (isOrderId) "Ketik nomor invoice, mis. INV-2026-001" else "Ketik nama produk…"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = if (currentValue.isEmpty()) placeholder else "$currentValue|",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (currentValue.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                                    color = if (currentValue.isEmpty()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (currentValue.isNotEmpty()) {
                                IconButton(
                                    onClick = onClear,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Hapus teks",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onDone,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Selesai", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // When searching products, show quick horizontal results right above the keyboard!
                if (!isOrderId) {
                    if (products.isEmpty()) {
                        Text(
                            text = if (loadingProducts) "Mencari produk…" else "Tidak ada produk yang cocok.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            products.take(10).forEach { prod ->
                                OutlinedCard(
                                    onClick = { onSendProduct(prod) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Column {
                                            Text(
                                                text = prod.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                            )
                                            Text(
                                                text = prod.priceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Kirim",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Render the actual keyboard below the input bar!
        ImeKeyboardBox()
    }
}

/* ----------------------------------------------------------------------- */
/* Project Selector Dropdown (Blue dot = Live, Red dot = Sandbox)          */
/* ----------------------------------------------------------------------- */

@Composable
private fun ProjectDropdownSelector(
    projects: List<Project>,
    selectedProject: Project?,
    onSelectProject: (Project) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { if (projects.isNotEmpty()) expanded = true },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedProject != null) {
                    ProjectEnvironmentDot(isProd = selectedProject.isProd)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Project",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = selectedProject?.displayLabel() ?: "Belum ada project",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Pilih Project",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            projects.forEach { prj ->
                val isSelected = prj.id == selectedProject?.id
                DropdownMenuItem(
                    leadingIcon = {
                        ProjectEnvironmentDot(isProd = prj.isProd)
                    },
                    text = {
                        Column {
                            Text(
                                text = prj.displayLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = if (prj.isProd) "Production (Live)" else "Sandbox (Testing)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelectProject(prj)
                    },
                )
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Order ID / Nama Invoice Selector Card                                   */
/* ----------------------------------------------------------------------- */

@Composable
private fun OrderIdFieldCard(
    orderId: String,
    onTap: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onTap,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nama Invoice / Order ID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = orderId.ifBlank { "Otomatis (ketuk untuk isi)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (orderId.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (orderId.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (orderId.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Hapus Order ID",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Nominal Display Card + Quick Chips + Intuitive Material 3 Numpad        */
/* ----------------------------------------------------------------------- */

@Composable
private fun NominalDisplayCard(
    amount: Long,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (amount > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nominal Pembayaran",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (amount > 0L) formatIdr(amount) else "Rp 0",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (amount > 0L) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (amount > 0L) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun QuickNominalRow(
    amount: Long,
    onSelectPreset: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        quickNominals.forEach { preset ->
            val selected = amount == preset
            FilterChip(
                selected = selected,
                onClick = { onSelectPreset(preset) },
                label = {
                    Text(
                        text = formatIdr(preset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.height(30.dp),
            )
        }
    }
}

@Composable
private fun MaterialAmountNumpad(
    amount: Long,
    isPresetSelected: Boolean,
    onAmountChange: (newAmount: Long, fromPreset: Boolean) -> Unit,
    keyHeightDp: Int = 36,
    modifier: Modifier = Modifier,
) {
    val maxAmount = 999_999_999L

    fun onDigitTap(digits: String) {
        val base = if (isPresetSelected || amount == 0L) "" else amount.toString()
        val combined = (base + digits).take(9)
        val next = combined.toLongOrNull() ?: 0L
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
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    val isBackspace = key == "⌫"
                    val isTripleZero = key == "000"
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "⌫" -> onAmountChange(amount / 10L, false)
                                "000" -> {
                                    if (amount > 0L) {
                                        val next = (amount.toString() + "000").take(9).toLongOrNull() ?: amount
                                        onAmountChange(next.coerceAtMost(maxAmount), false)
                                    }
                                }
                                else -> onDigitTap(key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeightDp.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isBackspace || isTripleZero) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isBackspace || isTripleZero) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    ) {
                        if (isBackspace) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Hapus",
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Version 1: Compact Phone / Cover Screen Layout                          */
/* ----------------------------------------------------------------------- */

@Composable
private fun TransaksiCompactPhoneLayout(
    projects: List<Project>,
    selectedProject: Project?,
    onSelectProject: (Project) -> Unit,
    amount: Long,
    onAmount: (Long) -> Unit,
    orderId: String,
    onTapOrderId: () -> Unit,
    onClearOrderId: () -> Unit,
    creating: Boolean,
    onCreate: () -> Unit,
) {
    var isPresetSelected by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Row 1: Project Dropdown + Order ID side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProjectDropdownSelector(
                projects = projects,
                selectedProject = selectedProject,
                onSelectProject = onSelectProject,
                modifier = Modifier.weight(1f),
            )
            OrderIdFieldCard(
                orderId = orderId,
                onTap = onTapOrderId,
                onClear = onClearOrderId,
                modifier = Modifier.weight(1f),
            )
        }

        // Row 2: Nominal Display
        NominalDisplayCard(
            amount = amount,
            onReset = {
                isPresetSelected = false
                onAmount(0L)
            },
        )

        // Row 3: Quick Nominal Chips
        QuickNominalRow(
            amount = amount,
            onSelectPreset = { preset ->
                isPresetSelected = true
                onAmount(preset)
            },
        )

        // Row 4: Clear & Intuitive Material 3 Numpad
        MaterialAmountNumpad(
            amount = amount,
            isPresetSelected = isPresetSelected,
            onAmountChange = { newAmount, fromPreset ->
                isPresetSelected = fromPreset
                onAmount(newAmount)
            },
            keyHeightDp = 34,
        )

        // Row 5: Primary Action Button
        Button(
            onClick = onCreate,
            enabled = !creating && selectedProject != null && amount > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                text = if (creating) "Membuat Link Pembayaran…" else "Buat Link Pembayaran",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Version 2: Wide / Foldable 2-Pane Layout (Samsung Z Fold 7 Inner Screen)*/
/* ----------------------------------------------------------------------- */

@Composable
private fun TransaksiWideFoldableLayout(
    projects: List<Project>,
    selectedProject: Project?,
    onSelectProject: (Project) -> Unit,
    amount: Long,
    onAmount: (Long) -> Unit,
    orderId: String,
    onTapOrderId: () -> Unit,
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // LEFT PANE: Project selector, Nominal card, Quick chips, Order ID, Create Button
        Column(
            modifier = Modifier.weight(1.08f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProjectDropdownSelector(
                    projects = projects,
                    selectedProject = selectedProject,
                    onSelectProject = onSelectProject,
                    modifier = Modifier.weight(1f),
                )
                OrderIdFieldCard(
                    orderId = orderId,
                    onTap = onTapOrderId,
                    onClear = onClearOrderId,
                    modifier = Modifier.weight(1f),
                )
            }

            NominalDisplayCard(
                amount = amount,
                onReset = {
                    isPresetSelected = false
                    onAmount(0L)
                },
            )

            QuickNominalRow(
                amount = amount,
                onSelectPreset = { preset ->
                    isPresetSelected = true
                    onAmount(preset)
                },
            )

            Button(
                onClick = onCreate,
                enabled = !creating && selectedProject != null && amount > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = if (creating) "Membuat Link Pembayaran…" else "Buat Link Pembayaran",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // RIGHT PANE: Either the Ready Link Card (after creating) or the Ergonomic Numpad
        Box(modifier = Modifier.weight(0.92f)) {
            if (screen == Screen.Ready && createdLink != null) {
                LinkReadyMaterial(
                    link = createdLink,
                    checkingStatus = checkingStatus,
                    onCheckStatus = onCheckStatus,
                    onCommit = onCommit,
                    onAgain = onAgain,
                )
            } else {
                MaterialAmountNumpad(
                    amount = amount,
                    isPresetSelected = isPresetSelected,
                    onAmountChange = { newAmount, fromPreset ->
                        isPresetSelected = fromPreset
                        onAmount(newAmount)
                    },
                    keyHeightDp = 42,
                )
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Link Pembayaran Siap (Material 3)                                       */
/* ----------------------------------------------------------------------- */

@Composable
private fun LinkReadyMaterial(
    link: PaymentLink,
    checkingStatus: Boolean,
    onCheckStatus: () -> Unit,
    onCommit: () -> Unit,
    onAgain: () -> Unit,
) {
    val statusLabel = when (link.status.lowercase()) {
        "paid", "settled", "completed" -> "Lunas ✓"
        "expired", "cancelled", "canceled" -> "Kedaluwarsa"
        else -> "Menunggu pembayaran"
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Link Pembayaran Siap",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Status: $statusLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onCheckStatus,
                    enabled = !checkingStatus,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (checkingStatus) "Mengecek…" else "Cek Status ↻",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = link.payUrl.removePrefix("https://"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatIdr(link.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val metaParts = buildList {
                link.orderId?.takeIf { it.isNotBlank() }?.let { add("Invoice: $it") }
                link.expiresAt?.takeIf { it.isNotBlank() }?.let { add("Berlaku s/d ${formatTimestampShort(it)}") }
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onAgain,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Buat Lagi")
                }
                Button(
                    onClick = onCommit,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Tempel ke Chat")
                }
            }
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
/* Katalog Produk Tab (1-column on phone, 2-column grid on Z Fold 7)       */
/* ----------------------------------------------------------------------- */

@Composable
private fun ProdukTabMaterial(
    isWideScreen: Boolean,
    products: List<Product>,
    loading: Boolean,
    search: String,
    onTapSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onSend: (Product) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Search Bar (tapping opens the inline keyboard)
        OutlinedCard(
            onClick = onTapSearch,
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cari produk",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = search.ifBlank { "Ketuk untuk cari produk di katalog…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (search.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (search.isNotBlank()) {
                    IconButton(
                        onClick = onClearSearch,
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Hapus pencarian",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (loading) "Memuat katalog produk…" else "Belum ada produk yang cocok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (isWideScreen) {
            // 2-Column Product Grid for Samsung Z Fold 7 Inner Screen / Tablet / Landscape
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(products, key = { it.id }) { prod ->
                    ProductItemCard(prod = prod, onSend = { onSend(prod) })
                }
            }
        } else {
            // 1-Column Product List for Cover Screen / Portrait Phone
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(products, key = { it.id }) { prod ->
                    ProductItemCard(prod = prod, onSend = { onSend(prod) })
                }
            }
        }
    }
}

@Composable
private fun ProductItemCard(
    prod: Product,
    onSend: () -> Unit,
) {
    OutlinedCard(
        onClick = onSend,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = prod.initial,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prod.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val stockSubtitle = if (prod.stock != null) " · Stok ${prod.stock}" else ""
                Text(
                    text = "${prod.priceLabel}$stockSubtitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            FilledTonalButton(
                onClick = onSend,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Kirim", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Unpaired / Revoked Prompt & Error Banner                                */
/* ----------------------------------------------------------------------- */

@Composable
private fun UnpairedPromptMaterial(
    isRevoked: Boolean,
    onOpenPairing: (() -> Unit)?,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isRevoked) "Perangkat Tidak Lagi Terhubung" else "Hubungkan Akun Merchant Aulaa",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isRevoked) {
                    "Token perangkat ini telah dicabut atau kedaluwarsa. Hubungkan ulang melalui kode pairing atau scan QR dari Dashboard Web Aulaa."
                } else {
                    "Buka Dashboard Web Aulaa → Account Settings → Keyboard Devices, lalu scan QR Code atau masukkan kode pairing 8 karakter."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onOpenPairing != null) {
                Button(
                    onClick = onOpenPairing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Hubungkan Perangkat (Pairing)")
                }
            }
        }
    }
}

@Composable
private fun ErrorBannerMaterial(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        onClick = onDismiss,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Tutup",
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
