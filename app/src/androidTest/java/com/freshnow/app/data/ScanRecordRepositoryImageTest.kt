package com.freshnow.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 换照片这条路的落盘顺序：新文件 → 改行 → 删旧文件。
 *
 * 顺序错了（比如先删旧图再写新图）不会立刻报错，只在某一步失败时把照片永久弄丢，所以这里逐条
 * 钉住「改完之后库里指着谁、磁盘上还剩谁」。
 *
 * 用内存库 + 临时目录，不碰设备上应用自己的记录与照片（与 ScanRecordDaoTest、ScanImageStoreTest
 * 同一个理由）。
 */
@RunWith(AndroidJUnit4::class)
class ScanRecordRepositoryImageTest {

    private lateinit var database: FreshNowDatabase
    private lateinit var directory: File
    private lateinit var store: ScanImageStore
    private lateinit var repository: ScanRecordRepository

    @Before
    fun openInMemoryDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FreshNowDatabase::class.java).build()
        directory = File(context.cacheDir, "test_repository_images")
        store = ScanImageStore(directory)
        repository = ScanRecordRepository(context, store, database.scanRecordDao())
    }

    @After
    fun cleanUp() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun replace_writesTheNewFileAndDropsTheOldOne() = runBlocking {
        val saved = insertWithPhoto()
        val oldName = saved.imageName

        val replaced = repository.update(saved, ImageChange.Replace(NEW_PHOTO))

        val updated = requireNotNull(repository.find(saved.id))
        assertTrue("换照片不该失败", replaced)
        assertNotEquals(oldName, updated.imageName)
        assertNull("旧照片要删掉，否则成了没人引用的孤儿", store.find(oldName))
        assertNotNull("新照片要真的在磁盘上", store.find(updated.imageName))
        assertEquals("文字字段与照片是同一次写入", "改后", updated.productName)
        assertEquals("保存时间照旧：编辑不该把记录提到列表最前面", saved.savedAt, updated.savedAt)
    }

    /** 本来就没照片的记录也能换：旧文件名是空串，删它不算失败 */
    @Test
    fun replace_onRecordWithoutImage_works() = runBlocking {
        val saved = insertWithPhoto(withPhoto = false)
        assertEquals("", saved.imageName)

        val replaced = repository.update(saved, ImageChange.Replace(NEW_PHOTO))

        val updated = requireNotNull(repository.find(saved.id))
        assertTrue(replaced)
        assertNotNull(store.find(updated.imageName))
    }

    /** 移除：行里的照片名清空（界面据此显示占位图），文件也跟着删 */
    @Test
    fun remove_clearsTheNameAndDeletesTheFile() = runBlocking {
        val saved = insertWithPhoto()

        repository.update(saved, ImageChange.Remove)

        val updated = requireNotNull(repository.find(saved.id))
        assertEquals("", updated.imageName)
        assertNull(store.find(saved.imageName))
    }

    /** 不换照片时文件原样留着：这一路只改文字字段 */
    @Test
    fun keep_leavesThePhotoAlone() = runBlocking {
        val saved = insertWithPhoto()

        repository.update(saved, ImageChange.Keep)

        val updated = requireNotNull(repository.find(saved.id))
        assertEquals(saved.imageName, updated.imageName)
        assertNotNull(store.find(updated.imageName))
    }

    /**
     * 写盘失败（目录名被一个文件占住）时不拦下整次编辑：文字字段照存、旧照片保留，
     * 返回值如实报 false，界面据此提示一句。
     */
    @Test
    fun replaceWhenWritingFails_keepsTheOldPhotoAndReportsFalse() = runBlocking {
        val saved = insertWithPhoto()
        val blocked = File(directory.parentFile, BLOCKED_DIRECTORY).apply { writeText("占住目录名") }
        val blockedRepository = ScanRecordRepository(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ScanImageStore(blocked),
            database.scanRecordDao()
        )
        try {
            val replaced = blockedRepository.update(saved, ImageChange.Replace(NEW_PHOTO))

            val updated = requireNotNull(repository.find(saved.id))
            assertFalse("写盘失败要如实报回来", replaced)
            assertEquals("旧照片一条都不动", saved.imageName, updated.imageName)
            assertNotNull(store.find(updated.imageName))
            assertEquals("文字字段照旧保存", "改后", updated.productName)
        } finally {
            blocked.delete()
        }
    }

    /** 插一条记录。照片按应用自己的写法落盘，于是这里测的是「编辑时换照片」那一段 */
    private suspend fun insertWithPhoto(withPhoto: Boolean = true): ScanRecord {
        val id = database.scanRecordDao().insert(
            ScanRecord(
                productName = "原名",
                productionDate = "2026-01-01",
                expiryDate = "",
                shelfLife = "1个月",
                imageName = if (withPhoto) store.write(PHOTO).orEmpty() else "",
                savedAt = 222L
            )
        )
        // 编辑测试要的是「改一条已存记录」，先把文字字段改成目标值的那次写入也走仓库
        val saved = requireNotNull(repository.find(id))
        repository.update(saved.copy(productName = "改后"), ImageChange.Keep)
        return requireNotNull(repository.find(id))
    }

    private companion object {
        val PHOTO = byteArrayOf(0x01, 0x02)
        val NEW_PHOTO = byteArrayOf(0x03, 0x04)

        /** 用一个已存在的文件占住目录名，让 mkdirs 与写入都失败 */
        const val BLOCKED_DIRECTORY = "blocked_image_dir"
    }
}
