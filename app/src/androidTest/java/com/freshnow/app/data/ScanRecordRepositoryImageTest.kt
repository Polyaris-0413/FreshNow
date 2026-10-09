package com.freshnow.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.sync.DeviceIdentity
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
import java.util.UUID

/**
 * 换照片这条路的落盘顺序：新文件 → 改行。
 *
 * 顺序错了（比如先删旧图再写新图）不会立刻报错，只在某一步失败时把照片永久弄丢，所以这里逐条
 * 钉住「改完之后库里指着谁、磁盘上还剩谁」。
 *
 * 旧文件不在这里删：照片名按内容算，同一张图可能正被另一条记录用着，删了就把那条记录的照片
 * 弄丢了。没人要的那些统一由 [ScanRecordRepository.reconcileImages] 收走，那一段单独测。
 *
 * 用内存库 + 临时目录 + 临时身份文件，不碰设备上应用自己的记录、照片与配对关系
 * （与 ScanRecordDaoTest、ScanImageStoreTest 同一个理由）。
 */
@RunWith(AndroidJUnit4::class)
class ScanRecordRepositoryImageTest {

    private lateinit var database: FreshNowDatabase
    private lateinit var directory: File
    private lateinit var identityFile: File
    private lateinit var store: ScanImageStore
    private lateinit var identity: DeviceIdentity
    private lateinit var repository: ScanRecordRepository

    @Before
    fun openInMemoryDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FreshNowDatabase::class.java).build()
        directory = File(context.cacheDir, "test_repository_images")
        // 文件名带随机后缀：DataStore 禁止同一个文件上同时存在两个实例，而每个用例各建一个实例、
        // 上一个的作用域又不会随着用例结束而取消，共用一个名字会让第二个用例当场抛异常
        identityFile = File(context.cacheDir, "test_repository_identity_${UUID.randomUUID()}.preferences_pb")
        store = ScanImageStore(directory)
        identity = DeviceIdentity(PreferenceDataStoreFactory.create { identityFile })
        repository = ScanRecordRepository(context, store, database.scanRecordDao(), identity)
    }

    @After
    fun cleanUp() {
        database.close()
        directory.deleteRecursively()
        identityFile.delete()
    }

    @Test
    fun replace_writesTheNewFileAndPointsTheRowAtIt() = runBlocking {
        val saved = insertWithPhoto()
        val oldName = saved.imageName

        val replaced = repository.update(saved, ImageChange.Replace(NEW_PHOTO))

        val updated = requireNotNull(repository.find(saved.id))
        assertTrue("换照片不该失败", replaced)
        assertNotEquals(oldName, updated.imageName)
        assertNotNull("新照片要真的在磁盘上", store.find(updated.imageName))
        assertEquals("文字字段与照片是同一次写入", "改后", updated.productName)
        assertEquals("保存时间照旧：编辑不该把记录提到列表最前面", saved.savedAt, updated.savedAt)
    }

    /**
     * 换照片时不能顺手删旧文件：照片名按内容算，那张图可能正被另一条记录用着
     * （比如两条记录存的是同一张照片），删掉就把那一条的照片弄丢了。
     */
    @Test
    fun replace_keepsTheOldFileThatAnotherRecordStillUses() = runBlocking {
        val shared = insertWithPhoto()
        val alsoShared = insertWithPhoto()
        assertEquals("同一张照片在磁盘上只有一份", shared.imageName, alsoShared.imageName)

        repository.update(shared, ImageChange.Replace(NEW_PHOTO))

        assertNotNull("另一条记录还在用这张图，文件必须留着", store.find(alsoShared.imageName))
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

    /** 移除：行里的照片名清空，界面据此显示占位图。文件还留着，直到清理确认没人要 */
    @Test
    fun remove_clearsTheName() = runBlocking {
        val saved = insertWithPhoto()

        repository.update(saved, ImageChange.Remove)

        val updated = requireNotNull(repository.find(saved.id))
        assertEquals("", updated.imageName)
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
     * 清理只收走没人引用的照片。写成「移除照片就把文件删掉」的话，这个用例里的 kept 会先没图：
     * dropped 的移除动作会把两条记录共用的那个文件删掉。
     */
    @Test
    fun reconcileImages_dropsOnlyUnreferencedPhotos() = runBlocking {
        val kept = insertWithPhoto()
        val dropped = insertWithPhoto()
        repository.update(dropped, ImageChange.Remove)
        val orphan = requireNotNull(store.write(ORPHAN_PHOTO))

        repository.reconcileImages()

        assertNotNull("还有记录在用的照片不能被收走", store.find(kept.imageName))
        assertNull("没人引用的照片要收走", store.find(orphan))
    }

    /** 墓碑记录的照片在保留期内不算孤儿：行还指着它，清理时按引用数一条条比对 */
    @Test
    fun reconcileImages_keepsPhotosOfTombstonedRecords() = runBlocking {
        val record = insertWithPhoto()

        repository.delete(listOf(record.id))
        repository.reconcileImages()

        assertNotNull("墓碑还在，照片就还归它所有", store.find(record.imageName))
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
            database.scanRecordDao(),
            identity
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
                savedAt = 222L,
                syncId = UUID.randomUUID().toString()
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
        val ORPHAN_PHOTO = byteArrayOf(0x05, 0x06)

        /** 用一个已存在的文件占住目录名，让 mkdirs 与写入都失败 */
        const val BLOCKED_DIRECTORY = "blocked_image_dir"
    }
}
