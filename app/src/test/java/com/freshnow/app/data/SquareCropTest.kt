package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SquareCropTest {

    @Test
    fun portrait_takesFullWidthAndCentersVertically() {
        val crop = squareCrop(width = 600, height = 800)

        assertEquals(600, crop.side)
        assertEquals(0, crop.left)
        assertEquals(100, crop.top)
    }

    @Test
    fun landscape_takesFullHeightAndCentersHorizontally() {
        val crop = squareCrop(width = 800, height = 600)

        assertEquals(600, crop.side)
        assertEquals(100, crop.left)
        assertEquals(0, crop.top)
    }

    @Test
    fun alreadySquare_keepsWholeFrame() {
        val crop = squareCrop(width = 640, height = 640)

        assertEquals(640, crop.side)
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
    }

    /** 边长为奇数时余数落在另一侧，裁剪区域必须仍在帧内 */
    @Test
    fun oddDifference_staysInsideFrame() {
        val crop = squareCrop(width = 601, height = 800)

        assertEquals(601, crop.side)
        assertEquals(0, crop.left)
        assertEquals(99, crop.top)
        assertEquals(true, crop.top + crop.side <= 800)
    }
}
