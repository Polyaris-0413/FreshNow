package com.freshnow.app.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 状态胶囊的最短停留。真机上对着取景框看过：不加这一条，一次请求来回就能让这句话一秒里跳三次。
 */
class ScanStatusDwellTest {

    @Test
    fun firstWordShowsRightAway() {
        val dwell = ScanStatusDwell(DWELL_MS)

        assertEquals(ScanStatus.Found(2), dwell.offer(nowMs = 10_000, ScanStatus.Found(2)))
    }

    @Test
    fun changeInsideDwellIsDropped() {
        val dwell = ScanStatusDwell(DWELL_MS)
        dwell.offer(10_000, ScanStatus.Found(2))

        assertNull(dwell.offer(10_000 + DWELL_MS - 1, ScanStatus.Recognizing))
    }

    /** 丢掉就是丢掉，不留着等停留期满再补演：它说的是「此刻如何」，补一句上一刻的话没有意义 */
    @Test
    fun droppedWordIsNotReplayedLater() {
        val dwell = ScanStatusDwell(DWELL_MS)
        dwell.offer(10_000, ScanStatus.Found(2))
        assertNull(dwell.offer(10_300, ScanStatus.Recognizing))

        // 停留期满后浮上来的仍是「已读到 2 项」，与现显示的相同，因此不算一次改动
        assertNull(dwell.offer(10_000 + DWELL_MS, ScanStatus.Found(2)))
    }

    @Test
    fun changeAfterDwellGoesThrough() {
        val dwell = ScanStatusDwell(DWELL_MS)
        dwell.offer(10_000, ScanStatus.Found(2))

        assertEquals(ScanStatus.Recognizing, dwell.offer(10_000 + DWELL_MS, ScanStatus.Recognizing))
    }

    @Test
    fun sameWordTwiceIsNotAChange() {
        val dwell = ScanStatusDwell(DWELL_MS)
        dwell.offer(10_000, ScanStatus.Found(2))

        assertNull(dwell.offer(10_100, ScanStatus.Found(2)))
    }

    /** 用户按了清空：画面上的字段已经当场没了，胶囊必须同时改口，不能被停留拦住 */
    @Test
    fun resetGoesThroughImmediately() {
        val dwell = ScanStatusDwell(DWELL_MS)
        dwell.offer(10_000, ScanStatus.Found(4))

        dwell.reset(ScanStatus.Hidden)

        // 紧接着就能换下一句：上一次停留属于已经作废的那一句，不该管住这一句
        assertEquals(ScanStatus.Found(1), dwell.offer(10_060, ScanStatus.Found(1)))
    }

    private companion object {
        const val DWELL_MS = 600L
    }
}
