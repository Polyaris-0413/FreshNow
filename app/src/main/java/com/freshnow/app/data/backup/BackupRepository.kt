package com.freshnow.app.data.backup

import com.freshnow.app.data.ScanRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** 导入一份备份之后实际发生了什么：并进来几条、落下几张照片 */
internal data class BackupRestore(val applied: Int, val images: Int)

/**
 * 把 [BackupArchive] 那层文件格式接到本机的记录上。
 *
 * 合并走的是同步用的那个 [ScanRecordRepository.mergeAll]：备份导入与局域网同步是同一件事——
 * 把另一份状态并进来、按 syncId 与 LWW 定胜负，所以不该有两套合并规则。这也让「导入旧备份」
 * 变成无损操作：本地更新的版本不会被旧的盖掉，备份里没有的记录也不会被删。
 */
internal class BackupRepository(private val records: ScanRecordRepository) {

    suspend fun exportTo(output: OutputStream): BackupExport = withContext(Dispatchers.IO) {
        exportBackup(output, records.snapshot(), records::imageBytes)
    }

    /** 文件读不出来（不是备份、被截断、版本看不懂）时抛 [BackupFormatException] */
    suspend fun restoreFrom(input: InputStream): BackupRestore = withContext(Dispatchers.IO) {
        val imported = readBackup(input, records::writeImage)
        BackupRestore(applied = records.mergeAll(imported.records), images = imported.images)
    }
}
