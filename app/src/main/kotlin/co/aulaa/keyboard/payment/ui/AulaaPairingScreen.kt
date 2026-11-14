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

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import co.aulaa.keyboard.payment.data.AulaaApi
import co.aulaa.keyboard.payment.data.AulaaAttestation
import co.aulaa.keyboard.payment.data.PairingStore
import dev.patrickgold.florisboard.app.components.SegmentedListColumn
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch

/**
 * VisualTransformation that formats an 8-character pairing code as `XXXX-XXXX`
 * automatically (inserting `-` as soon as the 4th character is typed).
 */
private object PairingCodeHyphenVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length < 4) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val formatted = "${raw.substring(0, 4)}-${raw.substring(4)}"
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return if (offset < 4) offset else (offset + 1).coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                return if (offset <= 4) offset else (offset - 1).coerceAtMost(raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Companion-app Pairing & Merchant Device Management screen.
 */
@Composable
fun AulaaPairingContent(
    api: AulaaApi,
    pairingStore: PairingStore,
    initialPairingCode: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val session by pairingStore.sessionFlow.collectAsState()

    var codeInput by remember(initialPairingCode) {
        mutableStateOf(normalizePairingCodeDigits(extractCodeFromInput(initialPairingCode.orEmpty())))
    }
    var deviceNameInput by remember {
        mutableStateOf(defaultDeviceName())
    }

    var claiming by remember { mutableStateOf(false) }
    var syncingProjects by remember { mutableStateOf(false) }

    var showQrScanner by remember { mutableStateOf(false) }
    var showManualCodeDialog by remember { mutableStateOf(false) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showUnpairConfirmDialog by remember { mutableStateOf(false) }

    fun submitClaim(rawCode: String) {
        val cleanCode = extractCodeFromInput(rawCode)
        val cleanDevice = AulaaAttestation.sanitizeDeviceName(deviceNameInput)
            .ifBlank { defaultDeviceName() }
        if (cleanCode.isBlank()) {
            Toast.makeText(context, "Masukkan kode pairing 8 karakter dari Dashboard Aulaa.", Toast.LENGTH_SHORT).show()
            return
        }
        val nameErr = AulaaAttestation.validateDeviceName(cleanDevice)
        if (nameErr != null) {
            Toast.makeText(context, nameErr, Toast.LENGTH_LONG).show()
            return
        }
        claiming = true
        scope.launch {
            runCatching {
                api.claimPairing(cleanCode, cleanDevice)
            }.onSuccess { claimed ->
                claiming = false
                codeInput = ""
                Toast.makeText(
                    context,
                    "Berhasil terhubung ke ${claimed.merchant.name}",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { err ->
                claiming = false
                Toast.makeText(
                    context,
                    err.message ?: "Gagal melakukan klaim pairing.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            showManualCodeDialog = true
        }
    }

    fun openQrScannerFlow() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            showQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-claim when opened via QR deep link `aulaa-keyboard://pair?code=ABCDEFGH`
    LaunchedEffect(initialPairingCode) {
        val codeFromQr = extractCodeFromInput(initialPairingCode.orEmpty())
        if (codeFromQr.isNotBlank()) {
            codeInput = normalizePairingCodeDigits(codeFromQr)
            submitClaim(codeFromQr)
        }
    }

    Column(modifier = modifier) {
        SegmentedListColumn {
            if (session != null) {
                val count = 2
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    icon = Icons.Outlined.CheckCircle,
                    title = "Terhubung ke ${session!!.merchant.name}",
                    summary = "Akun merchant aktif",
                    onClick = {},
                )
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(1, count),
                    icon = Icons.Outlined.Smartphone,
                    title = "Nama Perangkat",
                    summary = session!!.device.name,
                    onClick = {},
                )
            } else {
                val count = 3
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    icon = Icons.Default.QrCodeScanner,
                    title = if (claiming) "Menghubungkan Perangkat…" else "Scan QR Code Pairing",
                    summary = if (claiming) {
                        "Memverifikasi perangkat & kode pairing…"
                    } else {
                        "Scan QR Code langsung dari dialog Hubungkan Keyboard di Dashboard Web Aulaa"
                    },
                    onClick = {
                        if (!claiming) {
                            openQrScannerFlow()
                        }
                    },
                )
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(1, count),
                    icon = Icons.Default.Keyboard,
                    title = "Ketik Kode Pairing Manual (XXXX-XXXX)",
                    summary = "Masukkan kode 8 karakter dari Dashboard Web Aulaa",
                    onClick = {
                        if (!claiming) {
                            showManualCodeDialog = true
                        }
                    },
                )
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(2, count),
                    icon = Icons.Outlined.Smartphone,
                    title = "Nama Perangkat",
                    summary = deviceNameInput,
                    onClick = {
                        showDeviceNameDialog = true
                    },
                )
            }
        }

        if (session != null) {
            val currentSession = session!!
            val projects = currentSession.projects

            SegmentedListColumn(title = "Project Merchant (${projects.size})") {
                val totalItems = projects.size + 2
                projects.forEachIndexed { index, prj ->
                    Preference(
                        shapes = ListItemDefaults.segmentedShapes(index, totalItems),
                        icon = Icons.Outlined.Storefront,
                        title = prj.displayLabel(),
                        summary = "${if (prj.isProd) "Production (Live)" else "Sandbox (Testing)"} · ${prj.status}",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProjectEnvironmentDot(isProd = prj.isProd)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (prj.isProd) "Live" else "Sandbox",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {},
                    )
                }
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(projects.size, totalItems),
                    icon = Icons.Default.Sync,
                    title = if (syncingProjects) "Menyinkronkan…" else "Sinkronkan Project",
                    summary = "Perbarui daftar project & produk dari server Aulaa",
                    onClick = {
                        if (!syncingProjects) {
                            syncingProjects = true
                            scope.launch {
                                runCatching { api.listProjects() }
                                    .onSuccess { list ->
                                        syncingProjects = false
                                        Toast.makeText(
                                            context,
                                            "Sinkronisasi berhasil (${list.size} project).",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    .onFailure { err ->
                                        syncingProjects = false
                                        Toast.makeText(
                                            context,
                                            err.message ?: "Gagal menyinkronkan project.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            }
                        }
                    },
                )
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(projects.size + 1, totalItems),
                    icon = Icons.Default.LinkOff,
                    title = "Putuskan Perangkat",
                    summary = "Hapus akses perangkat ini dari akun merchant",
                    onClick = {
                        showUnpairConfirmDialog = true
                    },
                )
            }
        }
    }

    if (showQrScanner) {
        AulaaQrScannerDialog(
            onDismiss = { showQrScanner = false },
            onManualInput = {
                showQrScanner = false
                showManualCodeDialog = true
            },
            onResult = { scannedRaw ->
                showQrScanner = false
                val extracted = extractCodeFromInput(scannedRaw)
                codeInput = normalizePairingCodeDigits(extracted)
                submitClaim(extracted)
            },
        )
    }

    if (showManualCodeDialog) {
        var draftRaw by remember { mutableStateOf(normalizePairingCodeDigits(codeInput)) }
        JetPrefAlertDialog(
            title = "Masukkan Kode Pairing",
            confirmLabel = "Hubungkan",
            onConfirm = {
                showManualCodeDialog = false
                codeInput = draftRaw
                val formattedCode = if (draftRaw.length > 4) {
                    "${draftRaw.substring(0, 4)}-${draftRaw.substring(4)}"
                } else {
                    draftRaw
                }
                submitClaim(formattedCode)
            },
            dismissLabel = "Batal",
            onDismiss = { showManualCodeDialog = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Masukkan kode 8 karakter dari dialog \"Hubungkan Aulaa Keyboard\" di Dashboard Web Aulaa. Tanda strip (-) ditambahkan otomatis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draftRaw,
                    onValueChange = { input ->
                        val extracted = extractCodeFromInput(input)
                        draftRaw = normalizePairingCodeDigits(extracted)
                    },
                    label = { Text("Kode Pairing (XXXX-XXXX)") },
                    placeholder = {
                        Text(
                            text = "ABCD-EFGH",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                            ),
                        )
                    },
                    visualTransformation = PairingCodeHyphenVisualTransformation,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                    ),
                    supportingText = {
                        Text("${draftRaw.length}/8 karakter")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDeviceNameDialog) {
        var draftName by remember { mutableStateOf(deviceNameInput) }
        JetPrefAlertDialog(
            title = "Nama Perangkat",
            confirmLabel = "Simpan",
            onConfirm = {
                val cleaned = AulaaAttestation.sanitizeDeviceName(draftName).ifBlank { defaultDeviceName() }
                val err = AulaaAttestation.validateDeviceName(cleaned)
                if (err != null) {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                } else {
                    deviceNameInput = cleaned
                    showDeviceNameDialog = false
                }
            },
            dismissLabel = "Batal",
            onDismiss = { showDeviceNameDialog = false },
        ) {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it.take(100) },
                placeholder = { Text("mis. Samsung Galaxy Z Fold 7") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showUnpairConfirmDialog) {
        JetPrefAlertDialog(
            title = "Putuskan Perangkat?",
            confirmLabel = "Putuskan",
            onConfirm = {
                showUnpairConfirmDialog = false
                pairingStore.clear()
                Toast.makeText(
                    context,
                    "Perangkat telah diputuskan dari akun merchant.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            dismissLabel = "Batal",
            onDismiss = { showUnpairConfirmDialog = false },
        ) {
            Text(
                text = "Keyboard di perangkat ini tidak bisa lagi membuat link pembayaran sampai dihubungkan ulang dengan Dashboard Web Aulaa.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun normalizePairingCodeDigits(raw: String): String {
    return raw.filter { it.isLetterOrDigit() }.uppercase().take(8)
}

/**
 * Extracts the pairing code if the user scans or pastes `aulaa-keyboard://pair?code=ABCDEFGH`
 * or returns the raw code otherwise.
 */
fun extractCodeFromInput(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("aulaa-keyboard://", ignoreCase = true)) {
        val parsed = runCatching { Uri.parse(trimmed).getQueryParameter("code") }.getOrNull()
        if (!parsed.isNullOrBlank()) {
            return parsed.trim().uppercase()
        }
    }
    return trimmed
}

private fun defaultDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    val combined = if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "$manufacturer $model".trim()
    }
    val candidate = AulaaAttestation.sanitizeDeviceName(combined.replaceFirstChar { it.uppercase() })
    return if (AulaaAttestation.validateDeviceName(candidate) == null) candidate else "Android Device"
}
