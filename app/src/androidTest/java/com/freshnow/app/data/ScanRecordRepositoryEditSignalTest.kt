package com.freshnow.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.sync.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * [ScanRecordRepository.localEdits]：哪几种写入要报「用户动了记录」。
 *
 * 同步那边靠它在一改动就主动推一轮（见 SyncCoordinator），所以这里钉的是两侧的边界：
 * 用户来源的写入必须报，同步自己的回写与收尾清理不能报——后者报了，对端合并完再推回来，
 * 两台设备就会互相推个没完，而错处极难在真机上看出来（表现只是「同步好像一直在跑」）。
 *
 * 用内存库 + 临时目录 + 临时身份文件，不碰设备上应用自己的记录与照片
 * （与 ScanRecordRepositoryImageTest 同一个理由）。
 */
@RunWith(AndroidJUnit4::class)
class ScanRecordRepositoryEditSignalTest {

    private lateinit var database: FreshNowDatabase
    private lateinit var directory: File
    private lateinit var identityFile: File
    private lateinit var repository: ScanRecordRepository

    /**
     * 订阅者起在这个作用域上，而不是 runBlocking 的作用域里：这个流不会结束，挂在 runBlocking
     * 名下的子协程会让 runBlocking 一直等它——表现是整个用例卡死，而看不出卡在哪一步。
     */
    private val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun openInMemoryDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FreshNowDatabase::class.java).build()
        directory = File(context.cacheDir, "test_repository_edit_signal")
        // 文件名带随机后缀：DataStore 禁止同一个文件上同时存在两个实例，而每个用例各建一个实例、
        // 上一个的作用域又不会随着用例结束而取消，共用一个名字会让第二个用例当场抛异常
        identityFile = File(context.cacheDir, "test_edit_signal_identity_${UUID.randomUUID()}.preferences_pb")
        repository = ScanRecordRepository(
            context,
            ScanImageStore(directory),
            database.scanRecordDao(),
            DeviceIdentity(PreferenceDataStoreFactory.create { identityFile })
        )
    }

    @After
    fun cleanUp() {
        watcherScope.cancel()
        database.close()
        directory.deleteRecursively()
        identityFile.delete()
    }

    @Test
    fun savingARecordReportsAnEdit() = runBlocking {
        val edits = watchEdits()

        repository.save(ScanResult(productName = "纯牛奶"), frame = null)

        assertEquals("扫描存下来的那条要报到", 1, drain(edits))
    }

    @Test
    fun editingARecordReportsAnEdit() = runBlocking {
        repository.insert(
            ScanRecord(
                productName = "原名",
                productionDate = "",
                expiryDate = "",
                shelfLife = "",
                imageName = "",
                savedAt = 1L
            ),
            ImageChange.Keep
        )
        val saved = onlyRecord()

        val edits = watchEdits()
        repository.update(saved.copy(productName = "改后"), ImageChange.Keep)

        assertEquals("改一条已存记录要报到", 1, drain(edits))
    }

    @Test
    fun deletingRecordsReportsAnEdit() = runBlocking {
        repository.save(ScanResult(productName = "纯牛奶"), frame = null)

        val edits = watchEdits()
        repository.delete(listOf(onlyRecord().id))

        assertEquals("删记录要报到", 1, drain(edits))
    }

    @Test
    fun mergingFromAPeerDoesNotReportAnEdit() = runBlocking {
        val edits = watchEdits()

        val applied = repository.mergeAll(listOf(remoteRecord(syncId = UUID.randomUUID().toString())))

        assertEquals("对端来的那条要真的并进来", 1, applied)
        assertFalse("同步并进来的一条不叫「用户改了记录」", hasEdit(edits))
    }

    @Test
    fun cachingAPhotoFromAPeerDoesNotReportAnEdit() = runBlocking {
        val syncId = UUID.randomUUID().toString()
        repository.mergeAll(listOf(remoteRecord(syncId = syncId)))

        val edits = watchEdits()
        repository.attachImage(syncId, byteArrayOf(0x01, 0x02))

        val cached = requireNotNull(repository.findBySyncId(syncId))
        assertTrue("照片要真的挂到记录上", cached.imageName.isNotEmpty())
        assertFalse("回填照片只是本地缓存，用户没改过这条", hasEdit(edits))
    }

    /**
     * 订阅 [ScanRecordRepository.localEdits] 并把收到的事件丢进一个 channel。
     *
     * 用 UNDISPATCHED 起：订阅必须在下一行之前就挂上（这个流是 replay=0 的广播，晚一步就漏掉整次
     * 写入），而 UNDISPATCHED 会先把 collect 跑到挂起点，那时订阅已经生效。
     */
    private fun watchEdits(): Channel<Unit> {
        val received = Channel<Unit>(Channel.UNLIMITED)
        watcherScope.launch(start = CoroutineStart.UNDISPATCHED) {
            ScanRecordRepository.localEdits.collect { received.trySend(Unit) }
        }
        return received
    }

    /** 短窗口内收到几个事件。窗口取 200ms：写入是本地库操作，事件就在同一个调用里发出 */
    private suspend fun drain(edits: Channel<Unit>): Int {
        var count = 0
        while (withTimeoutOrNull(200) { edits.receive() } != null) count++
        return count
    }

    private suspend fun hasEdit(edits: Channel<Unit>) = drain(edits) > 0

    private suspend fun onlyRecord() = database.scanRecordDao().findAll().single()

    private fun remoteRecord(syncId: String) = ScanRecord(
        productName = "对端那条",
        productionDate = "",
        expiryDate = "",
        shelfLife = "",
        imageName = "",
        savedAt = 1L,
        syncId = syncId,
        updatedAt = 1L,
        updatedBy = UUID.randomUUID().toString()
    )
}
