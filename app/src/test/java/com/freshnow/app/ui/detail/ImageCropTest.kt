package com.freshnow.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 裁剪几何：视窗里看到的那块与真正裁下来的那块必须完全一致。
 *
 * 这类偏差在界面上看不出来（裁剪框本来就是透明的），一次算错只会表现为「裁出来的不是我框的」，
 * 所以这里逐条钉住：起点、上下限、两轴的差异。
 */
class ImageCropTest {

    /** 竖图的源图铺满正方形视窗，靠高度定倍率；宽度方向因此有可拖的余量 */
    @Test
    fun coverScale_usesTheFittingAxis() {
        assertEquals(1f, coverScale(imageWidth = 600, imageHeight = 600, viewportSide = 600f))
        assertEquals(2f, coverScale(imageWidth = 300, imageHeight = 300, viewportSide = 600f))
        assertEquals(1f, coverScale(imageWidth = 1200, imageHeight = 600, viewportSide = 600f))
    }

    @Test
    fun coverScale_invalidInput_fallsBackToIdentity() {
        assertEquals(1f, coverScale(imageWidth = 0, imageHeight = 0, viewportSide = 600f))
        assertEquals(1f, coverScale(imageWidth = 600, imageHeight = 600, viewportSide = 0f))
    }

    /** 恰好铺满的那一轴不该能拖：能拖就会在视窗里露出空白 */
    @Test
    fun maxPan_exactlyCoversIsZero() {
        assertEquals(0f, maxPan(imageExtent = 600, scale = 1f, viewportSide = 600f))
        // 放宽的那一轴：1200 的图放到 600 的视窗，单侧可拖 300
        assertEquals(300f, maxPan(imageExtent = 1200, scale = 1f, viewportSide = 600f))
    }

    /** 缩放后两轴都可拖，上限按缩放后的尺寸算 */
    @Test
    fun maxPan_scalesWithZoom() {
        assertEquals(300f, maxPan(imageExtent = 600, scale = 2f, viewportSide = 600f))
    }

    @Test
    fun clampPan_neverExceedsTheEdge() {
        assertEquals(300f, clampPan(offset = 9999f, imageExtent = 1200, scale = 1f, viewportSide = 600f))
        assertEquals(-300f, clampPan(offset = -9999f, imageExtent = 1200, scale = 1f, viewportSide = 600f))
        assertEquals(120f, clampPan(offset = 120f, imageExtent = 1200, scale = 1f, viewportSide = 600f))
    }

    /** 没拖动、没缩放时就是居中裁方：与相机那条路的 squareCrop 是同一块区域 */
    @Test
    fun cropWindow_noGestureTakesTheCenterSquare() {
        assertEquals(CropWindow(left = 100, top = 0, side = 600), window(imageWidth = 800, imageHeight = 600))
        assertEquals(CropWindow(left = 0, top = 100, side = 600), window(imageWidth = 600, imageHeight = 800))
        assertEquals(CropWindow(left = 0, top = 0, side = 600), window(imageWidth = 600, imageHeight = 600))
    }

    /** 往右拖 = 看左边的部分更多：裁剪框的起点要往左走，方向反了就成「拖哪边裁哪边」 */
    @Test
    fun cropWindow_pansTheOppositeWay() {
        val right = window(imageWidth = 1200, imageHeight = 600, offsetX = 300f)
        assertEquals(0, right.left)
        assertEquals(600, right.side)

        val left = window(imageWidth = 1200, imageHeight = 600, offsetX = -300f)
        assertEquals(600, left.left)
    }

    /** 放大后裁下来的那一块更小，位置跟着平移走 */
    @Test
    fun cropWindow_zoomShrinksTheWindow() {
        val zoomed = window(imageWidth = 600, imageHeight = 600, scale = 2f)
        assertEquals(300, zoomed.side)
        assertEquals(150, zoomed.left)
        assertEquals(150, zoomed.top)

        val panned = window(imageWidth = 600, imageHeight = 600, scale = 2f, offsetX = -150f)
        assertEquals(300, panned.side)
        // 图往左拖了 150px，倍率 2 时视窗在源图上右移 75px：600/2 的框从 225 开始
        assertEquals(225, panned.left)
    }

    /** 拖到边、放到最大都不能裁出图外：越界在界面上表现为「裁出来一块黑的」 */
    @Test
    fun cropWindow_extremesStayInsideTheImage() {
        val corner = window(
            imageWidth = 800,
            imageHeight = 600,
            scale = 2.4f,
            offsetX = 9999f,
            offsetY = 9999f
        )
        assertTrue(corner.left >= 0)
        assertTrue(corner.top >= 0)
        assertTrue(corner.left + corner.side <= 800)
        assertTrue(corner.top + corner.side <= 600)

        val other = window(
            imageWidth = 800,
            imageHeight = 600,
            scale = 2.4f,
            offsetX = -9999f,
            offsetY = -9999f
        )
        assertTrue(other.left >= 0)
        assertTrue(other.top >= 0)
        assertTrue(other.left + other.side <= 800)
        assertTrue(other.top + other.side <= 600)
    }

    /** 尺寸还没量出来（首帧视窗为 0）时给一块合法的空框，而不是 0 边长的图 */
    @Test
    fun cropWindow_unknownViewportFallsBackToWholeImage() {
        val window = window(imageWidth = 600, imageHeight = 0, viewportSide = 0f)

        assertEquals(1, window.side)
        assertEquals(0, window.left)
        assertEquals(0, window.top)
    }

    private fun window(
        imageWidth: Int,
        imageHeight: Int,
        viewportSide: Float = 600f,
        scale: Float = coverScale(imageWidth, imageHeight, viewportSide),
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) = cropWindow(imageWidth, imageHeight, viewportSide, scale, offsetX, offsetY)
}
