package com.freshnow.app.data.sync

import android.content.Context
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.SyncPeer
import com.freshnow.app.data.local.SyncPeerDao
import kotlinx.coroutines.flow.Flow
import java.util.Base64

/** 对端那把长期密钥。存的是 Base64，用的时候才解开，免得库里的值被当成文本随手打印出去 */
internal fun SyncPeer.key(): ByteArray = Base64.getDecoder().decode(secret)

internal fun ByteArray.toSecretText(): String = Base64.getEncoder().encodeToString(this)

/**
 * 已配对的对端设备。
 *
 * 配对关系要落库（见 [SyncPeer]），这里只是把那张表的读写收在一处：对端的地址、名字、密钥
 * 分散在各个调用点里各改各的，很快就会出现「这里更新了地址那里还拿旧的」。
 */
internal class SyncPeerRepository(private val dao: SyncPeerDao) {

    constructor(context: Context) : this(FreshNowDatabase.getInstance(context).syncPeerDao())

    val peers: Flow<List<SyncPeer>> = dao.observeAll()

    suspend fun all(): List<SyncPeer> = dao.findAll()

    suspend fun find(deviceId: String): SyncPeer? = dao.find(deviceId)

    suspend fun remember(peer: SyncPeer) = dao.upsert(peer)

    /**
     * 记下这次连上的地址。
     *
     * 地址会变（对端的端口是每次启动时由系统分配的），所以它只是「下次先试这个」的提示，
     * 连不上就退回发现流程，不能当成事实记着。
     */
    suspend fun rememberAddress(deviceId: String, address: String) =
        dao.updateAddress(deviceId, address)

    suspend fun forget(deviceId: String) = dao.delete(deviceId)
}
