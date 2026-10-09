package com.freshnow.app.data.sync

import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.SyncPeer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/** 请求头里带的设备号。服务端靠它查出该用哪把密钥，查不到就一律回绝 */
internal const val HEADER_DEVICE = "X-FreshNow-Device"

internal const val PATH_SYNC = "/sync"
internal const val PATH_PAIR = "/pair"
internal const val PATH_IMAGE = "/image"

/**
 * 本机开的 HTTP 服务端。
 *
 * 两台设备互为服务端又互为客户端：谁先发起并不重要，重要的是两边都能收。做成「一台当服务器、
 * 另一台当客户端」的话，那台当服务器的设备一换网络角色（从连热点变成开热点）就再也收不到东西，
 * 而用户手上没有任何可操作的地方。
 *
 * 端口交给系统分配（port = 0）：固定端口在多设备、多应用共存时可能撞上，而撞了的表现是整个
 * 同步功能静默失效；随机的代价只是每次会话重新发现一遍对端，而发现本来就要做。
 */
internal class SyncServer(
    private val records: ScanRecordRepository,
    private val peers: SyncPeerRepository,
    private val identity: DeviceIdentity,
    private val pairing: PairingSession
) {

    private var server: EmbeddedServer<*, *>? = null

    /** 起服务并返回实际端口 */
    suspend fun start(): Int {
        stop()
        val started = embeddedServer(CIO, port = 0, host = "0.0.0.0") { routes() }
        started.start(wait = false)
        server = started
        return started.engine.resolvedConnectors().first().port
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        server = null
    }

    private fun Application.routes() = routing {
        post(PATH_SYNC) {
            val peer = call.authenticatedPeer() ?: return@post
            val incoming = call.readSealed<SyncPayload>(peer) ?: return@post
            // 载荷自称的设备号必须和用来解密的那把密钥对得上：对不上说明这份载荷是别的设备
            // 加密的（或者有人在拼凑），按不可信处理
            if (incoming.deviceId != peer.deviceId) {
                return@post call.respond(HttpStatusCode.Unauthorized)
            }

            // 先合对端的，再把自己这一整份发回去：一次往返两边各合一次，就都收敛到同一个结果
            records.mergeAll(incoming.records.map { it.toRecord() })

            val mine = SyncPayload(
                deviceId = identity.deviceId(),
                deviceName = identity.deviceName(),
                records = records.snapshot().map { it.toDto() }
            )
            call.respondSealed(peer, mine)
        }

        post(PATH_PAIR) {
            val code = pairing.currentCode ?: return@post call.respond(HttpStatusCode.NotFound)
            // 配对请求发生在配对之前，没有长期密钥可用，只能用配对码派生的那把
            val key = SyncCrypto.derivePairingKey(code, identity.deviceId())
            val body = call.receiveBody() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val request = runCatching {
                syncJson.decodeFromString<PairRequest>(String(SyncCrypto.open(key, body)))
            }.getOrNull()
            if (request == null) {
                // 解不开就记一次失败尝试。这里分不清「码不对」与「载荷坏了」，而两者都该计一次
                pairing.recordFailure()
                return@post call.respond(HttpStatusCode.Unauthorized)
            }

            peers.remember(
                SyncPeer(
                    deviceId = request.deviceId,
                    deviceName = request.deviceName,
                    secret = request.secret,
                    // 对端的服务端口要等它来连时才看得到，先留空
                    lastAddress = "",
                    pairedAt = System.currentTimeMillis()
                )
            )
            // 配对成功就关窗：一个码只用一次，留着只是把一个已经被看见的秘密继续摆在屏幕上
            pairing.close()

            call.respondSealed(
                key,
                PairResponse(
                    deviceId = identity.deviceId(),
                    deviceName = identity.deviceName(),
                    secret = request.secret
                )
            )
        }

        get("$PATH_IMAGE/{syncId}") {
            val peer = call.authenticatedPeer() ?: return@get
            val syncId = call.parameters["syncId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val record = records.findBySyncId(syncId)
            // 墓碑记录的照片不给：那在用户眼里已经是删掉的东西，没有取回来的道理
            if (record == null || record.deletedAt != 0L) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            val bytes = records.imageBytes(record)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondBytes(
                SyncCrypto.seal(peer.key(), bytes),
                ContentType.Application.OctetStream
            )
        }
    }

    private suspend fun ApplicationCall.receiveBody(): ByteArray? =
        runCatching { receive<ByteArray>() }.getOrNull()

    /**
     * 认出请求来自哪台已配对设备。认不出就当场回绝并返回 null，调用方直接 return。
     *
     * 只看请求头里的设备号，凭据本身由 GCM 的认证标签保证：能解开的载荷必然出自持有密钥的一方，
     * 而密钥只有配对过的两台设备有。设备号在这里是查找用的名字，不是用来证明身份的东西。
     */
    private suspend fun ApplicationCall.authenticatedPeer(): SyncPeer? {
        val deviceId = request.headers[HEADER_DEVICE]
        val peer = deviceId?.let { peers.find(it) }
        if (peer == null) {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
        return peer
    }

    /** 解开请求体并反序列化。解不开（密钥不对、被改过）或解不成对象，都回绝并返回 null */
    private suspend inline fun <reified T> ApplicationCall.readSealed(peer: SyncPeer): T? {
        val body = receiveBody()
        if (body == null) {
            respond(HttpStatusCode.BadRequest)
            return null
        }
        val plain = runCatching { SyncCrypto.open(peer.key(), body) }.getOrNull()
        if (plain == null) {
            respond(HttpStatusCode.Unauthorized)
            return null
        }
        return runCatching { syncJson.decodeFromString<T>(String(plain)) }
            .getOrElse {
                respond(HttpStatusCode.BadRequest)
                null
            }
    }

    private suspend inline fun <reified T> ApplicationCall.respondSealed(
        peer: SyncPeer,
        message: T
    ) = respondSealed(peer.key(), message)

    private suspend inline fun <reified T> ApplicationCall.respondSealed(
        key: ByteArray,
        message: T
    ) {
        val bytes = SyncCrypto.seal(key, syncJson.encodeToString(message).toByteArray())
        respondBytes(bytes, ContentType.Application.OctetStream)
    }
}
