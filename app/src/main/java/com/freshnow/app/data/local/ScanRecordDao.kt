package com.freshnow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Insert
    suspend fun insert(record: ScanRecord): Long

    @Query("SELECT * FROM scan_records ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>
}
