package com.freshnow.app.data.sync

import com.freshnow.app.data.local.SyncPeer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * 去连对端那一次。
 *
 * 所有失败都折成 null / 空，而不是往上抛：对端随时可能关掉应用、切走网络、换了端口，
 * 这些都不是「错误」而是局域网同步的常态，界面要显示的是「这次没连上」而不是一个异常。
 * 真要区分原因（密钥失效 vs 网络不通）时，看日志比看异常类型更有用。
 */
internal class SyncClient(
    private val client: HttpClient = defaultHttpClient()
) {

    /** 与一台对端交换整份状态，成功返回它对回来的那一份 */
    suspend fun exchange(
        address: String,
        myDeviceId: String,
        peer: SyncPeer,
        payload: SyncPayload
    ): SyncPayload? {
        val sealed = SyncCrypto.seal(peer.key(), encode(payload))
        val response = request {
            client.post(url(address, PATH_SYNC)) {
                header(HEADER_DEVICE, myDeviceId)
                contentType(ContentType.Application.OctetStream)
                setBody(sealed)
            }
        } ?: return null
        return decode(response.body<ByteArray>(), peer)
    }

    /** 取一条记录的照片；对端没有这张图或记录已被删则返回 null */
    suspend fun fetchImage(
        address: String,
        myDeviceId: String,
        peer: SyncPeer,
        syncId: String
    ): ByteArray? {
        val response = request {
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
     * [responderDeviceId] 是对方（被配对的那台）的设备号，而它是配对码派生密钥的 salt 的一部分，
     * 所以这个值必须来自对方自己播出来的信息，不能由调用方随便编——编错了密钥就对不上，
     * 表现是「配对码没错但配对失败」，很难查。
     */
    suspend fun pair(
        address: String,
        responderDeviceId: String,
        code: String,
        request: PairRequest
    ): PairResponse? {
        val key = SyncCrypto.derivePairingKey(code, responderDeviceId)
        val sealed = SyncCrypto.seal(key, encode(request))
        val response = request {
            client.post(url(address, PATH_PAIR)) {
                contentType(ContentType.Application.OctetStream)
                setBody(sealed)
            }
        } ?: return null
        val body: ByteArray = runCatching { response.body<ByteArray>() }.getOrNull() ?: return null
        val plain = runCatching { SyncCrypto.open(key, body) }.getOrNull() ?: return null
        return runCatching { syncJson.decodeFromString<PairResponse>(String(plain)) }.getOrNull()
    }

    fun close() = client.close()

    /** 只把 200 的响应交回去，其余（认证失败、对方没有这张图）一律当「这次没拿到」 */
    private suspend inline fun request(call: () -> io.ktor.client.statement.HttpResponse) =
        runCatching { call() }.getOrNull()?.takeIf { it.status == HttpStatusCode.OK }

    private inline fun <reified T> encode(message: T): ByteArray =
        syncJson.encodeToString(message).toByteArray()

    private inline fun <reified T> decode(bytes: ByteArray, peer: SyncPeer): T? =
        runCatching { syncJson.decodeFromString<T>(String(SyncCrypto.open(peer.key(), bytes))) }
            .getOrNull()

    private fun url(address: String, path: String) = "http://$address$path"

    private companion object {
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
