package com.freshnow.app.ui.scan

import com.freshnow.app.data.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 胶囊报的是**一帧**的观察，不是累计了多少。这条口径是产品定的：用户要知道的是「眼下这一下有没有
 * 收获」，而累加值涨到四项就不再动了。所以这里钉住的是「一帧只看到品名就是 1 项」，而不是 4。
 */
class ScanStatusTest {

    @Test
    fun countsOnlyWhatThisFrameShows() {
        assertEquals(ScanStatus.Found(1), ScanStatus.of(ScanResult(productName = "纯牛奶")))
    }

    @Test
    fun countsEveryFieldOfASingleObservation() {
        val observation = ScanResult(
            productName = "纯牛奶",
            productionDate = "2026-01-15",
            shelfLife = "18个月"
        )

        assertEquals(ScanStatus.Found(3), ScanStatus.of(observation))
    }

    /** 这一帧什么都没看清时不该报「0 项」：那读起来像刚读到的东西被丢了 */
    @Test
    fun frameThatSawNothingSaysNothing() {
        assertEquals(ScanStatus.Hidden, ScanStatus.of(ScanResult()))
        assertEquals(ScanStatus.Hidden, ScanStatus.of(ScanResult(productName = "  ")))
    }
}
