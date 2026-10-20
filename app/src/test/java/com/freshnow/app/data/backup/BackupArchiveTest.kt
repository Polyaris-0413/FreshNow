package com.freshnow.app.data.backup

import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 备份文件是本机数据的唯一留底：它一旦读不出来，用户手里的记录就只剩这一份坏文件。
 * 所以这里只断言「写完再读回来还是同一份东西」，不比对 zip 里的字节排布。
 */
class BackupArchiveTest {

    private val firstPhoto = byteArrayOf(1, 2, 3, 4)
    private val secondPhoto = byteArrayOf(5, 6, 7, 8)

    private val photos = mapOf(FIRST_IMAGE to firstPhoto, SECOND_IMAGE to secondPhoto)

    @Test
    fun `导出再读回来得到同样的记录与照片`() = runBlocking {
        val records = listOf(
            record(syncId = "record-1", imageNames = listOf(FIRST_IMAGE, SECOND_IMAGE)),
            record(syncId = "record-2", imageNames = listOf(SECOND_IMAGE)),
            record(syncId = "record-3", deletedAt = 1_000L)
        )

        val bytes = ByteArrayOutputStream().also { out ->
            exportBackup(out, records) { photos[it] }
        }.toByteArray()

        val saved = mutableListOf<ByteArray>()
        val imported = readBackup(ByteArrayInputStream(bytes)) { frame ->
            saved += frame
            "${saved.size}".padStart(32, '0') + ".jpg"
        }

        assertEquals(records.map { it.withoutId() }, imported.records)
        // 同一张图被两条记录引用，只该打包一份
        assertEquals(setOf(firstPhoto.toList(), secondPhoto.toList()), saved.map { it.toList() }.toSet())
    }

    @Test
    fun `不是备份的文件当作格式错误`() = runBlocking {
        val error = runCatching {
            readBackup(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))) { null }
        }.exceptionOrNull()

        assertTrue("读不懂的文件应报格式错误，实际是 $error", error is BackupFormatException)
    }

    @Test
    fun `更新版本应用导出的备份被拒绝`() = runBlocking {
        // 只有版本号是本机的未来版本，其余结构与真备份一致：拒绝的理由只能是「读不懂」
        val bytes = zipOf(
            "manifest.json" to """{"format":2,"exportedAt":0,"recordCount":0}""".toByteArray(),
            "records.json" to "[]".toByteArray()
        )

        val error = runCatching {
            readBackup(ByteArrayInputStream(bytes)) { null }
        }.exceptionOrNull()

        assertTrue("新版本格式应被拒绝，实际是 $error", error is BackupFormatException)
    }

    @Test
    fun `磁盘上已经没有的照片不进备份`() = runBlocking {
        val records = listOf(record(syncId = "record-1", imageNames = listOf(FIRST_IMAGE, SECOND_IMAGE)))

        val exported = exportBackup(ByteArrayOutputStream(), records) { name ->
            if (name == FIRST_IMAGE) firstPhoto else null
        }

        assertEquals(1, exported.images)
    }

    @Test
    fun `落盘失败的照片跳过但记录照常导入`() = runBlocking {
        val bytes = ByteArrayOutputStream().also { out ->
            exportBackup(out, listOf(record(syncId = "record-1", imageNames = listOf(FIRST_IMAGE)))) { photos[it] }
        }.toByteArray()

        val imported = readBackup(ByteArrayInputStream(bytes)) { null }

        assertEquals(1, imported.records.size)
        assertEquals(0, imported.images)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ScanRecord.withoutId() = copy(id = 0)

    private fun record(
        syncId: String,
        imageNames: List<String> = emptyList(),
        deletedAt: Long = 0
    ) = ScanRecord(
        productName = "牛奶 $syncId",
        productionDate = "2026-10-01",
        expiryDate = "2026-11-01",
        shelfLife = "31 天",
        location = "冰箱上层",
        imageNames = imageNames,
        savedAt = 1_760_000_000_000,
        syncId = syncId,
        updatedAt = 1_760_000_000_500,
        updatedBy = "device-a",
        deletedAt = deletedAt
    )

    private companion object {
        const val FIRST_IMAGE = "0123456789abcdef0123456789abcdef.jpg"
        const val SECOND_IMAGE = "fedcba9876543210fedcba9876543210.jpg"
    }
}
