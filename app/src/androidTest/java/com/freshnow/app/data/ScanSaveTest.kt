package com.freshnow.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.sync.DeviceIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * 扫描保存交回新记录的 id，以及这一次写入里都落了什么。
 *
 * 【为什么盯它】开关打开时扫描页凭这个 id 进到这条记录的表单去补填位置。给不出 id 就补填不了；
 * 而更要紧的是「照片在这一步就落盘了」——补填位置是加分项，照片没跟着落下来是减分项。
 *
 * 用内存库 + 临时目录 + 临时身份文件，不碰设备上应用自己的记录、照片与配对关系。
 */
@RunWith(AndroidJUnit4::class)
class ScanSaveTest {

    private lateinit var database: FreshNowDatabase
    private lateinit var directory: File
    private lateinit var identityFile: File
    private lateinit var store: ScanImageStore
    private lateinit var repository: ScanRecordRepository

    @Before
    fun openInMemoryDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FreshNowDatabase::class.java).build()
        directory = File(context.cacheDir, "test_scan_save")
        // 文件名带随机后缀：DataStore 禁止同一个文件上同时存在两个实例，共用一个名字会让第二个
        // 用例当场抛异常
        identityFile = File(context.cacheDir, "test_scan_save_identity_${UUID.randomUUID()}.preferences_pb")
        store = ScanImageStore(directory)
        repository = ScanRecordRepository(
            context,
            store,
            database.scanRecordDao(),
            DeviceIdentity(PreferenceDataStoreFactory.create { identityFile })
        )
    }

    @After
    fun cleanUp() {
        database.close()
        directory.deleteRecursively()
        identityFile.delete()
    }

    @Test
    fun save_returnsTheNewRecordsId_soTheFormCanBeOpenedOnIt() = runBlocking {
        val id = repository.save(ScanResult(productName = "纯牛奶"), location = "", frames = listOf(PHOTO))

        val saved = requireNotNull(repository.find(id))
        assertTrue("交回的 id 要是真记录的主键", id > 0)
        assertEquals("纯牛奶", saved.productName)
    }

    /** 照片与文字是同一次写入。补填位置那一步在它之后，所以照片不靠那一步 */
    @Test
    fun save_writesThePhotoInTheSameWrite() = runBlocking {
        val id = repository.save(ScanResult(productName = "纯牛奶"), location = "", frames = listOf(PHOTO))

        val saved = requireNotNull(repository.find(id))
        assertNotNull("照片要真的在磁盘上", store.find(saved.imageName))
    }

    /** 位置由表单补填，保存这一刻还没有它；这里钉住它不会从别处被填成什么 */
    @Test
    fun save_leavesLocationEmpty() = runBlocking {
        val id = repository.save(ScanResult(productName = "纯牛奶"), location = "", frames = listOf(PHOTO))

        assertEquals("", requireNotNull(repository.find(id)).location)
    }

    private companion object {
        val PHOTO = byteArrayOf(0x01, 0x02)
    }
}
