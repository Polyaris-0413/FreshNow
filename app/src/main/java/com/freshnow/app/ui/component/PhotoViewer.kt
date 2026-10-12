package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlin.math.min

/**
 * 看图：整张按比例铺开，双指放大 / 双击缩放 / 拖动查看，放大后拖不出屏幕；单击关闭。
 *
 * M3 没有「看图」组件。这里沿用本项目对全屏照片的一贯做法（与裁剪页同一套 transformable 手势、
 * 同一种「手势说明摆在底部」的版式），倍率上限取得更高——剪裁是构图，看图的用途之一是看清标签上
 * 的小字。
 *
 * 单击关闭是看图这类界面的习惯读法（微信、微博的看图页都是点一下退出）：一个不能点的全屏图会让人
 * 反复戳屏幕。三个手势不冲突——拖动与捏合都会被 transformable 吃掉，单击/双击落在没被吃掉的
 * 那一下上（见下面 pointerInput 与 transformable 的先后）。
 *
 * [imageModifier] 是给**照片本身**的修饰符：进场/退场的共享元素动画要挂在照片上，挂到外层盒子上的
 * 话，动的是那个透明盒子，照片会在开始时直接跳到最终大小。
 */
@Composable
fun PhotoViewer(
    image: Bitmap,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier
) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var viewport by remember(image) { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_VIEW_ZOOM)
        offset = clampViewerOffset(offset + panChange, image, viewport, zoom)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            // 全部语义由页面标题与下面的手势说明承担，这里再报一遍只是噪音；点击的动作在
            // 说明里写着，读屏软件也念得到
            contentDescription = null,
            modifier = imageModifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                // 点击要接在 transformable 之前：接在后面的话，那一串手势先拿到事件，
                // 单击就轮不到了
                .pointerInput(image) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        // 双击在图里来回：一倍时放到 [DOUBLE_TAP_ZOOM]，已放大则缩回一倍。
                        // 缩回时位移会跟着归零（见 clampViewerOffset），不会把图留在角落
                        onDoubleTap = { zoom = if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM }
                    )
                }
                .transformable(state = transformState)
                .graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
        Text(
            text = stringResource(R.string.record_image_view_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(FreshNowSpacing.sm)
        )
    }
}

/** 看图能放到的最大倍率。比裁剪页高，看图的一个用途是看清标签上的小字 */
private const val MAX_VIEW_ZOOM = 6f

/** 双击放大到的倍率：够看清一行小字，又不至于一眼看不出看的是哪儿 */
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * 把拖动位移夹在「图仍铺满视窗」的范围内。
 *
 * 倍数与 [ContentScale.Fit] 同一口径：按宽高比例取小的那个，所以未放大时两个方向的可动范围都是
 * 0——回到 1 倍时位移自然归零，不会把图拖到角落里留一片空白。
 */
private fun clampViewerOffset(next: Offset, image: Bitmap, viewport: IntSize, zoom: Float): Offset {
    if (viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
    val fit = min(
        viewport.width / image.width.toFloat(),
        viewport.height / image.height.toFloat()
    )
    val maxX = ((image.width * fit * zoom - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((image.height * fit * zoom - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
}
