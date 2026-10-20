package com.freshnow.app.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ImageChange
import com.freshnow.app.data.ScanImageStore
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.sync.DeviceIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * 备份的完整一路：本机库与照片目录 → 文件 → 另一台设备的库与照片目录。
 *
 * 两台「设备」各有独立的库、照片目录与身份，装在同一台测试机上——与 SyncEngineTest 同一套
 * 安排。要验的正是「换机、重装」这种跨设备的情形，共用任何一处存储都测不到它。
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var deviceA: TestDevice
    private lateinit var deviceB: TestDevice

    @Before
    fun openDevices() {
        deviceA = TestDevice("a")
        deviceB = TestDevice("b")
    }

    @After
    fun closeDevices() {
        deviceA.close()
        deviceB.close()
    }

    /** 换机的正路：另一台设备上记录在、照片也在 */
    @Test
    fun exportThenRestore_carriesRecordsAndPhotosToAnotherDevice() = runBlocking {
        deviceA.addRecord("纯牛奶", listOf(MILK_PHOTO))
        deviceA.addRecord("鸡蛋", listOf(MILK_PHOTO, EGG_PHOTO))
        deviceA.addRecord("全麦面包")

        deviceB.backup.restoreFrom(ByteArrayInputStream(deviceA.export()))

        assertEquals(
            setOf("纯牛奶", "鸡蛋", "全麦面包"),
            deviceB.visible().map { it.productName }.toSet()
        )
        // 记录引用的每一张都必须真的落在 B 的磁盘上，否则界面上是占位图
        deviceB.visible().filter { it.imageNames.isNotEmpty() }.forEach { record ->
            assertEquals(
                "${record.productName} 的照片没落全",
                record.imageNames.size,
                deviceB.records.imageFiles(record).size
            )
        }
    }

    /**
     * 导入是合并：本机更新的那一版赢。这条要是错了，用户先改好的内容会被一份旧备份盖回去，
     * 而且这种丢数据要到下次打开详情页才看得见。
     */
    @Test
    fun restore_keepsLocalNewerVersion() = runBlocking {
        deviceA.addRecord("纯牛奶")
        val backup = deviceA.export()

        // 同一 syncId 在 B 上有一个更晚的版本，模拟「B 上刚改过」之后又导入了旧备份
        val original = deviceA.visible().single()
        deviceB.records.mergeAll(
            listOf(
                original.copy(
                    productName = "纯牛奶（改过）",
                    updatedAt = original.updatedAt + 1_000,
                    updatedBy = "device-b"
                )
            )
        )

        deviceB.backup.restoreFrom(ByteArrayInputStream(backup))

        assertEquals(listOf("纯牛奶（改过）"), deviceB.visible().map { it.productName })
    }

    /** 导入也不该删东西：备份里没有的记录只说明导出时它还不存在 */
    @Test
    fun restore_keepsLocalRecordsMissingFromBackup() = runBlocking {
        deviceA.addRecord("纯牛奶")
        val backup = deviceA.export()

        deviceB.addRecord("全麦面包")
        deviceB.backup.restoreFrom(ByteArrayInputStream(backup))

        assertEquals(
            setOf("纯牛奶", "全麦面包"),
            deviceB.visible().map { it.productName }.toSet()
        )
    }

    /** 同一份备份导第二次不该再改动任何东西（连点、或从两个位置各导一次） */
    @Test
    fun restoringSameBackupTwice_changesNothingTheSecondTime() = runBlocking {
        deviceA.addRecord("纯牛奶", listOf(MILK_PHOTO))
        val backup = deviceA.export()

        deviceB.backup.restoreFrom(ByteArrayInputStream(backup))
        val afterFirst = deviceB.visible()
        deviceB.backup.restoreFrom(ByteArrayInputStream(backup))

        assertEquals(afterFirst, deviceB.visible())
    }

    /**
     * 备份里的删除会随导入生效（与同步一致）：A 上删掉的记录，导到还有它的 B 上也会被删。
     * 这是用户选定的语义，不是意外——改成正向合并会把「已删」的记录又推回列表。
     */
    @Test
    fun restore_carriesDeletionsFromBackup() = runBlocking {
        deviceA.addRecord("纯牛奶")
        val inA = deviceA.visible().single()
        // B 先有同一条（同一个 syncId），模拟两台设备都曾经有过它
        deviceB.records.mergeAll(listOf(inA))
        assertEquals(1, deviceB.visible().size)

        // A 删掉它，再把删除后的状态导出
        deviceA.records.delete(listOf(inA.id))
        deviceB.backup.restoreFrom(ByteArrayInputStream(deviceA.export()))

        assertEquals(emptyList<String>(), deviceB.visible().map { it.productName })
    }

    private class TestDevice(label: String) {

        private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        private val database =
            Room.inMemoryDatabaseBuilder(context, FreshNowDatabase::class.java).build()
        private val directory = File(context.cacheDir, "backup_test_$label-${UUID.randomUUID()}")
        private val identityFile =
            File(context.cacheDir, "backup_test_identity_$label-${UUID.randomUUID()}.preferences_pb")

        val records = ScanRecordRepository(
            context,
            ScanImageStore(directory),
            database.scanRecordDao(),
            DeviceIdentity(PreferenceDataStoreFactory.create { identityFile })
        )

        val backup = BackupRepository(records)

        fun close() {
            database.close()
            directory.deleteRecursively()
            identityFile.delete()
        }

        suspend fun addRecord(name: String, photos: List<ByteArray> = emptyList()) {
            records.insert(
                ScanRecord(
                    productName = name,
                    productionDate = "2026-01-01",
                    expiryDate = "2026-12-31",
                    shelfLife = "12个月",
                    savedAt = System.currentTimeMillis()
                ),
                photos.takeIf { it.isNotEmpty() }
                    ?.let { ImageChange.Update(kept = emptyList(), added = it) }
                    ?: ImageChange.Keep
            )
        }

        suspend fun visible(): List<ScanRecord> = records.snapshot().filter { it.deletedAt == 0L }

        suspend fun export(): ByteArray =
            ByteArrayOutputStream().also { backup.exportTo(it) }.toByteArray()
    }

    private companion object {
        /** 内容不同即可：名字由内容算出，这两串字节代表两张不同的照片 */
        val MILK_PHOTO = byteArrayOf(1, 2, 3, 4, 5)
        val EGG_PHOTO = byteArrayOf(6, 7, 8, 9)
    }
}
