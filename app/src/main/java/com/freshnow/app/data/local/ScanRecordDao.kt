package com.freshnow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Insert
    suspend fun insert(record: ScanRecord): Long

    @Query("SELECT * FROM scan_records ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE id = :id")
    suspend fun findById(id: Long): ScanRecord?

    @Query("SELECT imageName FROM scan_records WHERE id IN (:ids)")
    suspend fun findImageNames(ids: List<Long>): List<String>

    @Query("DELETE FROM scan_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 删除记录，并把它们的照片文件名交回给调用方。
     *
     * 取名字和删行必须在同一个事务里：分两次做的话，中间若有别的写入插进来，
     * 或者名字还没读全行就没了，删掉的文件名就再也找不回来，照片成了无人引用的孤儿。
     *
     * 传空列表会生成非法的 `IN ()`，调用方需自行挡掉。
     */
    @Transaction
    suspend fun deleteAndCollectImageNames(ids: List<Long>): List<String> {
        val names = findImageNames(ids)
        deleteByIds(ids)
        return names
    }
}
