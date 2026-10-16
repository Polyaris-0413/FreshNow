package com.freshnow.app.data.sync

import com.freshnow.app.data.local.ScanRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 位置要跟着记录过网，两个方向各验一次。
 *
 * 第一条是本机的位置要发得出去、也要收得回来。
 *
 * 第二条是这一次改动的真风险所在：位置是后加的字段，对端可能还是不知道有这一项的版本。
 * 它发来的载荷里没有那个键，而解码一个缺键的对象，只要字段没有默认值就会当场抛异常——
 * 那不是「位置没同步过来」，而是那条记录整个同步不过来。
 */
class SyncRecordLocationTest {

    @Test
    fun locationTravelsWithTheRecord() {
        val record = ScanRecord(
            productName = "纯牛奶",
            productionDate = "2026-01-01",
            expiryDate = "2026-12-31",
            shelfLife = "12个月",
            location = "冰箱冷藏室",
            imageName = "",
            savedAt = 1L,
            syncId = "sid-1"
        )

        assertEquals("位置要发得出去", "冰箱冷藏室", record.toDto().location)
        assertEquals("位置要收得回来", "冰箱冷藏室", record.toDto().toRecord().location)
    }

    @Test
    fun payloadFromAnOlderPeerDecodesToAnEmptyLocation() {
        val json = """
            {"syncId":"sid-1","productName":"纯牛奶","productionDate":"2026-01-01",
             "expiryDate":"2026-12-31","shelfLife":"12个月","imageName":"a.jpg","savedAt":1,
             "updatedAt":1,"updatedBy":"dev-a","deletedAt":0}
        """.trimIndent()

        val dto = syncJson.decodeFromString<SyncRecordDto>(json)

        assertEquals("对端没这一项，就按「还没填」收下", "", dto.location)
        assertEquals("其余字段照旧", "纯牛奶", dto.productName)
    }
}
