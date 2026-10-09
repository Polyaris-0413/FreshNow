package com.freshnow.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 扫描照片的存放处。
 *
 * 库里只存文件名，读的时候再按当前的 filesDir 拼出路径：应用数据目录的绝对路径不保证长期不变
 * （换设备恢复备份、迁移存储位置都会变），把绝对路径写进库迟早会失效。
 *
 * 文件名是**内容的散列**，不是时间戳。这是局域网同步要的：两台设备各自收到同一张照片时算出的
 * 名字必须一样，才不会把同一张图在两端各存一份、也不会在对端缓存了旧图之后还接着用——
 * 内容变了名字就变，缓存自然失效。顺带还免掉了「同一张图被两条记录引用」时要去数引用次数。
 *
 * [directory] 由外部传入，测试可以换成临时目录，不往设备上留东西。
 */
class ScanImageStore(private val directory: File) {

    constructor(context: Context) : this(File(context.filesDir, DIRECTORY_NAME))

    /**
     * 落盘成功返回文件名，失败返回 null（该条记录就没有图片，界面用占位图代替）。
     *
     * 先写临时文件再改名，而不是往目标文件直接写：中途失败会留下一张残缺的图，而它的名字已经
     * 声称「内容就是这张照片」，此后每次引用都会读到坏图，且没有任何迹象表明它坏在哪一步。
     * 改名在同一个目录里是原子操作，要么见到完整的图，要么什么也见不到。
     *
     * 要求 `frame` 是已经编码好的 JPEG 字节（见 [ScanImageCodec]），这里不碰图像内容——
     * 一旦这里也做一次编码，散列算出的名字就不再对应真实内容了。
     */
    suspend fun write(frame: ByteArray): String? = withContext(Dispatchers.IO) {
        val name = contentName(frame)
        val target = File(directory, name)
        // 同名的文件已经在盘上，那就是同一张图，再写一遍只是白费 IO
        if (target.isFile) return@withContext name

        runCatching {
            directory.mkdirs()
            val staging = File(directory, "$name$STAGING_SUFFIX")
            staging.writeBytes(frame)
            check(staging.renameTo(target)) { "临时文件改名失败" }
            name
        }.getOrNull()
    }

    fun find(name: String): File? = name
        .takeIf { it.isNotBlank() }
        ?.let { File(directory, it) }
        ?.takeIf { it.isFile }

    /**
     * 删除照片。文件本就不在也算删除成功——目标状态已经达成，没有需要调用方补救的余地。
     */
    suspend fun delete(name: String) {
        if (name.isBlank()) return
        withContext(Dispatchers.IO) { File(directory, name).delete() }
    }

    /** 目录里所有文件的名字（含写了一半的临时文件），供孤儿清理比对 */
    suspend fun listAll(): List<String> = withContext(Dispatchers.IO) {
        directory.listFiles()?.map { it.name }.orEmpty()
    }

    private companion object {
        const val DIRECTORY_NAME = "scan_images"
        const val FILE_SUFFIX = ".jpg"
        const val STAGING_SUFFIX = ".staging"

        /**
         * 取散列的前 16 字节当名字：128 位足以让不同照片不撞名（撞了也只是两条记录共用一张图，
         * 不是数据损坏），而名字短一半，目录列表与人眼看日志时都好认。
         */
        const val NAME_BYTES = 16

        fun contentName(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .take(NAME_BYTES)
                .joinToString("") { "%02x".format(it) } + FILE_SUFFIX
    }
}
