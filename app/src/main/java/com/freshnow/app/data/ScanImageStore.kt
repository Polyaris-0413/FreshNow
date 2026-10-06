package com.freshnow.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 扫描照片的存放处。
 *
 * 库里只存文件名，读的时候再按当前的 filesDir 拼出路径：应用数据目录的绝对路径不保证长期不变
 * （换设备恢复备份、迁移存储位置都会变），把绝对路径写进库迟早会失效。
 */
class ScanImageStore(context: Context) {

    private val directory = File(context.filesDir, DIRECTORY_NAME)

    /** 落盘成功返回文件名，失败返回 null（该条记录就没有图片，界面用占位图代替） */
    suspend fun write(frame: ByteArray): String? = withContext(Dispatchers.IO) {
        val name = System.currentTimeMillis().toString() + FILE_SUFFIX
        runCatching {
            directory.mkdirs()
            File(directory, name).writeBytes(frame)
            name
        }.getOrNull()
    }

    fun find(name: String): File? = name
        .takeIf { it.isNotBlank() }
        ?.let { File(directory, it) }
        ?.takeIf { it.isFile }

    private companion object {
        const val DIRECTORY_NAME = "scan_images"
        const val FILE_SUFFIX = ".jpg"
    }
}
