package com.freshnow.app.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * 列表里的记录缩略图，正方形。
 *
 * 存下来的原图长边最大 768px，为 48dp 的格子整图解码进内存太浪费，这里按目标尺寸降采样，
 * 解码结果进共享缓存，滚动时不必反复读盘。没图时（保存时没有可用画面，或文件已不存在）
 * 用占位图标顶上去，与详情页的处理一致。
 */
@Composable
fun ScanThumbnail(image: File?, modifier: Modifier = Modifier) {
    val size = FreshNowSize.thumbnail
    val maxPixels = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = rememberThumbnail(image, maxPixels)
    val frame = modifier
        .size(size)
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)

    if (bitmap == null) {
        Box(modifier = frame, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_no_image),
                contentDescription = stringResource(R.string.record_image_missing),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = stringResource(R.string.record_image),
            modifier = frame,
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * 解码结果按文件名共享，容量取可用堆的 1/8（LruCache 的常规取法）。
 * 淘汰时不做 recycle：位图可能还被正在显示的 Image 引用着。
 */
private val thumbnailCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / CACHE_HEAP_DIVISOR).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

@Composable
private fun rememberThumbnail(image: File?, maxPixels: Int): Bitmap? {
    val name = image?.name
    return produceState(initialValue = name?.let(thumbnailCache::get), key1 = image) {
        if (image == null) {
            value = null
            return@produceState
        }
        val cached = thumbnailCache.get(image.name)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) { decodeThumbnail(image, maxPixels) }
        if (decoded != null) thumbnailCache.put(image.name, decoded)
        value = decoded
    }.value
}

private fun decodeThumbnail(file: File, maxPixels: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = thumbnailSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
        }
    )
}.getOrNull()

/**
 * inSampleSize 只接受 2 的幂，取长边降到 [maxPixels] 以内的最小值——降不到时保持原尺寸，不做放大。
 * 尺寸非法（文件读不出来时 outWidth 为 -1）时返回 1，让解码自身去失败。
 */
internal fun thumbnailSampleSize(width: Int, height: Int, maxPixels: Int): Int {
    if (width <= 0 || height <= 0 || maxPixels <= 0) return 1
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxPixels) sample *= 2
    return sample
}

private const val CACHE_HEAP_DIVISOR = 8
