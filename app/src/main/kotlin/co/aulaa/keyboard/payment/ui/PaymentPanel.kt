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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
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
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
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

private data class KeyboardPanelTokens(
    val windowBg: Color,
    val keyBg: Color,
    val keyAltBg: Color,
    val containerBg: Color,
    val fg: Color,
    val mutedFg: Color,
    val keyShape: Shape,
    val brandDeep: Color,
    val brandLight: Color,
    val onBrand: Color,
)

@Composable
private fun rememberKeyboardPanelTokens(): KeyboardPanelTokens {
    val p = AulaaTheme.palette
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val keyPressedStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName, selector = SnyggSelector.PRESSED)
    val keyHintStyle = rememberSnyggThemeQuery(FlorisImeUi.KeyHint.elementName)

    val windowBg = windowStyle.background(default = p.keyboardBackground)
    val keyBg = keyStyle.background(default = p.key)
    val keyAltBg = keyPressedStyle.background(default = p.keyAlt)
    val fg = keyStyle.foreground(default = p.keyForeground)
    val mutedFg = keyHintStyle.foreground(default = fg.copy(alpha = 0.62f))
    val keyShape = keyStyle.shape()
    val isDark = windowBg.red < 0.35f && windowBg.green < 0.35f && windowBg.blue < 0.35f
    val containerBg = if (isDark) keyBg.copy(alpha = 0.55f) else keyBg.copy(alpha = 0.85f)

    return remember(windowBg, keyBg, keyAltBg, containerBg, fg, mutedFg, keyShape, p) {
        KeyboardPanelTokens(
            windowBg = windowBg,
            keyBg = keyBg,
            keyAltBg = keyAltBg,
            containerBg = containerBg,
            fg = fg,
            mutedFg = mutedFg,
            keyShape = keyShape,
            brandDeep = p.brandDeep,
            brandLight = p.brandLight,
            onBrand = p.onBrand,
        )
    }
}

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
    val t = rememberKeyboardPanelTokens()
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

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loadingData by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tokenRevoked by remember { mutableStateOf(false) }

    val currentOrderId by rememberUpdatedState(orderId)
    val currentSearch by rememberUpdatedState(search)

    // Route keyboard key presses directly into Nama Invoice or Cari Produk when active
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(t.windowBg)
            .padding(top = 6.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        ) {
            val isWideScreen = maxWidth >= 540.dp

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // ---- Header Row: Logo + Aulaa + Merchant Name + Refresh & Close Icons ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AulaaBrandMark(size = 20.dp, showContainer = false)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Aulaa",
                            color = t.fg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (session != null && isPaired && !tokenRevoked) {
                            Text(
                                text = "  ·  ${session.merchant.name}",
                                color = t.mutedFg,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        if (isPaired && !tokenRevoked) {
                            IconButton(
                                onClick = { loadMerchantData() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sinkronkan",
                                    tint = t.mutedFg,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = t.mutedFg,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    if (!isPaired || tokenRevoked) {
                        UnpairedPrompt(
                            isRevoked = tokenRevoked,
                            t = t,
                            onOpenPairing = onOpenPairing,
                        )
                        Spacer(Modifier.height(6.dp))
                        return@BoxWithConstraints
                    }

                    // ---- Dedicated Tabs Row (FlorisBoard key-styled segmented row) ----
                    KeyboardTabs(
                        tab = tab,
                        t = t,
                        onSelect = {
                            tab = it
                            activeTextField = null
                            projectDropdownOpen = false
                            errorMessage = null
                            if (it == Tab.Transaksi && createdLink == null) screen = Screen.Form
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (errorMessage != null) {
                        ErrorBanner(
                            message = errorMessage!!,
                            t = t,
                            onDismiss = { errorMessage = null },
                        )
                    }

                    when (tab) {
                        Tab.Transaksi -> {
                            if (isWideScreen) {
                                TransaksiWideFoldableLayout(
                                    t = t,
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
                                    onCommit = {
                                        createdLink?.let { onCommitText(it.payUrl) }
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
                                        t = t,
                                        onCommit = { onCommitText(createdLink!!.payUrl) },
                                        onAgain = {
                                            createdLink = null
                                            screen = Screen.Form
                                        },
                                    )
                                } else {
                                    TransaksiCompactLayout(
                                        t = t,
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
                                t = t,
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
                                },
                            )
                        }
                    }
                }

                // ---- In-Panel Floating Project Dropdown Overlay ----
                if (projectDropdownOpen && projects.isNotEmpty() && tab == Tab.Transaksi) {
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
                            .padding(top = 108.dp)
                            .fillMaxWidth(if (isWideScreen) 0.52f else 1f)
                            .heightIn(max = 160.dp)
                            .zIndex(10f)
                            .shadow(6.dp, t.keyShape)
                            .clip(t.keyShape)
                            .background(t.keyBg, t.keyShape)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        projects.forEachIndexed { index, prj ->
                            val isSelected = prj.id == selectedProject?.id
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(t.fg.copy(alpha = 0.08f)),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) t.keyAltBg else Color.Transparent)
                                    .clickable {
                                        projectDropdownOpen = false
                                        selectedProject = prj
                                        loadMerchantData(projectIdFilter = prj.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                ProjectEnvironmentDot(isProd = prj.isProd)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = prj.displayLabel(),
                                    color = t.fg,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = t.brandLight,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-place keyboard shown directly underneath when typing Nama Invoice or Cari Produk
        if (activeTextField != null) {
            Spacer(Modifier.height(4.dp))
            ImeKeyboardBox()
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Project Selector & Nama Invoice (Opsional) Field                        */
/* ----------------------------------------------------------------------- */

@Composable
private fun ProjectSelectorTrigger(
    selectedProject: Project?,
    expanded: Boolean,
    t: KeyboardPanelTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .shadow(1.5.dp, t.keyShape)
            .clip(t.keyShape)
            .background(t.keyBg, t.keyShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
    ) {
        if (selectedProject != null) {
            ProjectEnvironmentDot(isProd = selectedProject.isProd)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = selectedProject?.displayLabel() ?: "Pilih project",
            color = t.fg,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = t.mutedFg,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun InlineOrderIdField(
    orderId: String,
    isEditing: Boolean,
    t: KeyboardPanelTokens,
    onToggleEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(t.keyShape)
            .background(if (isEditing) t.keyBg else t.containerBg, t.keyShape)
            .clickable { onToggleEdit() }
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = when {
                isEditing && orderId.isEmpty() -> "|"
                isEditing -> "$orderId|"
                orderId.isNotEmpty() -> orderId
                else -> "Nama Invoice (Opsional)"
            },
            color = if (orderId.isEmpty() && !isEditing) t.mutedFg else t.fg,
            fontSize = 12.5.sp,
            fontWeight = if (orderId.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (orderId.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Hapus",
                tint = t.mutedFg,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onClear() },
            )
        }
        if (isEditing) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Selesai",
                color = t.onBrand,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(t.keyShape)
                    .background(t.brandDeep, t.keyShape)
                    .clickable { onToggleEdit() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Nominal Display + Quick Chips + FlorisBoard-Matched Numpad              */
/* ----------------------------------------------------------------------- */

@Composable
private fun NominalBox(
    amount: Long,
    t: KeyboardPanelTokens,
    showNumpad: Boolean = false,
    onToggleNumpad: (() -> Unit)? = null,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(t.keyShape)
            .background(t.containerBg, t.keyShape)
            .then(if (onToggleNumpad != null) Modifier.clickable { onToggleNumpad() } else Modifier)
            .padding(horizontal = 12.dp),
    ) {
        Text("Rp ", color = t.mutedFg, fontSize = 14.sp)
        Text(
            text = if (amount > 0L) formatIdr(amount).removePrefix("Rp").trim() else "0",
            color = if (amount > 0L) t.fg else t.mutedFg,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (showNumpad && amount > 0L) {
            Text(
                text = "Reset",
                color = t.mutedFg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(t.keyShape)
                    .background(t.keyAltBg, t.keyShape)
                    .clickable { onReset() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (onToggleNumpad != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (showNumpad) "Selesai" else "Ketik nominal",
                    color = t.brandLight,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = if (showNumpad) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = t.brandLight,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickNominalChips(
    amount: Long,
    t: KeyboardPanelTokens,
    onSelectPreset: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        quickNominals.forEach { preset ->
            val on = preset == amount
            Box(
                modifier = Modifier
                    .shadow(if (on) 0.dp else 1.dp, t.keyShape)
                    .clip(t.keyShape)
                    .background(if (on) t.keyAltBg else t.keyBg, t.keyShape)
                    .clickable { onSelectPreset(preset) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Text(
                    text = formatIdr(preset),
                    color = if (on) t.brandLight else t.fg,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SleekAmountNumpad(
    amount: Long,
    isPresetSelected: Boolean,
    t: KeyboardPanelTokens,
    onAmountChange: (newAmount: Long, fromPreset: Boolean) -> Unit,
    keyHeightDp: Int = 34,
    modifier: Modifier = Modifier,
) {
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
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { key ->
                    val isBackspace = key == "⌫"
                    val isAction = isBackspace || key == "000"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeightDp.dp)
                            .shadow(1.5.dp, t.keyShape)
                            .clip(t.keyShape)
                            .background(if (isAction) t.keyAltBg else t.keyBg, t.keyShape)
                            .clickable {
                                when (key) {
                                    "⌫" -> onAmountChange(amount / 10L, false)
                                    "000" -> if (amount > 0L) appendDigits("000")
                                    else -> appendDigits(key)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isBackspace) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Hapus",
                                tint = t.mutedFg,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text(
                                text = key,
                                color = if (isAction) t.mutedFg else t.fg,
                                fontSize = if (isAction) 13.sp else 16.sp,
                                fontWeight = FontWeight.Normal,
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
private fun TransaksiCompactLayout(
    t: KeyboardPanelTokens,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProjectSelectorTrigger(
                selectedProject = selectedProject,
                expanded = projectDropdownOpen,
                t = t,
                onClick = onToggleProjectDropdown,
                modifier = Modifier.weight(1f),
            )
            InlineOrderIdField(
                orderId = orderId,
                isEditing = isEditingOrderId,
                t = t,
                onToggleEdit = onToggleOrderIdEdit,
                onClear = onClearOrderId,
                modifier = Modifier.weight(1f),
            )
        }

        if (!isEditingOrderId) {
            NominalBox(
                amount = amount,
                t = t,
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
                    t = t,
                    onAmountChange = { newAmount, fromPreset ->
                        isPresetSelected = fromPreset
                        onAmount(newAmount)
                    },
                    keyHeightDp = 32,
                )
            }

            QuickNominalChips(
                amount = amount,
                t = t,
                onSelectPreset = { preset ->
                    isPresetSelected = true
                    onAmount(preset)
                },
            )

            PrimaryButton(
                text = if (creating) "Membuat…" else "Buat Link Pembayaran",
                enabled = !creating && selectedProject != null && amount > 0L,
                t = t,
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
    t: KeyboardPanelTokens,
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
    onCommit: () -> Unit,
    onAgain: () -> Unit,
) {
    var isPresetSelected by remember { mutableStateOf(true) }

    if (isEditingOrderId) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProjectSelectorTrigger(
                selectedProject = selectedProject,
                expanded = projectDropdownOpen,
                t = t,
                onClick = onToggleProjectDropdown,
                modifier = Modifier.weight(1f),
            )
            InlineOrderIdField(
                orderId = orderId,
                isEditing = true,
                t = t,
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
        Column(
            modifier = Modifier.weight(1.08f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProjectSelectorTrigger(
                    selectedProject = selectedProject,
                    expanded = projectDropdownOpen,
                    t = t,
                    onClick = onToggleProjectDropdown,
                    modifier = Modifier.weight(1f),
                )
                InlineOrderIdField(
                    orderId = orderId,
                    isEditing = false,
                    t = t,
                    onToggleEdit = onToggleOrderIdEdit,
                    onClear = onClearOrderId,
                    modifier = Modifier.weight(1f),
                )
            }

            NominalBox(
                amount = amount,
                t = t,
                showNumpad = true,
                onReset = {
                    isPresetSelected = false
                    onAmount(0L)
                },
            )

            QuickNominalChips(
                amount = amount,
                t = t,
                onSelectPreset = { preset ->
                    isPresetSelected = true
                    onAmount(preset)
                },
            )

            PrimaryButton(
                text = if (creating) "Membuat…" else "Buat Link Pembayaran",
                enabled = !creating && selectedProject != null && amount > 0L,
                t = t,
                onClick = onCreate,
            )
        }

        Box(modifier = Modifier.weight(0.92f)) {
            if (screen == Screen.Ready && createdLink != null) {
                LinkReady(
                    link = createdLink,
                    t = t,
                    onCommit = onCommit,
                    onAgain = onAgain,
                )
            } else {
                SleekAmountNumpad(
                    amount = amount,
                    isPresetSelected = isPresetSelected,
                    t = t,
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
/* Link Pembayaran Siap (Clean, no Check Status)                           */
/* ----------------------------------------------------------------------- */

@Composable
private fun LinkReady(
    link: PaymentLink,
    t: KeyboardPanelTokens,
    onCommit: () -> Unit,
    onAgain: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(t.keyShape)
                .background(t.containerBg, t.keyShape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = t.brandLight,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Link pembayaran siap",
                    color = t.fg,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = link.payUrl.removePrefix("https://"),
                    color = t.fg,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatIdr(link.amount),
                    color = t.brandLight,
                    fontSize = 12.5.sp,
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
                    color = t.mutedFg,
                    fontSize = 11.sp,
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
                t = t,
                onClick = onAgain,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Tempel ke chat",
                enabled = true,
                t = t,
                onClick = onCommit,
                modifier = Modifier.weight(1.4f),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = t.onBrand,
                        modifier = Modifier.size(15.dp),
                    )
                },
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
    t: KeyboardPanelTokens,
    isWideScreen: Boolean,
    isKeyboardOpen: Boolean,
    products: List<Product>,
    loading: Boolean,
    search: String,
    onToggleSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onSend: (Product) -> Unit,
) {
    val maxListHeight = if (isKeyboardOpen) 105.dp else 190.dp

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(t.keyShape)
                .background(if (isKeyboardOpen) t.keyBg else t.containerBg, t.keyShape)
                .clickable { onToggleSearch() }
                .padding(horizontal = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = t.mutedFg,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = when {
                    isKeyboardOpen && search.isEmpty() -> "|"
                    isKeyboardOpen -> "$search|"
                    search.isNotEmpty() -> search
                    else -> "Cari produk…"
                },
                color = if (search.isEmpty() && !isKeyboardOpen) t.mutedFg else t.fg,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (search.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Hapus",
                    tint = t.mutedFg,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onClearSearch() },
                )
            }
            if (isKeyboardOpen) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Selesai",
                    color = t.onBrand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(t.keyShape)
                        .background(t.brandDeep, t.keyShape)
                        .clickable { onToggleSearch() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
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
                    color = t.mutedFg,
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
                    ProductRow(prod = prod, t = t, onSend = { onSend(prod) })
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
                    ProductRow(prod = prod, t = t, onSend = { onSend(prod) })
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    prod: Product,
    t: KeyboardPanelTokens,
    onSend: () -> Unit,
) {
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
            .shadow(1.5.dp, t.keyShape)
            .clip(t.keyShape)
            .background(t.keyBg, t.keyShape)
            .clickable { onSend() }
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(t.keyShape)
                .background(t.keyAltBg),
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
                    color = t.fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prod.name,
                color = t.fg,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val stockSubtitle = if (prod.stock != null) " · Stok ${prod.stock}" else ""
            Text(
                text = "${prod.priceLabel}$stockSubtitle",
                color = t.mutedFg,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(t.keyShape)
                .background(t.keyAltBg, t.keyShape)
                .clickable { onSend() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = "Kirim",
                color = t.fg,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Unpaired / Revoked Prompt & FlorisBoard-Matched Controls                */
/* ----------------------------------------------------------------------- */

@Composable
private fun UnpairedPrompt(
    isRevoked: Boolean,
    t: KeyboardPanelTokens,
    onOpenPairing: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(t.keyShape)
            .background(t.containerBg, t.keyShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (isRevoked) "Perangkat tidak lagi terhubung" else "Hubungkan dengan akun Merchant Aulaa",
            color = t.fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (isRevoked) {
                "Token perangkat ini telah dicabut atau kedaluwarsa. Hubungkan ulang melalui kode pairing atau scan QR dari Dashboard Web Aulaa."
            } else {
                "Buka Dashboard Web Aulaa → Account Settings → Keyboard devices, lalu scan QR atau klaim kode pairing 8 karakter."
            },
            color = t.mutedFg,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        if (onOpenPairing != null) {
            PrimaryButton(
                text = "Hubungkan Perangkat (Pairing)",
                enabled = true,
                t = t,
                onClick = onOpenPairing,
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    t: KeyboardPanelTokens,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(t.keyShape)
            .background(t.keyAltBg, t.keyShape)
            .clickable { onDismiss() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = message,
            color = Color(0xFFEF4444),
            fontSize = 11.5.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Tutup",
            tint = t.mutedFg,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun KeyboardTabs(
    tab: Tab,
    t: KeyboardPanelTokens,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(t.keyShape)
            .background(t.containerBg, t.keyShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Tab.entries.forEach { entry ->
            val on = entry == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (on) Modifier.shadow(1.5.dp, t.keyShape) else Modifier)
                    .clip(t.keyShape)
                    .background(if (on) t.keyBg else Color.Transparent, t.keyShape)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    color = if (on) t.fg else t.mutedFg,
                    fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
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
    t: KeyboardPanelTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .shadow(if (enabled) 2.dp else 0.dp, t.keyShape)
            .clip(t.keyShape)
            .background(if (enabled) t.brandDeep else t.keyAltBg, t.keyShape)
            .clickable(enabled = enabled) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = if (enabled) t.onBrand else t.mutedFg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OutlineButton(
    text: String,
    t: KeyboardPanelTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .shadow(1.5.dp, t.keyShape)
            .clip(t.keyShape)
            .background(t.keyBg, t.keyShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = t.fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
