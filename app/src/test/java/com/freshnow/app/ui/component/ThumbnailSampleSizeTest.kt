package com.freshnow.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailSampleSizeTest {

    /** 存下来的原图长边 768px，48dp 的格子在 3x 屏上是 144px：降到 192px 就够，再降会糊 */
    @Test
    fun largeImage_isDownsampledUntilCloseToTarget() {
        assertEquals(4, thumbnailSampleSize(width = 768, height = 768, maxPixels = 144))
    }

    @Test
    fun landscape_usesLongestSide() {
        assertEquals(4, thumbnailSampleSize(width = 1024, height = 768, maxPixels = 144))
    }

    @Test
    fun imageSmallerThanTarget_isNotUpscaled() {
        assertEquals(1, thumbnailSampleSize(width = 96, height = 96, maxPixels = 144))
    }

    /** 文件读不出来时 outWidth/outHeight 为 -1，交给解码自己失败，不在这里做除零 */
    @Test
    fun invalidBounds_fallBackToFullSize() {
        assertEquals(1, thumbnailSampleSize(width = -1, height = -1, maxPixels = 144))
        assertEquals(1, thumbnailSampleSize(width = 768, height = 768, maxPixels = 0))
    }
}
