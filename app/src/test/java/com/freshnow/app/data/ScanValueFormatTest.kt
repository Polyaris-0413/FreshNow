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
    }

    @Test
    fun date_compact_isNormalized() {
        assertEquals("2026-10-06", ScanValueFormat.date("20261006"))
    }

    @Test
    fun date_withSurroundingText_keepsOnlyDate() {
        assertEquals("2026-10-06", ScanValueFormat.date("生产日期 2026-10-06"))
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

    @Test
    fun shelfLife_unparseable_isKeptAsIs() {
        assertEquals("见包装", ScanValueFormat.shelfLife("见包装"))
    }
}
