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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.EnumMap

@Composable
fun AulaaQrScannerDialog(
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var scannedOnce by remember { mutableStateOf(false) }

    // Background decoding loop using ZXing QRCodeReader over TextureView frames
    LaunchedEffect(textureViewRef) {
        val tv = textureViewRef ?: return@LaunchedEffect
        val reader = QRCodeReader()
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
        }
        val frameSize = 420
        val pixels = IntArray(frameSize * frameSize)

        while (isActive && !scannedOnce) {
            delay(220)
            if (!tv.isAvailable) continue
            val bmp = runCatching { tv.getBitmap(frameSize, frameSize) }.getOrNull() ?: continue
            val decodedText = withContext(Dispatchers.Default) {
                try {
                    val w = bmp.width
                    val h = bmp.height
                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                    val source = RGBLuminanceSource(w, h, pixels)
                    val result = runCatching {
                        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
                    }.getOrElse {
                        reader.reset()
                        runCatching {
                            reader.decode(BinaryBitmap(HybridBinarizer(source.invert())), hints)
                        }.getOrNull()
                    }
                    reader.reset()
                    result?.text?.takeIf { it.isNotBlank() }
                } finally {
                    bmp.recycle()
                }
            }
            if (!decodedText.isNullOrBlank() && !scannedOnce) {
                scannedOnce = true
                onResult(decodedText)
                break
            }
        }
    }

    JetPrefAlertDialog(
        title = "Scan QR Code Pairing",
        dismissLabel = "Batal",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Arahkan kamera ke QR Code pada dialog \"Hubungkan Aulaa Keyboard\" di Dashboard Web Aulaa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CameraPreviewSurface(
                    context = context,
                    onTextureViewReady = { textureViewRef = it },
                    onError = { cameraError = it },
                )

                // Viewfinder frame overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSide = size.minDimension * 0.68f
                    val left = (size.width - boxSide) / 2f
                    val top = (size.height - boxSide) / 2f
                    drawRoundRect(
                        color = Color(0xFF43BAE8),
                        topLeft = Offset(left, top),
                        size = Size(boxSide, boxSide),
                        cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }

                if (cameraError != null) {
                    Text(
                        text = cameraError!!,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewSurface(
    context: Context,
    onTextureViewReady: (TextureView) -> Unit,
    onError: (String) -> Unit,
) {
    val controller = remember { Camera2PreviewController(context, onError) }

    DisposableEffect(controller) {
        onDispose {
            controller.stop()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        controller.start(this@apply, surface, width, height)
                        onTextureViewReady(this@apply)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        controller.updateTransform(this@apply, width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        controller.stop()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private class Camera2PreviewController(
    private val context: Context,
    private val onError: (String) -> Unit,
) {
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewWidth: Int = 1080
    private var previewHeight: Int = 1080

    @SuppressLint("MissingPermission")
    fun start(textureView: TextureView, surfaceTexture: SurfaceTexture, viewWidth: Int, viewHeight: Int) {
        stop()
        val thread = HandlerThread("AulaaQrCameraThread").apply { start() }
        val handler = Handler(thread.looper)
        cameraThread = thread
        cameraHandler = handler

        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                onError("Kamera tidak ditemukan di perangkat ini.")
                return
            }

            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val chosen = sizes
                .filter { it.width in 640..1920 && it.height in 480..1920 }
                .minByOrNull { kotlin.math.abs(it.width - 1280) + kotlin.math.abs(it.height - 960) }
                ?: sizes.firstOrNull()

            if (chosen != null) {
                previewWidth = chosen.width
                previewHeight = chosen.height
                surfaceTexture.setDefaultBufferSize(chosen.width, chosen.height)
            } else {
                surfaceTexture.setDefaultBufferSize(1280, 720)
            }

            textureView.post {
                updateTransform(textureView, viewWidth, viewHeight)
            }

            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createPreviewSession(camera, surfaceTexture, handler)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        onError("Gagal membuka kamera (kode $error).")
                    }
                },
                handler,
            )
        } catch (e: Exception) {
            onError("Gagal mengakses kamera: ${e.localizedMessage ?: "izin ditolak"}")
        }
    }

    private fun createPreviewSession(
        camera: CameraDevice,
        surfaceTexture: SurfaceTexture,
        handler: Handler,
    ) {
        try {
            val surface = Surface(surfaceTexture)
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            }
            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        runCatching {
                            session.setRepeatingRequest(requestBuilder.build(), null, handler)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError("Gagal memulai pratinjau kamera.")
                    }
                },
                handler,
            )
        } catch (e: Exception) {
            onError("Gagal mengonfigurasi kamera.")
        }
    }

    fun updateTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val matrix = Matrix()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        // In portrait orientation, camera sensor is typically rotated 90 degrees
        val bufferRatio = previewHeight.toFloat() / previewWidth.toFloat().coerceAtLeast(1f)
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (bufferRatio > viewRatio) {
            scaleY = 1f
            scaleX = bufferRatio / viewRatio
        } else {
            scaleX = 1f
            scaleY = viewRatio / bufferRatio
        }
        matrix.postScale(scaleX, scaleY, centerX, centerY)
        textureView.setTransform(matrix)
    }

    fun stop() {
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { cameraThread?.quitSafely() }
        cameraThread = null
        cameraHandler = null
    }
}
