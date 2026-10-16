package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 合并一帧观察结果时的两条规矩。
 *
 * 第一条是老规矩：本帧没读到的项保留旧值（标签上的几项常常分散在不同帧里）。
 *
 * 第二条是本任务的全部意义：用户手写过的那一项，后来的帧不能再动它。自己写的字被机器悄悄冲掉，
 * 是这一屏上最不可能被原谅的一件事。
 */
class ScanResultLockTest {

    @Test
    fun `手改过的项不被后续观察覆盖`() {
        val merged = mergeObservation(
            current = ScanResult(productName = "牛奶"),
            observation = ScanResult(productName = "纯牛奶", shelfLife = "12个月"),
            locked = setOf(RecordField.PRODUCT_NAME)
        )

        assertEquals("手写的品名要保持原样", "牛奶", merged.productName)
        assertEquals("没锁的项照旧跟着识别走", "12个月", merged.shelfLife)
    }

    @Test
    fun `没锁的项保持原来的空串不覆盖规则`() {
        val merged = mergeObservation(
            current = ScanResult(productName = "牛奶", shelfLife = "12个月"),
            observation = ScanResult(productName = "纯牛奶"),
            locked = emptySet()
        )

        assertEquals("本帧读到了就覆盖", "纯牛奶", merged.productName)
        assertEquals("本帧没读到就保留", "12个月", merged.shelfLife)
    }

    /** 一条锁都不给时，走的必须还是原来那条路（既有那套扩展函数就是它） */
    @Test
    fun `没有锁时与旧的合并结果一致`() {
        val current = ScanResult(productName = "牛奶", productionDate = "2026-01-01")
        val observation = ScanResult(shelfLife = "12个月")

        assertEquals(current.mergeObservation(observation), mergeObservation(current, observation, emptySet()))
    }
}
