package com.freshnow.app.data.sync

import android.content.Context
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.SyncPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 一轮同步的结果，够界面报一句话 */
internal sealed interface SyncReport {
    data class Done(val outcomes: List<SyncOutcome>) : SyncReport

    /** 配过对，但一台都没连上：多半不在同一个网，或者对方没打开应用 */
    data object Unreachable : SyncReport

    /** 还没和任何设备配对过 */
    data object NoPeers : SyncReport
}

/**
 * 同步这件事对界面露出的全部。
 *
 * 做成进程级单例（[getInstance]）而不是每个 ViewModel 各拿一个：服务端要占一个端口、mDNS 广播
 * 在系统里也只有一个，多个实例会各自开一份，表现是「有时连得上有时连不上」，而两边看起来都正常。
 *
 * 会话跟随应用的前台状态起停（见 MainActivity）：后台常驻在 Android 15 以上已经走不通
 * （dataSync 前台服务每天只给 6 小时），而这类应用的使用节奏就是「打开看一眼」，
 * 在打开的那一刻同步，体感与常驻无异。
 */
internal class SyncCoordinator private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val records = ScanRecordRepository(appContext)
    private val peers = SyncPeerRepository(appContext)
    private val identity = DeviceIdentity(appContext)
    private val pairing = PairingSession()
    private val discovery = PeerDiscovery(appContext)
    private val client = SyncClient()
    private val engine = SyncEngine(records, peers, identity, client)
    private val server = SyncServer(records, peers, identity, pairing)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private var discoveryJob: Job? = null
    private var sessionStarted = false

    /** 已配对的对端，设置页的设备列表用 */
    val pairedPeers: Flow<List<SyncPeer>> = peers.peers

    /** 本机显示给对方看的名字 */
    val deviceName: String get() = identity.deviceName()

    private val _pairingCode = MutableStateFlow<String?>(null)

    /** 当前显示给用户的配对码，null 表示配对窗口没开 */
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredPeer>>(emptyList())

    /** 局域网里当下能看到的设备 */
    val discovered: StateFlow<List<DiscoveredPeer>> = _discovered.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /** 是否正在同步，用来把按钮置灰 */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    // ---- 会话：应用进前台时起，离开时停 ----

    fun startSession() {
        // 用一个显式开关而不是「job 还活着吗」：会话里最后一步是同步，它跑完 job 就结束了，
        // 而那时服务端和 mDNS 广播都还在，用 job 的状态判会以为会话已经停了
        if (sessionStarted) return
        sessionStarted = true
        session = scope.launch {
            val port = runCatching { server.start() }.getOrNull() ?: return@launch
            discovery.advertise(identity.deviceId(), identity.deviceName(), port)
            // 顺手清一次墓碑与孤儿照片：这两件事都要遍历全表或整个目录，
            // 放在用户点下「同步」的那一刻会让他多等，放在这里正好是应用刚打开的空档
            records.purge()
            // 打开应用就同步一轮：用户选的就是「在前台时同步」，而「打开看一眼」正是这台设备的
            // 全部使用节奏。没配过对时这一下直接返回，不发任何网络请求
            syncNow()
        }
    }

    fun stopSession() {
        sessionStarted = false
        session?.cancel()
        session = null
        discovery.stopAdvertising()
        server.stop()
        stopDiscovery()
    }

    // ---- 配对 ----

    /** 开配对窗口，返回要显示在屏幕上的码 */
    fun openPairingWindow(): String = pairing.open().also { _pairingCode.value = it }

    fun closePairingWindow() {
        pairing.close()
        _pairingCode.value = null
    }

    /**
     * 用 [peer] 的地址和用户输入的码去配上它，成功后本机也把它记下来。
     *
     * 配对请求里的密钥由本机生成，对方存下来再回传同一把——两边拿到的是同一个值，
     * 之后谁发起同步都能解开对方的载荷。
     */
    suspend fun pairWith(peer: DiscoveredPeer, code: String): Boolean {
        val response = client.pair(
            address = peer.address,
            responderDeviceId = peer.deviceId,
            code = code,
            request = PairRequest(
                deviceId = identity.deviceId(),
                deviceName = identity.deviceName(),
                secret = SyncCrypto.newSecret().toSecretText()
            )
        ) ?: return false

        peers.remember(
            SyncPeer(
                deviceId = response.deviceId,
                deviceName = response.deviceName.ifBlank { peer.deviceName },
                secret = response.secret,
                lastAddress = peer.address,
                pairedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    /**
     * 配一台地址要手填的设备。
     *
     * 这条路是给「mDNS 用不了」的网络留的：酒店、企业网里客户端之间常常被隔离，组播直接被丢，
     * 发现页面一片空白，而两台设备明明就在同一个网段。少了这条路，那些网络上这个功能等于没有。
     */
    suspend fun pairWithAddress(address: String, deviceId: String, code: String): Boolean {
        val response = client.pair(
            address = address,
            responderDeviceId = deviceId,
            code = code,
            request = PairRequest(
                deviceId = identity.deviceId(),
                deviceName = identity.deviceName(),
                secret = SyncCrypto.newSecret().toSecretText()
            )
        ) ?: return false

        peers.remember(
            SyncPeer(
                deviceId = response.deviceId,
                deviceName = response.deviceName.ifBlank { response.deviceId.take(8) },
                secret = response.secret,
                lastAddress = address,
                pairedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun forget(deviceId: String) = peers.forget(deviceId)

    // ---- 发现 ----

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch {
            discovery.discover().collect { _discovered.value = it }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _discovered.value = emptyList()
    }

    // ---- 同步 ----

    /**
     * 与每一台已配对设备各交换一轮。
     *
     * 逐台来而不是并发：一次交换就是把整份状态发给对方，几台设备同时来会让手机在几秒里
     * 同时打包好几份、写好几轮库，收益只是省下一点点等待，代价是发热与更长的卡顿。
     */
    suspend fun syncNow(): SyncReport {
        val all = peers.all()
        if (all.isEmpty()) return SyncReport.NoPeers

        _syncing.value = true
        try {
            awaitDiscovery()
            val found = _discovered.value.associateBy { it.deviceId }
            val outcomes = all.mapNotNull { peer ->
                // 先试上次连上的地址（省一次组播往返），不行再用这次发现到的
                val candidates = listOfNotNull(
                    peer.lastAddress.takeIf { it.isNotEmpty() },
                    found[peer.deviceId]?.address
                ).distinct()
                candidates.firstNotNullOfOrNull { engine.syncWith(peer, it) }
            }
            return if (outcomes.isEmpty()) SyncReport.Unreachable else SyncReport.Done(outcomes)
        } finally {
            _syncing.value = false
        }
    }

    /** 把一条记录的照片从对端取回来。已经有的直接返回 true，不会去打扰对端 */
    suspend fun fetchPhoto(record: ScanRecord): Boolean {
        val peer = peers.find(record.updatedBy)
            ?: peers.all().firstOrNull()
            ?: return false
        val address = _discovered.value.firstOrNull { it.deviceId == peer.deviceId }?.address
            ?: peer.lastAddress.takeIf { it.isNotEmpty() }
            ?: return false
        return engine.fetchImage(record, peer, address)
    }

    /**
     * 发现一空就先起一次扫描并稍等片刻。
     *
     * 等的是一个短窗口而不是「等发现完」：mDNS 没有「发现完」这个时刻，它随设备上线下线持续报告，
     * 而用户点下同步之后要的是尽快有结果，不是等到网络里所有设备都露过面。
     */
    private suspend fun awaitDiscovery(timeoutMillis: Long = DISCOVERY_GRACE) {
        if (_discovered.value.isNotEmpty()) return
        startDiscovery()
        withTimeoutOrNull(timeoutMillis) { _discovered.first { it.isNotEmpty() } }
    }

    companion object {
        private const val DISCOVERY_GRACE = 2_500L

        @Volatile
        private var instance: SyncCoordinator? = null

        fun getInstance(context: Context): SyncCoordinator =
            instance ?: synchronized(this) {
                instance ?: SyncCoordinator(context.applicationContext).also { instance = it }
            }
    }
}
