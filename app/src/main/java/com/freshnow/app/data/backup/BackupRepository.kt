package com.freshnow.app.data.backup

import com.freshnow.app.data.ScanRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * 把 BackupArchive.kt 那层文件格式接到本机的记录上。
 * 合并走同步用的那个 [ScanRecordRepository.mergeAll]：备份导入与局域网同步是同一件事——
 * 把另一份状态并进来、按 syncId 与 LWW 定胜负，不该有两套合并规则。
 * 这也意味着备份里的删除会随着导入生效（墓碑参与合并），与同步的表现一致。
 */
internal class BackupRepository(private val records: ScanRecordRepository) {

    suspend fun exportTo(output: OutputStream) {
        withContext(Dispatchers.IO) { exportBackup(output, records.snapshot(), records::imageBytes) }
    }

    /** 文件读不出来（不是备份、被截断、版本看不懂）时抛 [BackupFormatException] */
    suspend fun restoreFrom(input: InputStream) {
        withContext(Dispatchers.IO) {
            records.mergeAll(readBackup(input, records::writeImage))
        }
    }
}
