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
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.ServerSocket

/** 请求头里带的设备号。服务端靠它查出该用哪把密钥，查不到就一律回绝 */
internal const val HEADER_DEVICE = "X-FreshNow-Device"

internal const val PATH_SYNC = "/sync"
internal const val PATH_PAIR = "/pair"
internal const val PATH_IMAGE = "/image"
internal const val PATH_UNPAIR = "/unpair"

/**
 * 服务端在「我不认识这个设备号」时回的标记，后面跟它自己的设备号。
 *
 * 为什么要把自己的身份也带上：客户端拿到这个标记会删掉本地那条配对记录，而它无法仅凭
 * 这个标记判断话是谁说的。对端的地址失效后（DHCP 重新分配、或记下的地址已经指向了别的
 * 设备），请求会落到一台不相干的设备上，那台也会说「我不认识你」——那时删掉的就是一段
 * 还好好的配对关系。带上设备号，客户端才能确认「说这话的正是我要找的那台」。
 *
 * 与「载荷解不开」分开：后者可能是有人拿错的密钥来试，同样不该动配对关系。
 */
internal const val UNKNOWN_PEER_MARKER = "unknown-peer"

/**
 * 约定的端口。
 *
 * 固定下来是为了「对方根本没被 mDNS 发现到」时还有路可走：那种网络（酒店、企业网、开了
 * AP 隔离的路由器）下用户只能手动填地址，而地址里连同端口一起填，就等于让用户在手机上
 * 抄一串 5 位数字——端口是随机的，这一点用户看不见也背不下来。
 * 被占时退回系统分配，代价是那一次只能靠发现，但只要不撞上，绝大多数情况下撞不上。
 */
internal const val DEFAULT_PORT = 47820

/**
 * 本机开的 HTTP 服务端。
 *
 * 两台设备互为服务端又互为客户端：谁先发起并不重要，重要的是两边都能收。做成「一台当服务器、
 * 另一台当客户端」的话，那台当服务器的设备一换网络角色（从连热点变成开热点）就再也收不到东西，
 * 而用户手上没有任何可操作的地方。
 *
 * 端口优先用 [DEFAULT_PORT]（理由见它的说明：手填地址那条路要一个可预期的端口）。端口被占时才
 * 退回系统分配（port = 0）——撞上别的应用这件事少见，而这一次还有发现流程兜底。
 */
internal class SyncServer(
    private val records: ScanRecordRepository,
    private val peers: SyncPeerRepository,
    private val identity: DeviceIdentity,
    private val pairing: PairingSession,
    /**
     * 替对端合完一次交换之后叫一次。用来在对方还在线的那一刻去补图（见 SyncCoordinator）。
     *
     * 必须不阻塞：调用方是一条正在进行中的 HTTP 请求，对端正等我们的回应。
     */
    private val onIncomingSync: () -> Unit = {}
) {

    private var server: EmbeddedServer<*, *>? = null

    /** 起服务并返回实际端口 */
    suspend fun start(): Int {
        stop()
        // 端口先探一下再启动，不靠「起了失败就换一个」：CIO 引擎是异步启动的，绑定失败的异常
        // 在引擎自己的协程里抛出，调用方的 try 抱不到，那次失败会变成一个没人处理的异常，
        // 把整个应用搅乱。探测与启动之间只隔一个系统调用，抢到的概率可以忽略
        val (started, port) = startOn(if (portIsFree(DEFAULT_PORT)) DEFAULT_PORT else RANDOM_PORT)
            ?: error("同步服务起不来")
        server = started
        return port
    }

    private fun portIsFree(port: Int): Boolean = runCatching {
        ServerSocket(port).use { }
        true
    }.getOrDefault(false)

    /** 在指定端口上起服务；绑定失败返回 null */
    private suspend fun startOn(port: Int): Pair<EmbeddedServer<*, *>, Int>? {
        val candidate = embeddedServer(CIO, port = port, host = "0.0.0.0") { routes() }
        return try {
            candidate.start(wait = false)
            // resolvedConnectors 才是「真的绑上了」的证据：start(wait = false) 只是把启动交给
            // 引擎线程，绑定失败要等到这里才会以异常的形式冒出来
            candidate to candidate.engine.resolvedConnectors().first().port
        } catch (e: Exception) {
            runCatching { candidate.stop(gracePeriodMillis = 0, timeoutMillis = 0) }
            null
        }
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
            // 合完就报一声：对方此刻一定在线（它正等我们回话），而照片不在这份载荷里，
            // 要另外去取。不报的话，接收侧只能等它自己下一次发起同步时才去取，那时对方
            // 可能已经退出应用了——用户看到的就是「记录同步了，照片一直没有」
            onIncomingSync()

            val mine = SyncPayload(
                deviceId = identity.deviceId(),
                deviceName = identity.deviceName(),
                records = records.snapshot().map { it.toDto() }
            )
            call.respondSealed(peer, mine)
        }

        post(PATH_PAIR) {            val code = pairing.currentCode ?: return@post call.respond(HttpStatusCode.NotFound)
            // 配对请求发生在配对之前，没有长期密钥可用，只能用配对码派生的那把。
            // 盐由发起方放在密文前面一并送来，两段都要有才算一个完整的请求
            val body = call.receiveBody() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val salt = body.takeIf { it.size > SyncCrypto.PAIRING_SALT_BYTES }
                ?.copyOfRange(0, SyncCrypto.PAIRING_SALT_BYTES)
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val key = SyncCrypto.derivePairingKey(code, salt)
            val request = runCatching {
                val sealed = body.copyOfRange(SyncCrypto.PAIRING_SALT_BYTES, body.size)
                syncJson.decodeFromString<PairRequest>(String(SyncCrypto.open(key, sealed)))
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
                    lastAddress = call.peerServiceAddress(),
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

        post(PATH_UNPAIR) {
            // 认证就行，不必再看载荷：能通过认证已经证明请求出自持有密钥的那一方，
            // 而「要解除配对」这件事没有别的参数
            val peer = call.authenticatedPeer() ?: return@post
            peers.forget(peer.deviceId)
            call.respond(HttpStatusCode.OK)
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
     * 配对请求来源那台设备的服务地址。
     *
     * 端口取约定端口，**不能**取这次请求的来源端口：那是对方连接时临时开的端口，
     * 关掉就没了，拿它存下来等于存了一个永远连不上的死地址。约定端口是本应用固定的，
     * 对方大概率也在用；万一它那次被占而换了随机端口，这个地址就连不上——那时会退回发现，
     * 面不是留下一条「看着有地址、实际永远连不上」的记录。
     *
     * 不记下这个地址的后果是实打实的：被配对方（显示配对码的那台）手上就什么都没有，
     * 而它每次同步都得等 mDNS 发现出结果，发现本来就慢，于是自动同步看起來就像从来没发生过。
     */
    private fun ApplicationCall.peerServiceAddress(): String =
        "${request.origin.remoteHost}:$DEFAULT_PORT"

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
            // 明说「我们不认识你」，并带上本机的设备号：对方据此删掉那条已经失效的配对关系。
            // 带上身份是必要的——见 UNKNOWN_PEER_MARKER 的说明
            respond(HttpStatusCode.Unauthorized, "$UNKNOWN_PEER_MARKER ${identity.deviceId()}")
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

    private companion object {
        /** 交给系统分配。只在约定端口被占时用得上 */
        const val RANDOM_PORT = 0
    }
}
