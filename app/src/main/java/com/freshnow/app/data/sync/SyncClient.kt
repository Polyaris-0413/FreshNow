package com.freshnow.app.data.sync

import android.util.Log
import com.freshnow.app.data.local.SyncPeer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * 一次交换的结果。
 *
 * 「对端不认这段配对关系」要单独拿出来：它跟「没连上」对用户意味着完全不同的事，
 * 而且本地那条配对记录应当就此删掉——留着只会每次同步都白试一遍。
 */
internal sealed interface SyncExchange {
    data class Done(val payload: SyncPayload) : SyncExchange

    /** 对端说它不认识本机（它解除了配对，或者重装过应用） */
    data object PeerUnknown : SyncExchange

    /** 没连上，或者回绝的理由与配对关系无关 */
    data object Failed : SyncExchange
}

/**
 * 去连对端那一次。
 *
 * 所有失败都折成返回值，而不是往上抛：对端随时可能关掉应用、切走网络、换了端口，
 * 这些都不是「错误」而是局域网同步的常态，界面要显示的是「这次没连上」而不是一个异常。
 */
internal class SyncClient(
    private val client: HttpClient = defaultHttpClient()
) {

    /** 与一台对端交换整份状态 */
    suspend fun exchange(
        address: String,
        myDeviceId: String,
        peer: SyncPeer,
        payload: SyncPayload
    ): SyncExchange {
        val sealed = SyncCrypto.seal(peer.key(), encode(payload))
        val response = try {
            client.post(url(address, PATH_SYNC)) {
                header(HEADER_DEVICE, myDeviceId)
                contentType(ContentType.Application.OctetStream)
                setBody(sealed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "与 $address 交换：连不上（${e.message}）")
            return SyncExchange.Failed
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            // 两种 401 含义不同：带标记的是「本机不在对端的配对名单里」，
            // 不带的是「载荷解不开」——后者可能是别人拿错的密钥来试，不该动本地的配对关系
            val body = runCatching { response.bodyAsText() }.getOrNull()?.trim()
            val responder = body
                ?.takeIf { it.startsWith(UNKNOWN_PEER_MARKER) }
                ?.removePrefix(UNKNOWN_PEER_MARKER)
                ?.trim()
            // 还要确认说这话的正是本机要找的那台。地址失效后（对方的 IP 被重新分配了、
            // 或者记下的地址本来就指向了别的设备）请求会落到一台不相干的设备上，
            // 它也会说「我不认识你」，而那时删掉的就是一段还好好的配对关系
            return if (responder == peer.deviceId) {
                Log.i(TAG, "与 $address 交换：对端已不认这段配对关系")
                SyncExchange.PeerUnknown
            } else {
                Log.w(TAG, "与 $address 交换：对端回绝（${if (responder == null) "认证不过" else "回应的是另一台设备"}）")
                SyncExchange.Failed
            }
        }
        if (response.status != HttpStatusCode.OK) {
            Log.w(TAG, "与 $address 交换：对端回了 HTTP ${response.status.value}")
            return SyncExchange.Failed
        }

        val body = runCatching { response.body<ByteArray>() }.getOrNull()
            ?: return SyncExchange.Failed
        val decoded = decode<SyncPayload>(body, peer) ?: return SyncExchange.Failed
        return SyncExchange.Done(decoded)
    }

    /**
     * 告诉对端「本机不再与它同步」。
     *
     * 失败不当作解除没生效：对方当时可能没开着应用，而它下次来同步时会从 401 的标记里
     * 自己发现这件事（见 [SyncExchange.PeerUnknown]）。这次通知只是为了让它当场就知道。
     */
    suspend fun unpair(address: String, myDeviceId: String, peer: SyncPeer): Boolean =
        request("与 $address 解除配对") {
            client.post(url(address, PATH_UNPAIR)) {
                header(HEADER_DEVICE, myDeviceId)
            }
        } != null

    /** 取一条记录的照片；对端没有这张图或记录已被删则返回 null */
    suspend fun fetchImage(
        address: String,
        myDeviceId: String,
        peer: SyncPeer,
        syncId: String
    ): ByteArray? {
        val response = request("向 $address 取图") {
            client.get(url(address, "$PATH_IMAGE/$syncId")) {
                header(HEADER_DEVICE, myDeviceId)
            }
        } ?: return null
        val body: ByteArray = runCatching { response.body<ByteArray>() }.getOrNull() ?: return null
        return runCatching { SyncCrypto.open(peer.key(), body) }.getOrNull()
    }

    /**
     * 用配对码连上对方，请求它记住本机。
     *
     * 盐由本机随机生成、明文放在密文前面一起发出去。它不保密，作用只是让同一个配对码在两次
     * 配对里派生出不同的密钥；而「不依赖对方的设备号」这一点很要紧——手动填地址那条路上，
     * 本机除了一个 IP 之外什么都不知道。
     */
    suspend fun pair(
        address: String,
        code: String,
        request: PairRequest
    ): PairResponse? {
        val salt = SyncCrypto.newPairingSalt()
        val key = SyncCrypto.derivePairingKey(code, salt)
        val sealed = SyncCrypto.seal(key, encode(request))
        val response = request("与 $address 配对") {
            client.post(url(address, PATH_PAIR)) {
                contentType(ContentType.Application.OctetStream)
                setBody(salt + sealed)
            }
        } ?: return null
        val body: ByteArray = runCatching { response.body<ByteArray>() }.getOrNull() ?: return null
        val plain = runCatching { SyncCrypto.open(key, body) }.getOrNull() ?: return null
        return runCatching { syncJson.decodeFromString<PairResponse>(String(plain)) }.getOrNull()
    }

    fun close() = client.close()

    /**
     * 只把 200 的响应交回去，其余（认证失败、对方没有这张图）一律当「这次没拿到」。
     *
     * 失败一律记日志：同步是后台跑的，失败了界面上没有任何变化，用户看到的就是「开了应用也没同步」。
     * 没有日志的话，这句话背后可能是连不上、密钥不对、或者对方根本没在跑，而这三件事的处理方式
     * 完全不同。
     */
    private suspend fun request(
        label: String,
        call: suspend () -> HttpResponse
    ): HttpResponse? {
        val response = try {
            call()
        } catch (e: Exception) {
            Log.w(TAG, "$label：连不上（${e.message}）")
            return null
        }
        if (response.status != HttpStatusCode.OK) {
            Log.w(TAG, "$label：对端回了 HTTP ${response.status.value}")
            return null
        }
        return response
    }

    private inline fun <reified T> encode(message: T): ByteArray =
        syncJson.encodeToString(message).toByteArray()

    private inline fun <reified T> decode(bytes: ByteArray, peer: SyncPeer): T? =
        runCatching { syncJson.decodeFromString<T>(String(SyncCrypto.open(peer.key(), bytes))) }
            .onFailure { Log.w(TAG, "解开对端的载荷失败：${it.message}") }
            .getOrNull()

    private fun url(address: String, path: String) = "http://$address$path"

    private companion object {
        const val TAG = "FreshNowSync"
        /**
         * 连接超时给得比请求超时短：连不上要尽快让位给下一台设备，而一次真实的交换里大头
         * 是打包和合并，不是网络往返。
         */
        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
    }
}
