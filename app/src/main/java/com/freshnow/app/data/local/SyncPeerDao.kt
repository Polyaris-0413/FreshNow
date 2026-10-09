package com.freshnow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncPeerDao {

    /**
     * 覆盖写入。同一个 deviceId 再次配对（比如对端重装应用、换了密钥）时，旧的密钥已经解不开
     * 对端的请求，留着它只会让同步一直失败；新的配对结果必须能盖过它。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: SyncPeer)

    @Query("SELECT * FROM sync_peers ORDER BY pairedAt")
    fun observeAll(): Flow<List<SyncPeer>>

    @Query("SELECT * FROM sync_peers ORDER BY pairedAt")
    suspend fun findAll(): List<SyncPeer>

    @Query("SELECT * FROM sync_peers WHERE deviceId = :deviceId")
    suspend fun find(deviceId: String): SyncPeer?

    @Query("UPDATE sync_peers SET lastAddress = :address WHERE deviceId = :deviceId")
    suspend fun updateAddress(deviceId: String, address: String)

    @Query("DELETE FROM sync_peers WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}
