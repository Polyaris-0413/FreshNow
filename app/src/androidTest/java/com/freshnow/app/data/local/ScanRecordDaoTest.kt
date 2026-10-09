package com.freshnow.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 记录的写入路径：删除那段事务、以及编辑时的整行覆盖。用内存库，不碰设备上应用自己的记录。
 */
@RunWith(AndroidJUnit4::class)
class ScanRecordDaoTest {

    private lateinit var database: FreshNowDatabase
    private lateinit var dao: ScanRecordDao

    @Before
    fun openInMemoryDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FreshNowDatabase::class.java).build()
        dao = database.scanRecordDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /**
     * 删除对界面就是消失，但对同步必须留下痕迹。
     */
    @Test
    fun markDeleted_hidesRowsFromReads() = runBlocking {
        val kept = dao.insert(record(name = "留着", imageName = "keep.jpg"))
        val dropped = dao.insert(record(name = "删掉一", imageName = "a.jpg"))
        val alsoDropped = dao.insert(record(name = "删掉二", imageName = "b.jpg"))

        dao.markDeleted(listOf(dropped, alsoDropped), now = 500L, deviceId = "dev-1")

        assertNotNull(dao.findById(kept))
        assertNull(dao.findById(dropped))
        assertNull(dao.findById(alsoDropped))
    }

    /**
     * 墓碑行本身必须还在，否则对端不知道有过这次删除，下次同步会把这条推回来。
     * 时刻与设备号也不能漏：删除就是一次修改，对端要拿它们跟自己的版本比新旧。
     */
    @Test
    fun markDeleted_keepsRowWithItsMomentAndDevice() = runBlocking {
        val id = dao.insert(record(name = "删掉", imageName = "a.jpg"))

        dao.markDeleted(listOf(id), now = 500L, deviceId = "dev-1")

        val stored = dao.findAll().single { it.id == id }
        assertEquals(500L, stored.deletedAt)
        assertEquals(500L, stored.updatedAt)
        assertEquals("dev-1", stored.updatedBy)
    }

    /** 保留期到点才清：清早了，还没同步上的设备会以为这条从未被删过 */
    @Test
    fun deletePurgeable_clearsOnlyTombstonesPastTheirRetention() = runBlocking {
        val expired = dao.insert(record(name = "过期墓碑", imageName = "a.jpg"))
        val fresh = dao.insert(record(name = "新墓碑", imageName = "b.jpg"))
        val alive = dao.insert(record(name = "正常记录", imageName = "c.jpg"))
        dao.markDeleted(listOf(expired), now = 100L, deviceId = "dev-1")
        dao.markDeleted(listOf(fresh), now = 900L, deviceId = "dev-1")

        dao.deletePurgeable(cutoff = 500L)

        val remaining = dao.findAll().map { it.id }.toSet()
        assertEquals(setOf(fresh, alive), remaining)
    }

    @Test
    fun update_replacesEditedFieldsOnly() = runBlocking {
        val id = dao.insert(
            record(name = "原名", imageName = "photo.jpg").copy(
                shelfLife = "6个月",
                savedAt = 111L
            )
        )
        val saved = requireNotNull(dao.findById(id)) { "插入后应当能查到" }

        dao.update(
            saved.copy(
                productName = "改后",
                productionDate = "2026-10-01",
                expiryDate = "2027-04-01",
                shelfLife = "18个月"
            )
        )

        val updated = requireNotNull(dao.findById(id)) { "更新后应当还能查到" }
        assertEquals("改后", updated.productName)
        assertEquals("2026-10-01", updated.productionDate)
        assertEquals("2027-04-01", updated.expiryDate)
        assertEquals("18个月", updated.shelfLife)
        // 调用方只改文字字段，照片与保存时间要原样留着：后者被改掉会把记录提到列表最前面
        assertEquals("photo.jpg", updated.imageName)
        assertEquals(111L, updated.savedAt)
    }

    private fun record(name: String, imageName: String) = ScanRecord(
        productName = name,
        productionDate = "",
        expiryDate = "",
        shelfLife = "",
        imageName = imageName,
        savedAt = 0,
        // 唯一索引不允许两条空 syncId：测试数据也要像真记录一样带着身份进场
        syncId = "sync-$name"
    )
}
