package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSize

/**
 * 记录里的扫描照片，详情页与编辑页共用（编辑页换照片时要当场看出换成了哪张）。
 *
 * 照片在采集或裁剪时就已按正方形落成（见 ScanImageCodec），这里按图片自身比例铺满宽度即可，
 * 不做二次裁剪——旧记录里可能存着更早期未裁方的图，同样按原比例显示。
 *
 * 没图时（保存时没有可用画面，或图片落盘、解码失败，以及用户刚移除了照片）用占位图标顶上去，
 * 高度固定得比照片矮一些，它只是占位、不假装是照片。
 */
@Composable
fun ScanPhoto(image: Bitmap?, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val photoModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)

    if (image == null) {
        Box(
            modifier = photoModifier.height(FreshNowSize.imagePlaceholderHeight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_no_image),
                contentDescription = stringResource(R.string.record_image_missing),
                modifier = Modifier.size(FreshNowSize.icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            contentDescription = stringResource(R.string.record_image),
            modifier = photoModifier,
            contentScale = ContentScale.FillWidth
        )
    }
}
