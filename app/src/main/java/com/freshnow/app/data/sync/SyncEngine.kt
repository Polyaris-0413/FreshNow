package com.freshnow.app.data.sync

import android.util.Log
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.SyncPeer

/**
 * 一次同步的结果，供界面报一句话。
 *
 * [applied] 与 [sent] 分开记：用户真正关心的是「对方那边多了几条」，而自己这一份有多少条
 * 是每次都一样的整份状态，单独报出来只会让人以为发生了什么。
 */
internal data class SyncOutcome(
    val peerName: String,
    val applied: Int,
    val sent: Int
)

/**
 * 把两边的内容合到一起。
 *
 * 一次同步就是一次往返（见 [SyncClient.exchange]）：本机把自己整份状态送过去，对端先合本机这份、
 * 再把它自己那一份送回来，本机合上。两边各合一次，一个来回就收敛——不必先问「你有哪些」
 * 再决定要什么，那是两次往返，而在局域网里往返虽快，多一次就多一处可能失败的中间状态。
 *
 * 合并靠 LWW（见 [com.freshnow.app.data.local.beats]），所以这个动作是幂等的、可重入的：
 * 同一条记录收到几次都合出同一个结果，中途失败重来一次即可，不需要记「上次同步到哪」。
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
     * 把一条记录的照片取到本地。本地已经有了就直接返回 true，不去打扰对端。
     *
     * 取回来只挂在本地记录上（见 [ScanRecordRepository.attachImage]），**不参与下一次同步**：
     * 它只是把对方那张图缓存在本机，用户并没有改这条记录。
     */
    suspend fun fetchImage(record: ScanRecord, peer: SyncPeer, address: String): Boolean {
        // 本地已经有这张图（名字对得上且文件在），没什么可取的
        if (record.imageName.isNotEmpty() && records.imageFile(record) != null) return true
        val bytes = client.fetchImage(address, identity.deviceId(), peer, record.syncId)
            ?: return false
        records.attachImage(record.syncId, bytes)
        return true
    }

    private companion object {
        const val TAG = "FreshNowSync"
    }
}
