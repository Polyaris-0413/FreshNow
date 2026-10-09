package com.freshnow.app.ui.home

import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.SortOrder
import com.freshnow.app.data.local.ScanRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 列表排序：按创建时间与按过期日期两种。
 *
 * 判据取「排完之后的 id 顺序」——排序的全部内容就是这个顺序。
 *
 * 输入一律按查库的原样给（savedAt 倒序），这样「过期日期排不出先后的那几条是不是还维持原样」
 * 才验得出来。
 */
class SortOrderTest {

    /** 新存的在前，与查库的 `ORDER BY savedAt DESC` 同一方向 */
    @Test
    fun createdOrder_newestFirst() {
        val items = listOf(
            item(id = 2, savedAt = 300),
            item(id = 3, savedAt = 200),
            item(id = 1, savedAt = 100)
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            items.sortedFor(SortOrder.CREATED_AT).map { it.record.id }
        )
    }

    /**
     * 快到期的在前：**已过期的排最上**，印刷值能规整的与推算出来的放在一起比；
     * 过期日期算不出的一律排最后。
     */
    @Test
    fun expiryOrder_soonestFirst() {
        val items = listOf(
            item(id = 1, savedAt = 500, printedExpiry = "2026-12-01"),
            item(id = 2, savedAt = 400, printedExpiry = "2026-01-01"), // 已过期
            item(id = 3, savedAt = 300, printedExpiry = "认不出的写法"), // 印刷值认不出 → 算不出
            item(id = 4, savedAt = 200, printedExpiry = "2027-06-01"),
            // 没印刷值，由生产日期与保质期推算：2026-11-01
            item(id = 5, savedAt = 100, productionDate = "2026-10-01", shelfLife = "1个月"),
            // 中文数字的保质期也认得出 → 同为 2026-12-01，与 1 并列，谁前谁后看创建时间
            item(id = 6, savedAt = 80, productionDate = "2026-10-01", shelfLife = "两个月"),
            // 保质期认不出（不收「半」）→ 算不出
            item(id = 7, savedAt = 50, productionDate = "2026-10-01", shelfLife = "一年半"),
            // 什么都没有 → 算不出
            item(id = 8, savedAt = 10)
        )

        assertEquals(
            listOf(2L, 5L, 1L, 6L, 4L, 3L, 7L, 8L),
            items.sortedFor(SortOrder.EXPIRY_DATE).map { it.record.id }
        )
    }

    /** 同一串写法排得出同一个日期：`2026/12/1` 与 `2026年12月1日` 是同一档，谁前谁后按创建时间 */
    @Test
    fun expiryOrder_sameDateKeepsCreatedOrder() {
        val items = listOf(
            item(id = 1, savedAt = 300, printedExpiry = "2026/12/1"),
            item(id = 2, savedAt = 200, printedExpiry = "2026-12-01"),
            item(id = 3, savedAt = 100, printedExpiry = "2026年12月1日")
        )

        assertEquals(
            listOf(1L, 2L, 3L),
            items.sortedFor(SortOrder.EXPIRY_DATE).map { it.record.id }
        )
    }

    private fun item(
        id: Long,
        savedAt: Long = 0,
        printedExpiry: String = "",
        productionDate: String = "",
        shelfLife: String = ""
    ) = HomeRecordItem(
        record = ScanRecord(
            id = id,
            productName = "记录 $id",
            productionDate = productionDate,
            expiryDate = printedExpiry,
            shelfLife = shelfLife,
            imageName = "",
            savedAt = savedAt
        ),
        expiry = ExpiryCalculator.resolve(printedExpiry, productionDate, shelfLife),
        image = null
    )
}
