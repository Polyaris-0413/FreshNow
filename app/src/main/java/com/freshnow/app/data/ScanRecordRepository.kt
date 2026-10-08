package com.freshnow.app.data

import android.content.Context
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.ScanRecordDao
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * 一条记录的照片该怎么处理。
 *
 * 三种情形放在一个类型里，是为了让「改字段」与「换照片」走同一次写入：分成两条接口的话，
 * 调用方得自己决定先改哪个，而这里有个不能颠倒的顺序（见 [ScanRecordRepository.update]）。
 */
sealed interface ImageChange {
    /** 照片原样保留 */
    data object Keep : ImageChange

    /** 移除照片，记录回到「没有图片」的状态 */
    data object Remove : ImageChange

    /** 换成 [bytes]。字节已是正方形 JPEG（见 ScanImageCodec），这里只负责落盘 */
    data class Replace(val bytes: ByteArray) : ImageChange
}

/**
 * 扫描记录与它们的照片。
 *
 * 两个存储入口都能从外部传：记录与照片的写入顺序（先写新图、再改行、最后删旧图）是要单独验的，
 * 而那一验要是碰设备上应用自己的库与图片目录，测试就变成了在改用户的记录（见 ScanImageStore 同样的理由）。
 */
class ScanRecordRepository(
    context: Context,
    private val imageStore: ScanImageStore = ScanImageStore(context),
    private val dao: ScanRecordDao = FreshNowDatabase.getInstance(context).scanRecordDao()
) {

    val records: Flow<List<ScanRecord>> = dao.observeAll()

    suspend fun find(id: Long): ScanRecord? = dao.findById(id)

    /**
     * 单条记录的订阅。详情页用它而不是查一次：编辑页改了字段后回到详情页，值要跟着变，
     * 判据落在数据上，不依赖页面何时被重新合成。
     */
    fun observe(id: Long): Flow<ScanRecord?> = dao.observeById(id)

    /**
     * 覆盖整行，按 [image] 处理照片。保存时间沿用传入记录里的值（调用方在原有记录上改字段即可），
     * 于是编辑不会把记录提到列表最前面。
     *
     * 返回 false 只表示照片没换成（新旧文件都写好之后才失败）：旧照片原样保留，其余字段已经落盘，
     * 调用方只需就这一件事提示用户。写盘失败不拦下整次编辑，与 [ScanImageStore.write] 的取舍一致——
     * 没有可用画面时本来就有「这条记录没有图片」这个正常状态。
     *
     * 换照片的顺序固定为新文件 → 改行 → 删旧文件，不能倒：先删旧文件的话，
     * 中途任何一步失败都会把照片永久弄丢。
     */
    suspend fun update(record: ScanRecord, image: ImageChange): Boolean = when (image) {
        ImageChange.Keep -> {
            dao.update(record)
            true
        }

        ImageChange.Remove -> {
            // 先把行改掉再删文件：反过来的话，改行失败就留下一个「行指着已被删掉的文件」，
            // 界面显示占位图而照片其实还在磁盘上。删文件失败没人引用它，只是白占一点空间
            dao.update(record.copy(imageName = ""))
            imageStore.delete(record.imageName)
            true
        }

        is ImageChange.Replace -> {
            val newName = imageStore.write(image.bytes)
            if (newName == null) {
                // 写新文件就失败了：行里的照片名一个字都不改，旧照片继续用
                dao.update(record)
                false
            } else {
                runCatching { dao.update(record.copy(imageName = newName)) }
                    .onFailure {
                        // 行没改成，新文件不能留下：它就是一张谁也找不到的孤儿图
                        imageStore.delete(newName)
                        throw it
                    }
                imageStore.delete(record.imageName)
                true
            }
        }
    }

    /**
     * [frame] 是点保存那一刻的最新画面；图片落盘失败时该记录就没有图片，界面显示占位图
     */
    suspend fun save(result: ScanResult, frame: ByteArray?) {
        dao.insert(
            ScanRecord(
                productName = result.productName,
                productionDate = result.productionDate,
                expiryDate = result.expiryDate,
                shelfLife = result.shelfLife,
                imageName = frame?.let { imageStore.write(it) }.orEmpty(),
                savedAt = System.currentTimeMillis()
            )
        )
    }

    /** 记录对应的照片文件，没有图片或文件已不在时返回 null */
    fun imageFile(record: ScanRecord): File? = imageStore.find(record.imageName)

    /**
     * 删除记录，连同它们的照片。
     *
     * 照片文件名由 DAO 在删行的事务里一并交回（见 [ScanRecordDao.deleteAndCollectImageNames]）：
     * 先删行再去找文件名的话，行没了就再也拼不出照片路径，文件会永远留在磁盘上。
     */
    suspend fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dao.deleteAndCollectImageNames(ids.toList()).forEach { imageStore.delete(it) }
    }
}
