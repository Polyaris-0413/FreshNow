package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 相册原图的解码倍数。它是内存上限的唯一一道闸：算小了会把几千像素的原图整张解进内存，
 * 算大了会把用户要裁的细节提前丢掉（而裁剪是往上推近的，丢了就补不回来）。
 */
class SampleSizeWithinTest {

    /** 恰好到上限时不再降：inSampleSize 只能翻倍，多降一档就成了上限的一半 */
    @Test
    fun exactlyAtTheCap_isNotDownsampled() {
        assertEquals(1, sampleSizeWithin(width = 2048, height = 1536, maxLongSide = 2048))
    }

    /** 刚超一点也只降一档，结果落在 (上限/2, 上限] 之间 */
    @Test
    fun slightlyOverTheCap_doublesOnce() {
        assertEquals(2, sampleSizeWithin(width = 3000, height = 2000, maxLongSide = 2048))
    }

    /** 手机原图量级：一路翻倍到长边落回上限之内 */
    @Test
    fun hugeImage_isDownsampledUntilItFits() {
        assertEquals(4, sampleSizeWithin(width = 8000, height = 6000, maxLongSide = 2048))
    }

    /** 视口按长边算，不看面积 */
    @Test
    fun landscape_usesLongestSide() {
        assertEquals(2, sampleSizeWithin(width = 4096, height = 64, maxLongSide = 2048))
    }

    /** 尺寸非法（文件读不出来时 outWidth 为 -1）时不做除法，交给解码自身去失败 */
    @Test
    fun invalidBounds_fallBackToFullSize() {
        assertEquals(1, sampleSizeWithin(width = -1, height = -1, maxLongSide = 2048))
        assertEquals(1, sampleSizeWithin(width = 0, height = 0, maxLongSide = 2048))
        assertEquals(1, sampleSizeWithin(width = 8000, height = 6000, maxLongSide = 0))
    }
}
