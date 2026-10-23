package com.freshnow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Insert
    suspend fun insert(record: ScanRecord): Long

    /** 按主键整行覆盖。照片文件名、保存时间都跟着传进来的记录一起保留，调用方只需在原有记录上改字段 */
    @Update
    suspend fun update(record: ScanRecord)

    // 下面所有面向界面的读取都过滤墓碑：删除留下的行只为同步存在，不该出现在列表或详情里

    @Query("SELECT * FROM scan_records WHERE deletedAt = 0 ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE id = :id AND deletedAt = 0")
    fun observeById(id: Long): Flow<ScanRecord?>

    @Query("SELECT * FROM scan_records WHERE id = :id AND deletedAt = 0")
    suspend fun findById(id: Long): ScanRecord?

    /**
     * 同步交换用：连墓碑一起交出去。墓碑不出去，对端只会看到「它没有这条」并把这条当成新增推回来。
     */
    @Query("SELECT * FROM scan_records")
    suspend fun findAll(): List<ScanRecord>

    /**
     * 按 [syncId] 找，**不过滤墓碑**：合并要比的是「哪一版更新」，墓碑也是一种版本，
     * 漏掉它就等于每次同步都把对端的删除当成新记录插回来。
     */
    @Query("SELECT * FROM scan_records WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): ScanRecord?

    /**
     * 把记录打成墓碑。时刻与修改时刻取同一个值：删除就是一次修改，分开取值会让
     * 「先改后删」和「先删后改」在同步里算出不同结果。
     *
     * 传空列表会生成非法的 `IN ()`，调用方需自行挡掉。
     */
    @Query(
        "UPDATE scan_records SET deletedAt = :now, updatedAt = :now, updatedBy = :deviceId " +
            "WHERE id IN (:ids)"
    )
    suspend fun markDeleted(ids: List<Long>, now: Long, deviceId: String)

    /**
     * 真删行，**不给同步留痕迹**。
     *
     * 只给测试收尾和墓碑清理用。界面上的删除必须走 [markDeleted]：直接从库里抹掉的话，
     * 对端不会知道有过这次删除，下次同步会把这条当成新增推回来。
     */
    @Query("DELETE FROM scan_records WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<Long>)

    /**
     * 只改照片名字这一列。同步收到对端照片后回填本地缓存用，必须只动这一列：
     * 走 [update] 会把整行盖回去，把 [updatedAt] 一起改掉就等于凭空造出一次「本机改了这条」，
     * 而用户只是让这张图显示出来而已。
     */
    @Query("UPDATE scan_records SET imageNames = :names WHERE syncId = :syncId")
    suspend fun updateImageNames(syncId: String, names: List<String>)

    /**
     * 把一张照片接在记录的照片列表末尾。名字已在列表里就什么都不做：同一张图被重复收到是常事
     * （对端每次同步都把它的整份状态发过来），而内容寻址下同名必然同图，重复追加只会让列表无限长。
     */
    @Transaction
    suspend fun appendImageName(syncId: String, name: String) {
        if (name.isEmpty()) return
        val existing = findBySyncId(syncId) ?: return
        if (name in existing.imageNames) return
        updateImageNames(syncId, existing.imageNames + name)
    }

    /**
     * 清掉过期的墓碑。照片不在这里删：行删了以后还要拿全文比对才知道哪张图没人要了，
     * 而那是 [ScanRecordRepository.reconcileImages] 的事，那里能顺带把各种原因留下的
     * 孤儿文件一并收走，不必在每次删除时都想一遍图该不该删。
     */
    @Query("DELETE FROM scan_records WHERE deletedAt != 0 AND deletedAt < :cutoff")
    suspend fun deletePurgeable(cutoff: Long)

    /**
     * 把一条对端来的记录并进本地。事务是必须的：合并的读与写之间若被另一次合并插进来，
     * 两边都会判定「本地没有」，同一个 [ScanRecord.syncId] 被插两次，唯一索引挡住并让整次同步失败——
     * 而这条记录其实只是重复而非冲突。
     * 保留本地的 [ScanRecord.id]：它可能正被详情页的路由、列表的选中集合引用着，换成对端的 id
     * 会让用户眼前的界面指向一条不存在的记录。返回是否真的写入（没赢返回 false，界面不需要刷新）。
     */
    @Transaction
    suspend fun merge(record: ScanRecord): Boolean {
        val existing = findBySyncId(record.syncId)
        return when {
            existing == null -> {
                insert(record.copy(id = 0))
                true
            }

            record.beats(existing) -> {
                update(record.copy(id = existing.id))
                true
            }

            else -> false
        }
    }
}
