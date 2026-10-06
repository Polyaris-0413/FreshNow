package com.freshnow.app.data

import android.content.Context
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.flow.Flow

class ScanRecordRepository(context: Context) {

    private val dao = FreshNowDatabase.getInstance(context).scanRecordDao()

    val records: Flow<List<ScanRecord>> = dao.observeAll()

    suspend fun find(id: Long): ScanRecord? = dao.findById(id)

    suspend fun save(result: ScanResult) {
        dao.insert(
            ScanRecord(
                productionDate = result.productionDate,
                expiryDate = result.expiryDate,
                shelfLife = result.shelfLife,
                savedAt = System.currentTimeMillis()
            )
        )
    }
}
