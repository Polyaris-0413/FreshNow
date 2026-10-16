package com.freshnow.app.ui.component

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.freshnow.app.ui.theme.FreshNowTransitions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 看图：双指放大 / 双击缩放 / 拖动查看 / 单击关闭，放大后拖不出屏幕。
 *
 * M3 没有「看图」组件。这里沿用本项目对全屏照片的一贯做法（与裁剪页同一套 transformable 手势），
 * 倍率上限取得更高——剪裁是构图，看图的用途之一是看清标签上的小字。
 *
 * 照片节点的尺寸就是它按 [ContentScale.Fit] 铺进视窗后的那块矩形，不是整个视窗，蒙版（圆角）也就
 * 落在照片上：
 * - 进场的共享元素过渡动的正是这块矩形，两边比例一致，照片不会被拉成视窗的比例；
 * - 尺寸取的是 `BoxWithConstraints` 给的约束而不是 `onSizeChanged` 量出来的尺寸——后者要等一帧
 *   才知道，那一帧里照片是 0×0，过渡会先缩到看不见再弹回来。
 *
 * 手势挂在外层盒子上而不是照片上：整屏指哪都能捏、都能点，不必对准照片。
 *
 * 双击朝**点到的那一点**放大：按住的那点全程不动，放大后看的就是你指的地方。缩回一倍时位移跟着
 * 归零（见 [clampViewerOffset]），不会把图留在角落。
 *
 * 多张时左右滑翻页（[startIndex] 决定从哪一张进来）；此时水平方向的拖动归翻页，退出只看垂直位移
 * ——一次左右滑不该既翻页又退出。
 */
