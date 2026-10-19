package com.freshnow.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freshnow.app.R
import com.freshnow.app.data.toSquareJpegBytes
import com.freshnow.app.ui.theme.FreshNowCameraOverlay
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowTransitions
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * 取景框的宽高比。必须是 1f：本组件会把送给 AI 的整帧裁成居中正方形，
 * 一旦调用方的取景框不是方的，「看到什么就裁什么」就会静默失效——
 * 用户看到整幅画面，模型只收到中间一块。相机页与拍照页共用它，取值不会分叉。
 */
internal const val CAMERA_ASPECT_RATIO = 1f

/**
 * 分析流的取帧尺寸。CameraX 的默认值是 640×480，而这条流同时是送 AI 的图与落盘照片的来源：
 * 裁成正方形后只剩 480×480，详情页铺满屏幕时是硬放大，字边全是糊的。
 *
 * 要 4:3 是因为传感器原生比例就是它，正方形裁切因此与预览看到的一致；尺寸由设备的相机
 * 自己给，给不了 1280×960 就按 CameraX 的兜底规则退到最接近的（见下）。
 */
private val ANALYSIS_RESOLUTION = Size(1280, 960)

/**
 * 实时预览 + 取帧回调。取帧前先问 [canAcceptFrame]，避免在请求进行或冷却期内白做一次 JPEG 编码
 *
 * 点一下画面就在那一点合焦并测光：本应用没有变焦，凑近拍包装上的小字时全靠这一下把焦点
 * 从前后背景上拉回到字上（不点就一直是系统的连续对焦，它未必选得中有字的那一块）。
 *
 * [torchOn] 由调用方持有：相机实例每次重新绑定（Activity 重建就会重新绑定）都会回到关灯状态，
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
        animationSpec = FreshNowTransitions.previewFadeIn(),
        label = "previewAlpha"
    )

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { state ->
            streaming = state == PreviewView.StreamState.STREAMING
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }

    // 绑定后才存在，故用状态保存：开关变化、以及重新绑定之后，都靠它下发一次
    var camera by remember { mutableStateOf<Camera?>(null) }
    // 对焦落点与它的序号：序号每点一次加一，取景圈靠它从头演一遭
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    val notifyTorchAvailability by rememberUpdatedState(onTorchAvailabilityChange)

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .alpha(previewAlpha)
                .pointerInput(camera) {
                    detectTapGestures { point ->
                        val bound = camera ?: return@detectTapGestures
                        // 相机已解绑或正在切换时 startFocusAndMetering 会抛，而这一次点击
                        // 落空只意味着没对上焦，不该把整个取景框带崩
                        runCatching {
                            bound.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    previewView.meteringPointFactory.createPoint(point.x, point.y),
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                ).build()
                            )
                        }
                        focusPoint = point
                        focusRequest++
                    }
                }
        )
        focusPoint?.let { point ->
            FocusMark(point = point, request = focusRequest) { focusPoint = null }
        }
    }

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
            // 不显式要尺寸时 CameraX 给的是 VGA，这条流因此一直是 480 见方的图。
            // 4:3 优先、要不到就换别的比例（RATIO_4_3_FALLBACK_AUTO）：只支持 16:9 的设备
            // 也拿得到最接近的尺寸，而不是绑定失败——正方形裁切与比例无关，画面依然一致
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            ANALYSIS_RESOLUTION,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
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

/**
 * 点按对焦的落点回执：图形从大一圈收到位、停一拍、淡出。
 *
 * 图形是 Material Symbols 的 filter_center_focus（[R.drawable.ic_focus]）：四个角加中心一点。
 * 中心点比手画的四角多了一个信息，但它本来就是「对焦标记」这一语义的现成图形。
 *
 * 坐标直接用取景框内的像素：预览铺满本组件的这个 Box，所以这里的坐标与 [PreviewView]
 * 的坐标系是同一套，既能画图形也能拿来算测光点。
 */
@Composable
private fun FocusMark(point: Offset, request: Int, onFinished: () -> Unit) {
    val side = with(LocalDensity.current) { FreshNowSize.focusBracket.toPx() }
    val scale = remember(request) { Animatable(FreshNowTransitions.FOCUS_MARK_START_SCALE) }
    val alpha = remember(request) { Animatable(1f) }
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(request) {
        scale.animateTo(1f, FreshNowTransitions.fastSpatial())
        delay(FreshNowTransitions.FOCUS_MARK_HOLD_MS)
        alpha.animateTo(0f, FreshNowTransitions.stateChange())
        finish()
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (point.x - side / 2f).roundToInt(),
                    (point.y - side / 2f).roundToInt()
                )
            }
            .size(FreshNowSize.focusBracket)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
    ) {
        val painter = painterResource(R.drawable.ic_focus)
        // 图标是实心填充，不能像线那样在外面再描一圈；把整枚图形复制一份放大当衬边，
        // 露出来的那圈边就是它在白墙、白包装上的可见性
        Icon(
            painter = painter,
            contentDescription = null,
            tint = FreshNowCameraOverlay.indicatorUnderlay,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = FreshNowSize.focusMarkUnderlayScale
                    scaleY = FreshNowSize.focusMarkUnderlayScale
                }
        )
        Icon(
            painter = painter,
            contentDescription = null,
            tint = FreshNowCameraOverlay.indicator,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 取一帧、摆正、裁成正方形 JPEG。
 *
 * 转位图交给 [ImageProxy.toBitmap]（CameraX 自带的转换，走的是 native 的 YUV→RGB），
 * 而不是自己逐像素搬：手写的循环在 VGA 下勉强够用，到 1280×960 就是每帧一百多万像素，
 * 分析线程扛不住。它也顺带省掉了 CameraX 为 RGBA 输出多插的那道转换。
 */
private fun ImageProxy.toUprightJpeg(): ByteArray? {
    val bitmap = runCatching { toBitmap() }.getOrNull() ?: return null
    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation == 0) {
        bitmap
    } else {
        Bitmap.createBitmap(
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
    // 看到什么就存什么、也就识别什么，三者画面一致。裁方、缩放、压缩与相册那条路共用
    // ScanImageCodec，规格只有一份
    return upright.toSquareJpegBytes().also { upright.recycle() }
}
