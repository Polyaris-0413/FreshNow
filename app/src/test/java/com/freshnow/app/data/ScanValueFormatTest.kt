package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanValueFormatTest {

    @Test
    fun date_alreadyIso_isUnchanged() {
        assertEquals("2026-10-06", ScanValueFormat.date("2026-10-06"))
    }

    /** 标签上印的是中文年月日，模型有时照抄，这里统一回 ISO */
    @Test
    fun date_withChineseUnits_isNormalizedAndPadded() {
        assertEquals("2026-10-06", ScanValueFormat.date("2026年10月06日"))
        assertEquals("2026-10-06", ScanValueFormat.date("2026年10月6日"))
    }

    @Test
    fun date_withSlashesAndSingleDigits_isPadded() {
        assertEquals("2026-10-06", ScanValueFormat.date("2026/10/6"))
        // 模型照抄标签简写时会出现这种不补零的写法
        assertEquals("2026-10-06", ScanValueFormat.date("2026-10-6"))
        assertEquals("2026-01-06", ScanValueFormat.date("2026-1-6"))
    }

    @Test
    fun date_compact_isNormalized() {
        assertEquals("2026-10-06", ScanValueFormat.date("20261006"))
    }

    @Test
    fun date_withSurroundingText_keepsOnlyDate() {
        assertEquals("2026-10-06", ScanValueFormat.date("生产日期 2026-10-06"))
    }

    /** 连数字都用中文写的日期，标签上确实存在 */
    @Test
    fun date_writtenInChineseNumerals_isNormalized() {
        assertEquals("2026-10-06", ScanValueFormat.date("二〇二六年十月六日"))
        assertEquals("2026-12-24", ScanValueFormat.date("二〇二六年十二月二十四日"))
    }

    @Test
    fun date_unparseable_isKeptAsIs() {
        assertEquals("见包装喷码", ScanValueFormat.date("见包装喷码"))
    }

    @Test
    fun blankDate_staysBlank() {
        assertEquals("", ScanValueFormat.date(""))
    }

    @Test
    fun shelfLife_withSpace_isNormalized() {
        assertEquals("18个月", ScanValueFormat.shelfLife("18 个月"))
    }

    @Test
    fun shelfLife_singleMonthChar_becomesMonths() {
        assertEquals("2个月", ScanValueFormat.shelfLife("2月"))
    }

    @Test
    fun shelfLife_dayVariants_becomeDays() {
        assertEquals("180天", ScanValueFormat.shelfLife("180日"))
        assertEquals("180天", ScanValueFormat.shelfLife("180 天"))
    }

    /** 中文数字的保质期：这曾经让过期日期直接算不出来 */
    @Test
    fun shelfLife_chineseNumerals_areConverted() {
        assertEquals("2个月", ScanValueFormat.shelfLife("两个月"))
        assertEquals("2个月", ScanValueFormat.shelfLife("二个月"))
        assertEquals("10天", ScanValueFormat.shelfLife("十天"))
        assertEquals("18个月", ScanValueFormat.shelfLife("十八个月"))
        assertEquals("20天", ScanValueFormat.shelfLife("二十天"))
        assertEquals("24个月", ScanValueFormat.shelfLife("二十四个月"))
    }

    @Test
    fun shelfLife_half_isConverted() {
        assertEquals("6个月", ScanValueFormat.shelfLife("半年"))
        assertEquals("18个月", ScanValueFormat.shelfLife("一年半"))
        assertEquals("15天", ScanValueFormat.shelfLife("半个月"))
    }

    @Test
    fun shelfLife_unparseable_isKeptAsIs() {
        assertEquals("见包装", ScanValueFormat.shelfLife("见包装"))
    }

    /** 端到端：中文数字的保质期也能算出过期日期 */
    @Test
    fun chineseShelfLife_producesExpiryDate() {
        assertEquals(
            ExpiryOutcome.Resolved("2026-12-06"),
            ExpiryCalculator.resolve(printedExpiry = "", productionDate = "2026-10-06", shelfLife = "两个月")
        )
    }
}
