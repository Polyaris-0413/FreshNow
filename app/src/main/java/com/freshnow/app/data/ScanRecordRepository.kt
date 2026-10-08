package com.freshnow.app.data

import android.content.Context
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.ScanRecordDao
import kotlinx.coroutines.flow.Flow
import java.io.File

class ScanRecordRepository(context: Context) {

    private val dao = FreshNowDatabase.getInstance(context).scanRecordDao()
    private val imageStore = ScanImageStore(context)

    val records: Flow<List<ScanRecord>> = dao.observeAll()

    suspend fun find(id: Long): ScanRecord? = dao.findById(id)

    /**
     * 单条记录的订阅。详情页用它而不是查一次：编辑页改了字段后回到详情页，值要跟着变，
     * 判据落在数据上，不依赖页面何时被重新合成。
     */
    fun observe(id: Long): Flow<ScanRecord?> = dao.observeById(id)

    /**
     * 覆盖整行。照片文件名与保存时间沿用传入记录里的值（调用方在原有记录上改字段即可），
     * 于是编辑不会把记录提到列表最前面
     */
    suspend fun update(record: ScanRecord) = dao.update(record)

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
