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

    @Test
    fun deleteAndCollectImageNames_removesOnlyGivenRows() = runBlocking {
        val kept = dao.insert(record(name = "留着", imageName = "keep.jpg"))
        val dropped = dao.insert(record(name = "删掉一", imageName = "a.jpg"))
        val alsoDropped = dao.insert(record(name = "删掉二", imageName = "b.jpg"))

        val names = dao.deleteAndCollectImageNames(listOf(dropped, alsoDropped))

        // 文件名要交得回来，否则调用方删不掉照片文件
        assertEquals(setOf("a.jpg", "b.jpg"), names.toSet())
        assertNotNull(dao.findById(kept))
        assertNull(dao.findById(dropped))
        assertNull(dao.findById(alsoDropped))
    }

    /** 没照片的记录（imageName 是空串）同样要能删，交回的名字就是空串本身 */
    @Test
    fun deleteAndCollectImageNames_handlesRecordWithoutImage() = runBlocking {
        val id = dao.insert(record(name = "没图", imageName = ""))

        assertEquals(listOf(""), dao.deleteAndCollectImageNames(listOf(id)))
        assertNull(dao.findById(id))
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
        savedAt = 0
    )
}
