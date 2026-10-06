package com.freshnow.app.data

import android.content.Context
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.flow.Flow
import java.io.File

class ScanRecordRepository(context: Context) {

    private val dao = FreshNowDatabase.getInstance(context).scanRecordDao()
    private val imageStore = ScanImageStore(context)

    val records: Flow<List<ScanRecord>> = dao.observeAll()

    suspend fun find(id: Long): ScanRecord? = dao.findById(id)

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
}
