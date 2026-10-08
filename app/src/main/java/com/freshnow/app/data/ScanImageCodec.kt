package com.freshnow.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * 记录照片的统一规格：居中正方形的 JPEG，长边不超过 [MAX_IMAGE_DIMENSION]。
 *
 * 相机帧与相册选来的图都要落成同一规格，所以裁方、缩放、压缩、解码全在这一处。
 * 两条来源各写一份的话，比例与压缩质量稍有分叉，界面上看不出来——详情页却依赖
 * 「照片必是正方形」这条不变量（见 ScanPhoto 的说明），分叉会在那里变成显示不一致。
 */

/** 长边上限。768 是识别送图与详情页展示都够用的尺寸，再大只是白占磁盘与内存 */
internal const val MAX_IMAGE_DIMENSION = 768

/**
 * 相册原图解码后的长边上限。比 [MAX_IMAGE_DIMENSION] 大一档是为了给裁剪留余量：
 * 用户可能要往里推近，取一档比输出更大的源图才有得裁；不设上限则一张 8000px 的原图
 * 解码就是 200MB 量级的内存
 */
internal const val CROP_SOURCE_MAX_DIMENSION = 2048

private const val JPEG_QUALITY = 80

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
 * 居中裁方 → 长边缩到上限 → JPEG。失败（编码器拿不到内存等）返回 null，调用方按「没有照片」处理。
 *
 * 不回收 `this`：传入的位图归调用方，本函数造出来的中间位图自己回收。这条约定写在这里，
 * 是因为两种所有权混在一处最容易变成「谁 recycle 都说不清」，而用错的表现是随机的崩溃。
 */
internal fun Bitmap.toSquareJpegBytes(): ByteArray? {
    val crop = squareCrop(width, height)
    var square = this
    if (crop.side != width || crop.side != height) {
        square = Bitmap.createBitmap(this, crop.left, crop.top, crop.side, crop.side)
    }

    var encoded = square
    val longSide = max(square.width, square.height)
    if (longSide > MAX_IMAGE_DIMENSION) {
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / longSide
        encoded = Bitmap.createScaledBitmap(
            square,
            (square.width * ratio).toInt().coerceAtLeast(1),
            (square.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    val jpeg = runCatching {
        ByteArrayOutputStream().use { out ->
            encoded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }.getOrNull()

    if (encoded !== square) encoded.recycle()
    if (square !== this) square.recycle()
    return jpeg
}

/**
 * 读记录里存下的照片。文件不在、解码失败都返回 null——界面用占位图代替，
 * 与「保存时就没有可用画面」是同一种呈现。
 */
internal fun decodeScanImage(file: File?): Bitmap? =
    file?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }

/**
 * 读相册选来的图，按 [CROP_SOURCE_MAX_DIMENSION] 降采样并摆正方向。
 *
 * 方向必须自己摆：BitmapFactory 不看 EXIF，竖拍的照片在文件里是横着的像素加一个旋转标记，
 * 直接解码出来会躺在裁剪框里。方向读不出来时按不旋转处理——认不出方向总比猜错方向好。
 *
 * 读不出内容（文件已被删、格式不支持）返回 null，由调用方告知用户。
 */
internal fun decodePickedImage(resolver: ContentResolver, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeWithin(bounds.outWidth, bounds.outHeight, CROP_SOURCE_MAX_DIMENSION)
    }
    val decoded = runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull() ?: return null

    return decoded.rotateByExif(resolver, uri)
}

/**
 * 降采样倍数：保证长边**不超过** [maxLongSide]。inSampleSize 只接受 2 的幂，故只能一路翻倍，
 * 结果是长边落在 (maxLongSide/2, maxLongSide] 之间。
 *
 * 与缩略图那套（thumbnailSampleSize）不同：那里的目标是「降到接近目标尺寸就够」，
 * 允许结果比目标大一档；这里是要卡住内存上限，只能比上限小。
 */
internal fun sampleSizeWithin(width: Int, height: Int, maxLongSide: Int): Int {
    if (width <= 0 || height <= 0 || maxLongSide <= 0) return 1
    var sample = 1
    while (max(width, height) / sample > maxLongSide) sample *= 2
    return sample
}

private fun Bitmap.rotateByExif(resolver: ContentResolver, uri: Uri): Bitmap {
    val degrees = runCatching {
        resolver.openInputStream(uri)?.use { exifRotationDegrees(it) } ?: 0
    }.getOrNull() ?: 0
    if (degrees == 0) return this

    return Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postRotate(degrees.toFloat()) },
        true
    ).also { recycle() }
}

/**
 * 方向标记对应的顺时针旋转角。
 *
 * 用平台版 ExifInterface：它自 API 24 起与 androidx 版本是同一套实现，够读 JPEG 的方向，
 * 不必为一个属性引一个库。镜像翻转（FLIP_*）不处理：相册里的图极少是镜像的，
 * 真要按镜像还原得再定义一套坐标，而它错了也只是一张照片方向不对，不会错位到别处。
 */
private fun exifRotationDegrees(stream: InputStream): Int = when (
    ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}
