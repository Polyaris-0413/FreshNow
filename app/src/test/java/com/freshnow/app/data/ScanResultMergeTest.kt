package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultMergeTest {

    @Test
    fun blankObservationKeepsExistingRecord() {
        val record = ScanResult(productionDate = "2025-01-01")
        val merged = record.mergeObservation(ScanResult(shelfLife = "18个月"))

        assertEquals("2025-01-01", merged.productionDate)
        assertEquals("18个月", merged.shelfLife)
    }

    @Test
    fun fieldsAccumulateAcrossFrames() {
        val afterFirst = ScanResult().mergeObservation(ScanResult(productionDate = "2025-01-01"))
        val afterSecond = afterFirst.mergeObservation(ScanResult(shelfLife = "18个月"))
        val afterThird = afterSecond.mergeObservation(ScanResult(expiryDate = "2026-07-01"))

        assertEquals(
            ScanResult("2025-01-01", "2026-07-01", "18个月"),
            afterThird
        )
    }

    @Test
    fun laterObservationOverwritesSameField() {
        val record = ScanResult(productionDate = "2025-01-01")
        val merged = record.mergeObservation(ScanResult(productionDate = "2025-03-15"))

        assertEquals("2025-03-15", merged.productionDate)
    }

    @Test
    fun emptyObservationChangesNothing() {
        val record = ScanResult("2025-01-01", "2026-07-01", "18个月")

        assertEquals(record, record.mergeObservation(ScanResult()))
    }
}