@Composable
fun PhotoViewer(
    images: List<Bitmap>,
    startIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: @Composable (Int) -> Modifier = { Modifier }
) {
    if (images.isEmpty()) return
    val pageCount = images.size
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(images.indices),
        pageCount = { pageCount }
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
        // 当前这一页的图：缩放、位移、退出手势都围着它转。翻页时它一变，下面那些
        // remember(image) 就跟着重置——翻到下一张本来就该回到原样
        val image = images[pagerState.currentPage]
        var zoom by remember(image) { mutableFloatStateOf(1f) }
        var offset by remember(image) { mutableStateOf(Offset.Zero) }
        // 退出时图跟手的位移（像素，两轴都算）
        var dragOffset by remember(image) { mutableStateOf(Offset.Zero) }
        // 一旦触发退出，这一页就在退场了：这时再来手势只会把「跟手位移」在退场动画中途改掉，
        // 共享元素记住的是旧矩形、内容却已经挪走，图就留在屏幕角落（复现：滑走后 200~300ms 内
        // 再点一下）。所以退场一开始就关掉这一页的手势，并让回弹别再跑
        var dismissing by remember(image) { mutableStateOf(false) }
        // 双击与回弹的动画：捏合要能随时打断，所以留个句柄
        var gestureAnimation by remember(image) { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()

        val photoSize = photoSize(image, viewport)
        val density = LocalDensity.current
        // 拖多远算「要退出」：取视窗短边的比例，不写死 dp（换机型、分屏都不用改）；
        // 用位移长度而不是只看纵向，所以四个方向一个标准
        val dismissThreshold =
            min(viewport.width, viewport.height) * FreshNowTransitions.VIEW_DISMISS_THRESHOLD_RATIO

        // 复现「图卡在屏幕边缘」时要看的是退出那一刻的状态，所以进出这一页都打一份快照
        DisposableEffect(Unit) {
            Log.d(TAG, "看图页进入 viewport=${viewport.width}x${viewport.height} photo=${photoSize.width.toInt()}x${photoSize.height.toInt()}")
            onDispose {
                Log.d(TAG, "看图页离开 zoom=${zoom} pan=(${offset.x.toInt()},${offset.y.toInt()}) drag=(${dragOffset.x.toInt()},${dragOffset.y.toInt()})")
            }
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            // 手指一动就接管：正在演的动画再往下演，就会和手指抢缩放值
            gestureAnimation?.cancel()
            zoom = (zoom * zoomChange).coerceIn(1f, FreshNowTransitions.VIEW_MAX_ZOOM)
            offset = clampViewerOffset(offset + panChange, image, viewport, zoom)
        }

        fun animateZoomTo(target: Float, tap: Offset) {
            gestureAnimation?.cancel()
            val focus = tap - Offset(photoSize.width / 2f, photoSize.height / 2f)
            gestureAnimation = scope.launch {
                val startZoom = zoom
                val startOffset = offset
                animate(
                    initialValue = startZoom,
                    targetValue = target,
                    animationSpec = FreshNowTransitions.fastSpatial()
                ) { value, _ ->
                    zoom = value
                    // 同一点在两套缩放下的屏幕位置相同 → 位移这么反推
                    offset = clampViewerOffset(
                        startOffset + focus * (startZoom - value),
                        image,
                        viewport,
                        value
                    )
                }
            }
        }

        /** 唯一的出口：先关手势再交给调用方 */
        fun dismiss() {
            if (dismissing) return
            dismissing = true
            onDismiss()
        }

        /** 没到阈值就把图弹回原位 */
        fun animateDragBack() {
            gestureAnimation?.cancel()
            if (dismissing || dragOffset == Offset.Zero) return
            Log.d(TAG, "回弹开始 drag=(${dragOffset.x.toInt()},${dragOffset.y.toInt()})")
            gestureAnimation = scope.launch {
                val start = dragOffset
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = FreshNowTransitions.fastSpatial()
                ) { value, _ -> dragOffset = start * (1f - value) }
                Log.d(TAG, "回弹结束 drag=(${dragOffset.x.toInt()},${dragOffset.y.toInt()})")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 只有一张时不让它吃手势：那样「水平拖动退出」就再没机会触发（见上面的退出判据）
            // 不让它自己吃手势：捏合、下滑退出、水平翻页统一由下面那层盒子处理（见那里的说明）。
            // 两层都吃的话会互相抢，谁都拿不准
            userScrollEnabled = false
        ) { page ->
            val current = page == pagerState.currentPage
            val pageSize = photoSize(images[page], viewport)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = remember(images[page]) { images[page].asImageBitmap() },
                    // 语义由页面标题承担，这里再报一遍是噪音；单击关闭在看图这类界面里是共识读法
                    contentDescription = null,
                    modifier = Modifier
                        // 跟手用**布局**位移，不用 graphicsLayer 的位移：退场的共享元素过渡以这块矩形的
                        // 布局位置为起点，拖到哪儿就从哪儿飞回去；用图层位移的话，松手那一刻图会先弹回正中。
                        // 位移只属于当前这一页：别的页正在翻页动画里，跟着挪会看出重影
                        .offset {
                            if (current) {
                                IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                            } else {
                                IntOffset.Zero
                            }
                        }
                        .then(imageModifier(page))
                        .size(
                            width = with(density) { pageSize.width.toDp() },
                            height = with(density) { pageSize.height.toDp() }
                        )
                        // 缩放要接在蒙版之前：接在之后的话，图是在「蒙版出来的那块矩形」里缩放，
                        // 放大后只是那块矩形里换了内容，看上去没变大（测试里就撞上了这个）
                        .graphicsLayer(
                            scaleX = if (current) zoom else 1f,
                            scaleY = if (current) zoom else 1f,
                            translationX = if (current) offset.x else 0f,
                            translationY = if (current) offset.y else 0f
                        )
                        .clip(MaterialTheme.shapes.medium),
                    // 节点已经是照片的比例，Fit 与 FillBounds 在这里等价
                    contentScale = ContentScale.Fit
                )
            }
        }

        // 手势都挂在外层盒子上：指哪都能捏、都能点
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 拖拽退出。用 Initial 阶段旁听、不消费事件：一旦消费掉，与它同时开始的捏合与拖动
                // 就会被抢走（transformable 再也拿不到事件）。四个方向都能拖，标准只有一条：位移
                // 超过阈值；只在未放大时生效——放大后拖动是看图，不是退出
                .pointerInput(image) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (dismissing) return@awaitEachGesture
                        Log.d(TAG, "手势开始 zoom=$zoom pan=(${offset.x.toInt()},${offset.y.toInt()}) drag=(${dragOffset.x.toInt()},${dragOffset.y.toInt()})")
                        var drag = Offset.Zero
                        var multiTouch = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            // 出现第二根手指就当成捏合，这一次手势不算退出
                            if (pressed > 1) multiTouch = true
                            if (pressed == 0) break
                            if (!multiTouch && zoom <= 1f) {
                                val change = event.changes.first()
                                drag += change.position - change.previousPosition
                                dragOffset = drag
                            }
                        }
                        val distance = drag.getDistance()
                        // 多图时水平方向归翻页，退出只看垂直位移：否则一次左右滑会既翻页又退出
                        val dismissDistance = if (pageCount > 1) abs(drag.y) else distance
                        val turnPage = pageCount > 1 && !multiTouch && zoom <= 1f &&
                            abs(drag.x) > dismissThreshold
                        Log.d(
                            TAG,
                            "手势结束 多指=$multiTouch zoom=$zoom 拖动=(${drag.x.toInt()},${drag.y.toInt()}) " +
                                "距离=${dismissDistance.toInt()} 阈值=${dismissThreshold.toInt()} pan=(${offset.x.toInt()},${offset.y.toInt()})"
                        )
                        when {
                            turnPage -> {
                                // 水平拖够了就翻一张。翻页也归这一层：pager 自己不吃手势，
                                // 两层都吃的话会互相抢，谁都拿不准
                                val target = (pagerState.currentPage + if (drag.x < 0) 1 else -1)
                                    .coerceIn(0, pageCount - 1)
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }

                            !multiTouch && zoom <= 1f && dismissDistance > dismissThreshold -> {
                                // 这一页马上就没了，回弹动画不必跑：退场的共享元素过渡接着演，
                                // 图就停在跟手的位置，不会先弹回正中
                                Log.d(TAG, "退出：拖拽 拖动=(${drag.x.toInt()},${drag.y.toInt()})")
                                dismiss()
                            }

                            else -> animateDragBack()
                        }
                    }
                }
                // 点击接在 transformable 之前：接在后面的话那一串手势先拿到事件，单击就轮不到了
                .pointerInput(image) {
                    detectTapGestures(
                        onTap = {
                            Log.d(TAG, "退出：单击 zoom=$zoom pan=(${offset.x.toInt()},${offset.y.toInt()}) drag=(${dragOffset.x.toInt()},${dragOffset.y.toInt()})")
                            dismiss()
                        },
                        onDoubleTap = { tap ->
                            if (dismissing) return@detectTapGestures
                            Log.d(TAG, "双击 落点=(${tap.x.toInt()},${tap.y.toInt()}) zoom=$zoom")
                            animateZoomTo(if (zoom > 1f) 1f else FreshNowTransitions.VIEW_DOUBLE_TAP_ZOOM, tap)
                        }
                    )
                }
                .transformable(state = transformState, enabled = !dismissing)
        )
    }
}

