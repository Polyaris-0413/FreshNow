package com.freshnow.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

private const val MAX_IMAGE_DIMENSION = 768
private const val JPEG_QUALITY = 80
private const val PREVIEW_FADE_IN_MS = 250
private const val OPAQUE_ALPHA = 0xFF shl 24

/**
 * 实时预览 + 取帧回调。取帧前先问 [canAcceptFrame]，避免在请求进行或冷却期内白做一次 JPEG 编码
 *
 * [torchOn] 由调用方持有：相机实例每次重新绑定（换版式、Activity 重建）都会回到关灯状态，
 * 状态留在这里就会与硬件失步——调用方拿着它，重绑后本组件按它再下发一次，两端始终一致。
 */
@Composable
fun CameraPreview(
    torchOn: Boolean,
    onTorchAvailabilityChange: (Boolean) -> Unit,
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
            // 默认的 PERFORMANCE 模式用 SurfaceView，其内容由系统合成器单独合成，
            // 不参与 Compose 的位移/淡出，页面退出时预览会慢半拍；
            // COMPATIBLE 用 TextureView，作为普通 View 跟随页面一起动
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // 相机从打开到出第一帧要几百毫秒，这段时间预览层是纯黑，画面会"啪"地跳出。
    // 等预览流真正开始出帧后再淡入，黑屏期间露出的是外层容器的 surfaceContainerHighest 底色
    var streaming by remember { mutableStateOf(false) }
    val previewAlpha by animateFloatAsState(
        targetValue = if (streaming) 1f else 0f,
        animationSpec = tween(PREVIEW_FADE_IN_MS),
        label = "previewAlpha"
    )

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { state ->
            streaming = state == PreviewView.StreamState.STREAMING
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }

    AndroidView(factory = { previewView }, modifier = modifier.alpha(previewAlpha))

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    // 绑定后才存在，故用状态保存：开关变化、以及重新绑定之后，都靠它下发一次
    var camera by remember { mutableStateOf<Camera?>(null) }
    val notifyTorchAvailability by rememberUpdatedState(onTorchAvailabilityChange)

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
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )
        notifyTorchAvailability(camera?.cameraInfo?.hasFlashUnit() == true)
    }

    // 无闪光灯的设备上 enableTorch 只会失败，先按可用性挡掉，与调用方显示按钮的判据同源
    LaunchedEffect(camera, torchOn) {
        val bound = camera ?: return@LaunchedEffect
        if (!bound.cameraInfo.hasFlashUnit()) return@LaunchedEffect
        bound.cameraControl.enableTorch(torchOn)
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

/** 居中正方形裁剪区域 */
internal data class SquareCrop(val left: Int, val top: Int, val side: Int)

/**
 * 算出居中的正方形裁剪区域。抽成纯函数是为了几何部分能直接单测，不必起相机或模拟器
 */
internal fun squareCrop(width: Int, height: Int): SquareCrop {
    val side = minOf(width, height)
    return SquareCrop(left = (width - side) / 2, top = (height - side) / 2, side = side)
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

    // 取景框是正方形、预览也是居中裁剪填满的，所以整帧同样裁成中间的正方形：
    // 看到什么就存什么、也就识别什么，三者画面一致
    val crop = squareCrop(bitmap.width, bitmap.height)
    if (crop.side != bitmap.width || crop.side != bitmap.height) {
        bitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.side, crop.side)
            .also { bitmap.recycle() }
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
