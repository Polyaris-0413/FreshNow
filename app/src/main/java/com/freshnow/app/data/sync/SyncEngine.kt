package com.freshnow.app.data.sync

import android.util.Log
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.SyncPeer

/**
 * [applied] 与 [sent] 分开记：用户真正关心的是「对方那边多了几条」，而自己这一份有多少条
 * 是每次都一样的整份状态，单独报出来只会让人以为发生了什么。
 */
internal data class SyncOutcome(
    val peerName: String,
    val applied: Int,
    val sent: Int
)

/**
 * 一次同步就是一次往返（见 [SyncClient.exchange]）：本机送整份状态过去，对端先合、再送回它那一份，本机合上。
 * 不做增量：增量要维护「每台设备同步到哪了」的水位，而时钟不同步、设备长期离线时很容易算错，
 * 算错的代价是丢更新且很久才显形。
 * 合并靠 LWW（见 [com.freshnow.app.data.local.beats]），因此幂等、可重入，不需要记「上次同步到哪」。
 */
internal class SyncEngine(
    private val records: ScanRecordRepository,
    private val peers: SyncPeerRepository,
    private val identity: DeviceIdentity,
    private val client: SyncClient
) {

    /** 与一台对端交换一次。连不上或对方回绝返回 null，由调用方决定要不要提示用户 */
    suspend fun syncWith(peer: SyncPeer, address: String): SyncOutcome? {
        val myDeviceId = identity.deviceId()
        val mine = SyncPayload(
            deviceId = myDeviceId,
            deviceName = identity.deviceName(),
            records = records.snapshot().map { it.toDto() }
        )

        return when (val exchange = client.exchange(address, myDeviceId, peer, mine)) {
            is SyncExchange.Done -> {
                val applied = records.mergeAll(exchange.payload.records.map { it.toRecord() })
                // 连上了才记地址：记下来的用途是「下次先试这个」，
                // 一个连不上的地址记着只会让下次也先失败一遍
                peers.rememberAddress(peer.deviceId, address)
                SyncOutcome(
                    peerName = exchange.payload.deviceName.ifBlank { peer.deviceName },
                    applied = applied,
                    sent = mine.records.size
                )
            }

            SyncExchange.PeerUnknown -> {
                // 对端那边已经没有本机了（它解除了配对，或者重装过应用）：本地这条留着
                // 只会每次同步都白试一遍，而界面上还摆着一台永远连不上的设备
                Log.i(TAG, "对端已不认这段配对关系，本机这边也删掉：${peer.deviceName}")
                peers.forget(peer.deviceId)
                null
            }

            SyncExchange.Failed -> null
        }
    }
    /**
     * 返回 false 只表示这一趟没能把全部补齐（对端不在线、或者它那里也缺）：已经取回来的那些留着，
     * 下一次再补剩下的。取回来只挂在本地记录上（见 [ScanRecordRepository.attachImage]），
     * **不参与下一次同步**：它只是把对方的图缓存在本机，用户并没有改这条记录。
     */
    suspend fun fetchImages(record: ScanRecord, peer: SyncPeer, address: String): Boolean {
        val missing = record.imageNames.filterNot { records.hasImage(it) }
        if (missing.isEmpty()) return true

        var allFetched = true
        for (name in missing) {
            val bytes = client.fetchImage(address, identity.deviceId(), peer, record.syncId, name)
            if (bytes == null || !records.attachImage(record.syncId, bytes)) allFetched = false
        }
        return allFetched
    }

    private companion object {
        const val TAG = "FreshNowSync"
    }
}
