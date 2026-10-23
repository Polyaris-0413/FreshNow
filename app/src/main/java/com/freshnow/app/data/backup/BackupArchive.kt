package com.freshnow.app.data.backup

import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.sync.SyncRecordDto
import com.freshnow.app.data.sync.syncJson
import com.freshnow.app.data.sync.toDto
import com.freshnow.app.data.sync.toRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 备份文件里的格式版本。改条目布局或字段含义时加一，旧版本据此拒绝读不懂的新文件 */
internal const val BACKUP_FORMAT_VERSION = 1

/** 备份文件认不出来（不是本应用的备份、被截断、格式版本比本机新） */
class BackupFormatException(message: String) : Exception(message)

/**
 * 内容与同步载荷同源：记录直接走 [SyncRecordDto] 与 [syncJson]，墓碑、多图、位置的语义与
 * `data/sync/` 完全一致，同步那边加字段这里自动跟上，不必维护第二套映射。
 *
 * 刻意不含 API Key、同步密钥、设备号与已配对设备：这些东西本来就不该离开本机（见
 * AndroidManifest 里关掉系统备份的理由），备份文件常被放进网盘，多一份就多一个泄漏面。
 *
 * 两个函数都用流，不把整份备份读进内存：照片一张几百 KB，几百条就是几十 MB。
 */
@Serializable
private data class BackupManifest(
    val format: Int,
    val exportedAt: Long,
    val recordCount: Int
)

/**
 * 照片只打包未删除记录引用的那些：墓碑记录的照片在界面上早已不可见，打包进来只让文件变大。
 * 记录本身连墓碑一起写——墓碑是「这条被删过」的唯一凭据，丢了它，导入到还有旧数据的设备上
 * 会把已删除的记录重新变成活的。反过来说，导入这份备份会把那些删除一并带过去（与同步一致）。
 *
 * [imageBytes] 返回 null（文件已不在）时跳过这张图，不中断整次导出：缺几张图比导出失败好，
 * 而记录里的名字仍在，界面显示占位图，与同步缺图时的表现一致。
 */
internal suspend fun exportBackup(
    out: OutputStream,
    records: List<ScanRecord>,
    imageBytes: suspend (String) -> ByteArray?
) {
    val zip = ZipOutputStream(out)
    zip.use {
        it.writeBytes(
            MANIFEST_ENTRY,
            syncJson.encodeToString(
                BackupManifest(
                    format = BACKUP_FORMAT_VERSION,
                    exportedAt = System.currentTimeMillis(),
                    recordCount = records.size
                )
            ).toByteArray()
        )
        it.writeBytes(RECORDS_ENTRY, syncJson.encodeToString(records.map { it.toDto() }).toByteArray())

        // 同一张照片可能被多条记录引用（内容寻址），set 保证文件里只有一份
        val sent = mutableSetOf<String>()
        for (record in records) {
            if (record.deletedAt != 0L) continue
            for (name in record.imageNames) {
                if (!sent.add(name)) continue
                val frame = imageBytes(name) ?: continue
                it.writeStored("$IMAGE_DIR$name", frame)
            }
        }
    }
}

/**
 * [saveImage] 负责把一张照片落盘并返回它的名字；照片名字由内容算出，所以这里不需要（也不该）
 * 相信 zip 里的条目名——条目名只用来判断「这是不是一张照片」。落盘失败的那张跳过即可，
 * 记录仍然会导进来，缺的那张显示占位图。
 */
internal suspend fun readBackup(
    input: InputStream,
    saveImage: suspend (ByteArray) -> String?
): List<ScanRecord> {
    var manifest: BackupManifest? = null
    var recordsJson: String? = null

    ZipInputStream(input).use { zip ->
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            when {
                entry.name == MANIFEST_ENTRY ->
                    manifest = runCatching {
                        syncJson.decodeFromString<BackupManifest>(zip.readEntryText(entry))
                    }.getOrElse { throw BackupFormatException("manifest 读不出来") }

                entry.name == RECORDS_ENTRY -> recordsJson = zip.readEntryText(entry)

                entry.name.startsWith(IMAGE_DIR) -> saveImage(zip.readEntryBytes(entry))
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    // 没有 manifest 就不是本应用导出的备份：把那串字节当记录去解，只会解出更莫名其妙的错误
    val header = manifest ?: throw BackupFormatException("不是 FreshNow 的备份文件")
    if (header.format > BACKUP_FORMAT_VERSION) {
        throw BackupFormatException("备份来自更新版本的应用")
    }
    val json = recordsJson ?: throw BackupFormatException("备份里没有记录")

    val records = runCatching {
        syncJson.decodeFromString<List<SyncRecordDto>>(json).map { it.toRecord() }
    }.getOrElse { throw BackupFormatException("记录读不出来") }

    return records
}

private fun ZipOutputStream.writeBytes(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(bytes)
    closeEntry()
}

/**
 * JPEG 已经压过一遍，再 deflate 只能省几个字节，却要多花一次 CPU。
 * STORED 要求条目自带长度与校验和，这两个值都在内存里，算得出来。
 */
private fun ZipOutputStream.writeStored(name: String, bytes: ByteArray) {
    val crc = CRC32().apply { update(bytes) }
    putNextEntry(
        ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
    )
    write(bytes)
    closeEntry()
}

private fun ZipInputStream.readEntryText(entry: ZipEntry): String =
    String(readEntryBytes(entry), Charsets.UTF_8)

/**
 * 读一个条目，超过 [MAX_ENTRY_BYTES] 直接失败：备份要么是本应用自己导出的，要么是用户
 * 从别处拿来的文件，后者不该有能力把内存吃光。
 */
private fun ZipInputStream.readEntryBytes(
    entry: ZipEntry,
    limit: Int = MAX_ENTRY_BYTES
): ByteArray {
    val buffer = ByteArray(READ_BUFFER_BYTES)
    val collected = java.io.ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        collected.write(buffer, 0, read)
        if (collected.size() > limit) throw BackupFormatException("${entry.name} 这个条目过大")
    }
    return collected.toByteArray()
}

private const val MANIFEST_ENTRY = "manifest.json"
private const val RECORDS_ENTRY = "records.json"

/** 照片条目都在这个名字下；读的时候只认这个前缀，写的时候拼在照片名前 */
private const val IMAGE_DIR = "images/"

/** 单个条目上限。最大的条目是照片，正常远小于这个值 */
private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024

private const val READ_BUFFER_BYTES = 8 * 1024
