package com.freshnow.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

private const val MAX_IMAGE_DIMENSION = 768
private const val JPEG_QUALITY = 80
private const val OPAQUE_ALPHA = 0xFF shl 24

/**
 * 实时预览 + 取帧回调。取帧前先问 [canAcceptFrame]，避免在请求进行或冷却期内白做一次 JPEG 编码
 */
@Composable
fun CameraPreview(
    canAcceptFrame: () -> Boolean,
    onFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(Unit) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder()
            .build()
            .apply { setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .apply {
                setAnalyzer(executor) { imageProxy ->
                    val jpeg = if (canAcceptFrame()) imageProxy.toUprightJpeg() else null
                    imageProxy.close()
                    if (jpeg != null) onFrame(jpeg)
                }
            }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

/**
 * RGBA_8888 输出的字节序是 R,G,B,A（见 ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888），
 * 而 Bitmap 的 ARGB_8888 内存序是 B,G,R,A，直接 copyPixelsFromBuffer 会红蓝互换，
 * 因此这里逐像素打包成 0xAARRGGBB
 */
private fun ImageProxy.toUprightJpeg(): ByteArray? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    buffer.rewind()

    val pixels = IntArray(width * height)
    val row = ByteArray(rowStride)
    for (y in 0 until height) {
        val available = minOf(rowStride, buffer.remaining())
        if (available <= 0) break
        buffer.get(row, 0, available)
        val rowStart = y * width
        for (x in 0 until width) {
            val index = x * pixelStride
            if (index + 2 >= available) break
            val red = row[index].toInt() and 0xFF
            val green = row[index + 1].toInt() and 0xFF
            val blue = row[index + 2].toInt() and 0xFF
            pixels[rowStart + x] = OPAQUE_ALPHA or (red shl 16) or (green shl 8) or blue
        }
    }

    var bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    val rotation = imageInfo.rotationDegrees
    if (rotation != 0) {
        bitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true
        ).also { bitmap.recycle() }
    }

    val longSide = max(bitmap.width, bitmap.height)
    if (longSide > MAX_IMAGE_DIMENSION) {
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / longSide
        bitmap = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        ).also { bitmap.recycle() }
    }

    val jpeg = runCatching {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }.getOrNull()
    bitmap.recycle()
    return jpeg
}
