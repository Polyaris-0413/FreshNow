package com.freshnow.app.ui.component

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.IntSize
import com.freshnow.app.ui.theme.FreshNowTransitions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

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
 */
@Composable
fun PhotoViewer(
    image: Bitmap,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
        var zoom by remember(image) { mutableFloatStateOf(1f) }
        var offset by remember(image) { mutableStateOf(Offset.Zero) }
        // 下滑退出时图跟手的位移（像素，向下为正）
        var dragDown by remember(image) { mutableFloatStateOf(0f) }
        // 双击与下滑回弹的动画：捏合要能随时打断，所以留个句柄
        var gestureAnimation by remember(image) { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()

        val photoSize = photoSize(image, viewport)
        val density = LocalDensity.current
        // 下滑多少像素算「要退出」：按视窗高度取比例，不写死 dp（换机型、分屏都不用改）
        val dismissThreshold = viewport.height * DISMISS_THRESHOLD_RATIO
        // 跟手时图缩小、淡出多少
        val dragProgress = (dragDown / dismissThreshold).coerceIn(0f, 1f)

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            // 手指一动就接管：正在演的动画再往下演，就会和手指抢缩放值
            gestureAnimation?.cancel()
            zoom = (zoom * zoomChange).coerceIn(1f, MAX_VIEW_ZOOM)
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
                    animationSpec = FreshNowTransitions.stateChange()
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

        /** 没到阈值就把图弹回原位：位移、缩放、透明一起回，免得各演各的 */
        fun animateDragBack() {
            gestureAnimation?.cancel()
            if (dragDown == 0f) return
            gestureAnimation = scope.launch {
                animate(
                    initialValue = dragDown,
                    targetValue = 0f,
                    animationSpec = FreshNowTransitions.stateChange()
                ) { value, _ -> dragDown = value }
            }
        }

        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            // 语义由页面标题承担，这里再报一遍是噪音；单击关闭在看图这类界面里是共识读法
            contentDescription = null,
            modifier = imageModifier
                .size(
                    width = with(density) { photoSize.width.toDp() },
                    height = with(density) { photoSize.height.toDp() }
                )
                // 缩放要接在蒙版之前：接在之后的话，图是在「蒙版出来的那块矩形」里缩放，
                // 放大后只是那块矩形里换了内容，看上去没变大（测试里就撞上了这个）
                .graphicsLayer(
                    scaleX = zoom * (1f - DRAG_SHRINK * dragProgress),
                    scaleY = zoom * (1f - DRAG_SHRINK * dragProgress),
                    translationX = offset.x,
                    translationY = offset.y + dragDown,
                    alpha = 1f - dragProgress
                )
                .clip(MaterialTheme.shapes.medium),
            // 节点已经是照片的比例，Fit 与 FillBounds 在这里等价
            contentScale = ContentScale.Fit
        )

        // 手势都挂在外层盒子上：指哪都能捏、都能点
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 下滑退出。用 Initial 阶段旁听、不消费事件：一旦消费掉，与它同时开始的捏合与拖动
                // 就会被抢走（transformable 再也拿不到事件）。只在未放大时生效：放大后纵向拖动是看图，不是退出
                .pointerInput(image) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var drag = 0f
                        var multiTouch = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            // 出现第二根手指就当成捏合，这一次手势不算下滑
                            if (pressed > 1) multiTouch = true
                            if (pressed == 0) break
                            if (!multiTouch && zoom <= 1f) {
                                val change = event.changes.first()
                                drag += change.position.y - change.previousPosition.y
                                dragDown = drag.coerceAtLeast(0f)
                            }
                        }
                        if (!multiTouch && zoom <= 1f && drag > dismissThreshold) {
                            // 这一页马上就没了，图也已经淡到几乎看不见：回弹动画不必跑，
                            // 交给退场的共享元素过渡接着演
                            onDismiss()
                        } else {
                            animateDragBack()
                        }
                    }
                }
                // 点击接在 transformable 之前：接在后面的话那一串手势先拿到事件，单击就轮不到了
                .pointerInput(image) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = { tap -> animateZoomTo(if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM, tap) }
                    )
                }
                .transformable(state = transformState)
        )
    }
}

/** 看图能放到的最大倍率。比裁剪页高，看图的一个用途是看清标签上的小字 */
private const val MAX_VIEW_ZOOM = 6f

/** 双击放大到的倍率：够看清一行小字，又不至于一眼看不出看的是哪儿 */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** 下滑多少（占视窗高度的比例）算「要退出」 */
private const val DISMISS_THRESHOLD_RATIO = 0.18f

/** 下滑到底时图缩到多小、淡到多透（比例） */
private const val DRAG_SHRINK = 0.15f

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
