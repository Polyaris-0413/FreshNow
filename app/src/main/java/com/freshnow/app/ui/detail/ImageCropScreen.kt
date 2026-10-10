package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.SquareViewport
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlin.math.roundToInt

/**
 * 把相册选来的图裁成正方形。
 *
 * 记录照片的不变量是「正方形」，而相册里的图什么比例都有（见 ScanPhoto 的说明），所以这里
 * 不是可选的美化步骤，而是唯一能把相册图落到同一规格的地方。相机那条路不必经过这里——
 * 它的取景框本来就是方的。
 *
 * 摆出来的框与真正裁下来的那块由 [cropWindow] 一套公式换算：画面里看不出偏差，
 * 所以这段几何是单测的对象，不是「看着差不多就行」的东西。
 *
 * 只做平移与缩放，不做旋转：旋转要再定义一套坐标，而竖拍照片的方向在解码时已经按 EXIF 摆正。
 */
@Composable
internal fun ImageCropScreen(
    image: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (CropWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    // 视窗边长（像素）由布局给出，几何要用它，顶栏的「确定」也要用它
    var viewportSide by remember { mutableStateOf(0f) }
    var zoom by remember(image) { mutableStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }

    val scale = coverScale(image.width, image.height, viewportSide) * zoom
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (viewportSide <= 0f) return@rememberTransformableState
        // 缩放与平移一起变：只处理其中一个的话，双指缩放时手指的位移会被丢掉，图会跟手不同步
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_CROP_ZOOM)
        val nextScale = coverScale(image.width, image.height, viewportSide) * zoom
        offset = Offset(
            clampPan(offset.x + panChange.x, image.width, nextScale, viewportSide),
            clampPan(offset.y + panChange.y, image.height, nextScale, viewportSide)
        )
    }

    FreshNowSubPage(
        title = stringResource(R.string.photo_crop_title),
        onBack = onCancel,
        actions = {
            TextButton(
                // 视窗还没量出尺寸时裁剪几何没有意义（会算出一像素的框），先不给点
                enabled = viewportSide > 0f,
                onClick = {
                    onConfirm(
                        cropWindow(
                            imageWidth = image.width,
                            imageHeight = image.height,
                            viewportSide = viewportSide,
                            scale = scale,
                            offsetX = offset.x,
                            offsetY = offset.y
                        )
                    )
                }
            ) {
                Text(text = stringResource(R.string.action_confirm))
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(FreshNowSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
        ) {
            // 与拍照页同一套取景尺寸：按可用宽高里较小的一边取方
            SquareViewport(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSide = it.width.toFloat() }
                        .transformable(state = transformState)
                ) {
                    val imageBitmap = remember(image) { image.asImageBitmap() }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (scale <= 0f) return@Canvas
                        val drawnWidth = image.width * scale
                        val drawnHeight = image.height * scale
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(
                                ((size.width - drawnWidth) / 2f + offset.x).roundToInt(),
                                ((size.height - drawnHeight) / 2f + offset.y).roundToInt()
                            ),
                            dstSize = IntSize(
                                drawnWidth.roundToInt().coerceAtLeast(1),
                                drawnHeight.roundToInt().coerceAtLeast(1)
                            ),
                            // 源图可能比输出大得多，降采样取样更好看；默认的 Low 在缩小时会有锯齿
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
            }

            // 手势没有可见的把手，说明一句比让用户自己试出来省事
            Text(
                text = stringResource(R.string.photo_crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
