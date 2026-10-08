package com.freshnow.app.ui.detail

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 裁剪界面上的手势状态与源图裁剪框之间的换算。抽成纯函数是为了能直接单测——
 * 这段几何一旦偏了，用户摆好的框和真正裁下来的那块就对不上，而画面里看不出任何异常。
 *
 * 坐标一律是「源图像素」或「视窗像素」，不含 dp、也不含图片自身的 EXIF 之类：
 * 源图在 [com.freshnow.app.data.decodePickedImage] 里就已经摆正并降采样过了。
 */

/**
 * 放大倍数上限。自定值，不是 M3 的规格：记录照片的输出长边是 768，源图解码后长边不超过 2048，
 * 放到 4 倍时源图一侧切出来的边长已经接近输出的分辨率上限，再放只是把像素拉大、画面变糊。
 */
internal const val MAX_CROP_ZOOM = 4f

/** 视窗对应的源图裁剪框 */
internal data class CropWindow(val left: Int, val top: Int, val side: Int)

/**
 * 源图铺满正方形视窗所需的最小倍数（cover）。小于它就会在视窗里露出空白，
 * 与「看到什么就裁什么」冲突，所以它是缩放的起点而不是终点。
 */
internal fun coverScale(imageWidth: Int, imageHeight: Int, viewportSide: Float): Float {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportSide <= 0f) return 1f
    return max(viewportSide / imageWidth, viewportSide / imageHeight)
}

/**
 * 单轴允许的最大平移量：图被拖到哪一步才会在视窗里露出空白。
 * 每轴各算一次（正方形视窗配非正方形图时两轴不同），界面按它夹紧、裁剪按它反解，两者同源。
 */
internal fun maxPan(imageExtent: Int, scale: Float, viewportSide: Float): Float =
    max(0f, (imageExtent * scale - viewportSide) / 2f)

/** 把平移量夹进 [maxPan] 的范围里 */
internal fun clampPan(offset: Float, imageExtent: Int, scale: Float, viewportSide: Float): Float =
    offset.coerceIn(-maxPan(imageExtent, scale, viewportSide), maxPan(imageExtent, scale, viewportSide))

/**
 * 视窗里现在看到的那块源图区域。
 *
 * [offsetX]/[offsetY] 是图的平移量（正方向与屏幕一致），所以裁剪中心要往反方向走；
 * 结果夹进图内，保证裁出来的一定在图里。
 */
internal fun cropWindow(
    imageWidth: Int,
    imageHeight: Int,
    viewportSide: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): CropWindow {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportSide <= 0f || scale <= 0f) {
        return CropWindow(left = 0, top = 0, side = minOf(imageWidth, imageHeight).coerceAtLeast(1))
    }

    val side = (viewportSide / scale).roundToInt().coerceIn(1, minOf(imageWidth, imageHeight))
    val centerX = imageWidth / 2f - offsetX / scale
    val centerY = imageHeight / 2f - offsetY / scale

    return CropWindow(
        left = (centerX - side / 2f).roundToInt().coerceIn(0, imageWidth - side),
        top = (centerY - side / 2f).roundToInt().coerceIn(0, imageHeight - side),
        side = side
    )
}
