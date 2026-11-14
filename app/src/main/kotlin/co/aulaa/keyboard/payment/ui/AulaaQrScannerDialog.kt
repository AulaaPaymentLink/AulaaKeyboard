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
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun AulaaQrScannerDialog(
    onDismiss: () -> Unit,
    onManualInput: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val scannedOnce = remember { AtomicBoolean(false) }
    val currentOnResult by rememberUpdatedState(onResult)

    fun deliverResult(rawText: String) {
        val clean = rawText.trim()
        if (clean.isNotEmpty() && scannedOnce.compareAndSet(false, true)) {
            Handler(Looper.getMainLooper()).post {
                currentOnResult(clean)
            }
        }
    }

    // Secondary fallback decoding loop over TextureView bitmap in case ImageReader stream isn't used
    LaunchedEffect(textureViewRef) {
        val tv = textureViewRef ?: return@LaunchedEffect
        val reader = MultiFormatReader().apply {
            setHints(createZxingHints())
        }
        val frameSize = 640
        val pixels = IntArray(frameSize * frameSize)

        while (isActive && !scannedOnce.get()) {
            delay(280)
            if (!tv.isAvailable || scannedOnce.get()) continue
            val bmp = runCatching { tv.getBitmap(frameSize, frameSize) }.getOrNull() ?: continue
            val decodedText = withContext(Dispatchers.Default) {
                try {
                    val w = bmp.width
                    val h = bmp.height
                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                    val source = RGBLuminanceSource(w, h, pixels)
                    decodeFromSource(reader, source)
                } finally {
                    bmp.recycle()
                }
            }
            if (!decodedText.isNullOrBlank()) {
                deliverResult(decodedText)
                break
            }
        }
    }

    JetPrefAlertDialog(
        title = "Scan QR Code Pairing",
        confirmLabel = "Ketik Kode Manual",
        onConfirm = onManualInput,
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
                    onQrDecoded = { deliverResult(it) },
                    onError = { cameraError = it },
                )

                // Viewfinder frame overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSide = size.minDimension * 0.70f
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

private fun createZxingHints(): Map<DecodeHintType, Any> {
    return EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.TRY_HARDER, true)
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
    }
}

private fun decodeFromSource(reader: MultiFormatReader, source: LuminanceSource): String? {
    // 1. Standard HybridBinarizer
    runCatching {
        return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.onFailure { reader.reset() }

    // 2. GlobalHistogramBinarizer (better on screen glare / moire patterns)
    runCatching {
        return reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
    }.onFailure { reader.reset() }

    // 3. Inverted source (for dark-mode QR codes)
    val inverted = runCatching { source.invert() }.getOrNull()
    if (inverted != null) {
        runCatching {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(inverted))).text
        }.onFailure { reader.reset() }
    }
    return null
}

@Composable
private fun CameraPreviewSurface(
    context: Context,
    onTextureViewReady: (TextureView) -> Unit,
    onQrDecoded: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val controller = remember { Camera2PreviewController(context, onQrDecoded, onError) }

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
    private val onQrDecoded: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewWidth: Int = 1280
    private var previewHeight: Int = 720
    private val isProcessingFrame = AtomicBoolean(false)
    private var lastFrameTimestampMs: Long = 0L

    private val zxingReader = MultiFormatReader().apply {
        setHints(createZxingHints())
    }

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
            val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val chosenPreview = previewSizes
                .filter { it.width in 640..1920 && it.height in 480..1920 }
                .minByOrNull { kotlin.math.abs(it.width - 1280) + kotlin.math.abs(it.height - 720) }
                ?: previewSizes.firstOrNull()

            if (chosenPreview != null) {
                previewWidth = chosenPreview.width
                previewHeight = chosenPreview.height
                surfaceTexture.setDefaultBufferSize(chosenPreview.width, chosenPreview.height)
            } else {
                surfaceTexture.setDefaultBufferSize(1280, 720)
            }

            val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
            val chosenYuv = yuvSizes
                .filter { it.width in 640..1600 && it.height in 480..1200 }
                .minByOrNull { kotlin.math.abs(it.width - 1280) + kotlin.math.abs(it.height - 720) }
                ?: chosenPreview

            val readerW = chosenYuv?.width ?: 1280
            val readerH = chosenYuv?.height ?: 720
            val reader = ImageReader.newInstance(readerW, readerH, ImageFormat.YUV_420_888, 2)
            reader.setOnImageAvailableListener(
                { ir ->
                    val image = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTimestampMs < 120L || !isProcessingFrame.compareAndSet(false, true)) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    lastFrameTimestampMs = now
                    try {
                        val decoded = decodeYuvImage(image)
                        if (!decoded.isNullOrBlank()) {
                            onQrDecoded(decoded)
                        }
                    } catch (_: Throwable) {
                    } finally {
                        image.close()
                        isProcessingFrame.set(false)
                    }
                },
                handler,
            )
            imageReader = reader

            textureView.post {
                updateTransform(textureView, viewWidth, viewHeight)
            }

            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createPreviewSession(camera, surfaceTexture, reader.surface, handler)
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

    private fun decodeYuvImage(image: Image): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val yData = ByteArray(width * height)
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(yData, 0, width * height)
        } else {
            val rowBuffer = ByteArray(rowStride)
            var outOffset = 0
            for (row in 0 until height) {
                val bytesToRead = if (row == height - 1) {
                    kotlin.math.min(rowStride, buffer.remaining())
                } else {
                    rowStride
                }
                buffer.get(rowBuffer, 0, bytesToRead)
                if (pixelStride == 1) {
                    System.arraycopy(rowBuffer, 0, yData, outOffset, width)
                    outOffset += width
                } else {
                    for (col in 0 until width) {
                        val idx = col * pixelStride
                        yData[outOffset++] = if (idx < bytesToRead) rowBuffer[idx] else 0
                    }
                }
            }
        }

        // 1. Try unrotated YUV frame (center crop 85% + full frame)
        val source0 = PlanarYUVLuminanceSource(yData, width, height, 0, 0, width, height, false)
        decodeFromSource(zxingReader, source0)?.let { return it }

        // 2. Try 90-degree rotated YUV frame (for portrait camera sensor orientation)
        val rotated = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[x * height + (height - y - 1)] = yData[y * width + x]
            }
        }
        val source90 = PlanarYUVLuminanceSource(rotated, height, width, 0, 0, height, width, false)
        decodeFromSource(zxingReader, source90)?.let { return it }

        return null
    }

    private fun createPreviewSession(
        camera: CameraDevice,
        surfaceTexture: SurfaceTexture,
        readerSurface: Surface?,
        handler: Handler,
    ) {
        try {
            val previewSurface = Surface(surfaceTexture)
            val targets = if (readerSurface != null) {
                listOf(previewSurface, readerSurface)
            } else {
                listOf(previewSurface)
            }
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                targets.forEach { addTarget(it) }
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
                set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON,
                )
            }
            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                targets,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        runCatching {
                            session.setRepeatingRequest(requestBuilder.build(), null, handler)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (readerSurface != null) {
                            // Fallback to preview-only if dual surface is rejected by hardware
                            createPreviewSession(camera, surfaceTexture, null, handler)
                        } else {
                            onError("Gagal memulai pratinjau kamera.")
                        }
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
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { cameraThread?.quitSafely() }
        cameraThread = null
        cameraHandler = null
    }
}