/** 日志标签：复现手势异常时用 adb logcat -s PhotoViewer 抓 */
private const val TAG = "PhotoViewer"

// 下面三个是手势参数：M3 里没有「看图」这个组件（也就没有双击倍率、下拉退出距离的档位），
// 社区也没有公认值——把这三个数换成别的照样能跑，它们既不对应规范、也不对应行业惯例。
// 各自的取法见注释。

/** 看图能放到的最大倍率。比裁剪页高，看图的一个用途是看清标签上的小字 */




/** 照片按 [ContentScale.Fit] 铺进视窗后的尺寸。视窗还没量出来时为 [Size.Zero] */
private fun photoSize(image: Bitmap, viewport: IntSize): Size {
    if (viewport.width <= 0 || viewport.height <= 0) return Size.Zero
    val fit = min(
        viewport.width / image.width.toFloat(),
        viewport.height / image.height.toFloat()
    )
    return Size(image.width * fit, image.height * fit)
}

/**
 * 把拖动位移夹在「图仍铺满视窗」的范围内。
 *
 * 口径与 [ContentScale.Fit] 一致，所以未放大时两个方向的可动范围都是 0——回到 1 倍时位移自然
 * 归零，不会把图拖到角落里留一片空白。
 */
private fun clampViewerOffset(next: Offset, image: Bitmap, viewport: IntSize, zoom: Float): Offset {
    val photo = photoSize(image, viewport)
    if (photo == Size.Zero) return Offset.Zero
    val maxX = ((photo.width * zoom - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((photo.height * zoom - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
}
