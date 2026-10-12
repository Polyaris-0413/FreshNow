package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlin.math.min

/**
 * 看图：整张按比例铺开，双指放大、拖动查看，放大后拖不出屏幕。
 *
 * M3 没有「看图」组件。这里沿用本项目对全屏照片的一贯做法（与裁剪页同一套 transformable 手势、
 * 同一种「手势说明摆在照片下方」的版式），放大倍率取一样的档位，不另造一套版式。
 *
 * 上限比裁剪页（[com.freshnow.app.ui.detail.MAX_CROP_ZOOM]）高：裁剪是构图，看图的用途之一是
 * 看清标签上的小字，标签上的日期往往只占画面很小一块。
 *
 * 不提供「点图关闭」：全屏内容的出口是顶欄返回键与系统返回，与裁剪页同一口径——那里点图也是
 * 拖动，不是关闭。
 */
@Composable
fun PhotoViewer(image: Bitmap, modifier: Modifier = Modifier) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        // 缩放与平移一起算：只处理其中一个的话，双指缩放时手指的位移会被丢掉，图会跟手不同步
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_VIEW_ZOOM)
        offset = clampViewerOffset(offset + panChange, image, viewport, zoom)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            // 全部语义由页面的标题与下面的手势说明承担，这里再报一遍只是噪音
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                // 手势要接在 graphicsLayer 之前：接在里面的话触摸区会跟着图一起缩放
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
