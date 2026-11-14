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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.aulaa.keyboard.payment.data.AulaaApi
import co.aulaa.keyboard.payment.data.AulaaAttestation
import co.aulaa.keyboard.payment.data.PairingStore
import dev.patrickgold.florisboard.app.components.SegmentedListColumn
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch

/**
 * Companion-app Pairing & Merchant Device Management screen.
 *
 * Uses the exact same Material 3 [SegmentedListColumn], [Preference], and
 * [JetPrefAlertDialog] components as all other settings screens in the app,
 * plus an integrated camera QR scanner ([AulaaQrScannerDialog]) for scanning
 * the pairing barcode directly from the Aulaa Web Dashboard.
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
        mutableStateOf(extractCodeFromInput(initialPairingCode.orEmpty()))
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

    fun openPairingFlow() {
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
            codeInput = codeFromQr
            submitClaim(codeFromQr)
        }
    }

    Column(modifier = modifier) {
        SegmentedListColumn {
            val count = 2
            if (session != null) {
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
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    icon = Icons.Default.QrCodeScanner,
                    title = if (claiming) "Menghubungkan Perangkat…" else "Hubungkan Perangkat",
                    summary = if (claiming) {
                        "Memverifikasi perangkat & kode pairing…"
                    } else {
                        "Belum terhubung · Scan QR Code atau ketik kode dari Dashboard Web Aulaa"
                    },
                    onClick = {
                        if (!claiming) {
                            openPairingFlow()
                        }
                    },
                )
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(1, count),
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
                        title = prj.name,
                        summary = "${if (prj.isProd) "Production" else "Sandbox"} · ${prj.status}",
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
                codeInput = extracted
                submitClaim(extracted)
            },
        )
    }

    if (showManualCodeDialog) {
        var draftCode by remember { mutableStateOf(codeInput) }
        JetPrefAlertDialog(
            title = "Masukkan Kode Pairing",
            confirmLabel = "Hubungkan",
            onConfirm = {
                showManualCodeDialog = false
                codeInput = draftCode
                submitClaim(draftCode)
            },
            dismissLabel = "Batal",
            onDismiss = { showManualCodeDialog = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Masukkan kode 8 karakter dari dialog \"Hubungkan Aulaa Keyboard\" di Dashboard Web Aulaa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draftCode,
                    onValueChange = { draftCode = extractCodeFromInput(it) },
                    placeholder = { Text("ABCD-EFGH") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
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
                placeholder = { Text("mis. Pixel 8 Pro") },
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
