package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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

    /** 「半个月」不再折成 15 天：如实报认不出，而不是给一个靠「1个月=30天」凑出来的日期 */
    @Test
    fun reportsUnparseableHalfMonth() {
        assertEquals(
            ExpiryOutcome.UnparseableShelfLife,
            ExpiryCalculator.resolve("", "2025-01-01", "半个月")
        )
        assertEquals(
            ExpiryOutcome.UnparseableShelfLife,
            ExpiryCalculator.resolve("", "2025-01-01", "1.5个月")
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

    @Test
    fun countsDaysUntilExpiry() {
        val today = LocalDate.of(2026, 1, 1)

        assertEquals(9L, ExpiryCalculator.daysRemaining(ExpiryOutcome.Resolved("2026-01-10"), today))
        assertEquals(0L, ExpiryCalculator.daysRemaining(ExpiryOutcome.Resolved("2026-01-01"), today))
        assertEquals(-3L, ExpiryCalculator.daysRemaining(ExpiryOutcome.Resolved("2025-12-29"), today))
    }

    @Test
    fun countsDaysAcrossLeapDay() {
        assertEquals(
            29L,
            ExpiryCalculator.daysRemaining(
                ExpiryOutcome.Resolved("2024-03-01"),
                LocalDate.of(2024, 2, 1)
            )
        )
    }

    /** 中文数字写的保质期也要能一路算到剩余天数，这是「两个月」算不出来的那条链路的回归 */
    @Test
    fun countsDaysFromExpiryResolvedOutOfChineseShelfLife() {
        val expiry = ExpiryCalculator.resolve("", "2025年1月1日", "十八个月")

        assertEquals(0L, ExpiryCalculator.daysRemaining(expiry, LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun reportsNoCountdownWhenExpiryUnknown() {
        val today = LocalDate.of(2026, 1, 1)

        assertNull(ExpiryCalculator.daysRemaining(ExpiryOutcome.InsufficientInput, today))
        assertNull(ExpiryCalculator.daysRemaining(ExpiryOutcome.UnparseableShelfLife, today))
        assertNull(ExpiryCalculator.daysRemaining(ExpiryOutcome.Resolved("见包装"), today))
    }
}
