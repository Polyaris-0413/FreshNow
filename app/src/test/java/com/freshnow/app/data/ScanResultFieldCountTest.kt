package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 状态胶囊报的「已读到 N 项」取的就是这个数，它的口径必须与 [anyFieldFilled] 是同一套：
 * 两处各算各的，界面就会出现「已读到 3 项」而「清空 / 保存」还是灰的这种自相矛盾。
 */
class ScanResultFieldCountTest {

    @Test
    fun emptyRecordCountsZero() {
        assertEquals(0, ScanResult().filledFieldCount)
    }

    @Test
    fun countsEveryFilledField() {
        val record = ScanResult(
            productName = "纯牛奶",
            productionDate = "2026-01-15",
            expiryDate = "2026-11-15",
            shelfLife = "18个月"
        )

        assertEquals(4, record.filledFieldCount)
    }

    @Test
    fun blankFieldDoesNotCount() {
        val record = ScanResult(productName = "纯牛奶", productionDate = "   ")

        assertEquals(1, record.filledFieldCount)
    }

    @Test
    fun countAndHasAnyValueNeverDisagree() {
        val records = listOf(
            ScanResult(),
            ScanResult("   "),
            ScanResult(productName = "纯牛奶"),
            ScanResult(shelfLife = "18个月", expiryDate = "2026-11-15")
        )

        records.forEach { record ->
            assertEquals(record.filledFieldCount > 0, record.hasAnyValue)
        }
    }
}
