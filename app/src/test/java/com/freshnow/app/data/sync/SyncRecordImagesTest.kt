package com.freshnow.app.data.sync

import com.freshnow.app.data.local.ScanRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 照片要跟着记录过网：两个方向各验一次，另加旧版本那一侧的来回。
 *
 * 第一条是多张图的往返，顺序即展示顺序、第一位仍是代表图。
 *
 * 第二条是本任务的真风险：`imageNames` 是后加的键，旧版本设备发来的载荷里没有它。解码一个缺键的
 * 对象，只要字段没有默认值就会当场抛异常——那时丢的不是照片，是那条记录整个同步不过来。
 *
 * 第三条是反方向：`imageName` 那个旧键必须继续发出去。旧版本收到缺了 `imageName` 的载荷会整条
 * 解不出来（那边这个字段没有默认值），所以发记录的人不能只顾新版本。
 */
class SyncRecordImagesTest {

    @Test
    fun severalImagesTravelWithTheRecord() {
        val record = ScanRecord(
            productName = "纯牛奶",
            productionDate = "2026-01-01",
            expiryDate = "2026-12-31",
            shelfLife = "12个月",
            imageNames = listOf("a.jpg", "b.jpg"),
            savedAt = 1L,
            syncId = "sid-1"
        )

        assertEquals("两张都要发得出去", listOf("a.jpg", "b.jpg"), record.toDto().imageNames)
        assertEquals("两张都要收得回来", listOf("a.jpg", "b.jpg"), record.toDto().toRecord().imageNames)
        assertEquals("旧键取第一位", "a.jpg", record.toDto().imageName)
    }

    @Test
    fun payloadFromAnOlderPeerBecomesASingleImageList() {
        val json = """
            {"syncId":"sid-1","productName":"纯牛奶","productionDate":"2026-01-01",
             "expiryDate":"2026-12-31","shelfLife":"12个月","imageName":"a.jpg","savedAt":1,
             "updatedAt":1,"updatedBy":"dev-a","deletedAt":0}
        """.trimIndent()

        val dto = syncJson.decodeFromString<SyncRecordDto>(json)

        assertEquals("对端只带得动一张，就当成单元素列表", listOf("a.jpg"), dto.toRecord().imageNames)
        assertEquals("其余字段照旧", "纯牛奶", dto.productName)
    }

    @Test
    fun newPayloadStillCarriesTheOldKeyForOlderPeers() {
        val record = ScanRecord(
            productName = "纯牛奶",
            productionDate = "",
            expiryDate = "",
            shelfLife = "",
            imageNames = listOf("a.jpg", "b.jpg"),
            savedAt = 1L,
            syncId = "sid-1"
        )

        val text = syncJson.encodeToString(SyncRecordDto.serializer(), record.toDto())

        assertTrue("旧版本还要靠这个键认照片", text.contains("\"imageName\":\"a.jpg\""))
        assertTrue("新版本看这个键", text.contains("\"imageNames\""))
    }
}
