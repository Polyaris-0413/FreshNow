package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryCalculatorTest {

    @Test
    fun printedExpiryWins_overComputation() {
        val outcome = ExpiryCalculator.resolve("2026-08-01", "2025-01-01", "18个月")
        assertEquals(ExpiryOutcome.Resolved("2026-08-01"), outcome)
    }

    @Test
    fun computesFromMonths() {
        assertEquals(
            ExpiryOutcome.Resolved("2026-07-01"),
            ExpiryCalculator.resolve("", "2025-01-01", "18个月")
        )
    }

    @Test
    fun computesFromDays() {
        assertEquals(
            ExpiryOutcome.Resolved("2025-06-30"),
            ExpiryCalculator.resolve("", "2025-01-01", "180天")
        )
    }

    @Test
    fun computesFromYears() {
        assertEquals(
            ExpiryOutcome.Resolved("2027-01-01"),
            ExpiryCalculator.resolve("", "2025-01-01", "2年")
        )
    }

    @Test
    fun monthEndClampsToLastValidDay() {
        // 2024 为闰年，1-31 加一个月应落在 2-29
        assertEquals(
            ExpiryOutcome.Resolved("2024-02-29"),
            ExpiryCalculator.resolve("", "2024-01-31", "1个月")
        )
    }

    @Test
    fun toleratesSurroundingTextInShelfLife() {
        assertEquals(
            ExpiryOutcome.Resolved("2026-07-01"),
            ExpiryCalculator.resolve("", "2025-01-01", "常温下保质期18个月")
        )
    }

    @Test
    fun parsesChineseDateFormats() {
        assertEquals(
            ExpiryOutcome.Resolved("2026-07-01"),
            ExpiryCalculator.resolve("", "2025年1月1日", "18个月")
        )
        assertEquals(
            ExpiryOutcome.Resolved("2026-03-15"),
            ExpiryCalculator.resolve("", "20250315", "12个月")
        )
    }

    @Test
    fun reportsUnparseableShelfLifeInsteadOfGuessing() {
        assertEquals(
            ExpiryOutcome.UnparseableShelfLife,
            ExpiryCalculator.resolve("", "2025-01-01", "常温")
        )
    }

    /** 标签上写中文数字的情况，例如「十八个月」 */
    @Test
    fun computesFromChineseNumeralShelfLife() {
        assertEquals(
            ExpiryOutcome.Resolved("2026-07-01"),
            ExpiryCalculator.resolve("", "2025-01-01", "十八个月")
        )
        assertEquals(
            ExpiryOutcome.Resolved("2026-05-01"),
            ExpiryCalculator.resolve("", "2025-11-01", "半年")
        )
    }

    @Test
    fun reportsInsufficientInputWhenProductionDateMissing() {
        assertEquals(
            ExpiryOutcome.InsufficientInput,
            ExpiryCalculator.resolve("", "", "18个月")
        )
    }

    @Test
    fun keepsUnparseablePrintedExpiryVerbatim() {
        assertEquals(
            ExpiryOutcome.Resolved("见包装"),
            ExpiryCalculator.resolve("见包装", "", "")
        )
    }
}
