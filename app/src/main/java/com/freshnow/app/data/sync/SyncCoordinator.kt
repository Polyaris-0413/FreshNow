package com.freshnow.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.SyncPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger

/** 一轮同步的结果，够界面报一句话 */
internal sealed interface SyncReport {
    data class Done(val outcomes: List<SyncOutcome>) : SyncReport

    /** 配过对，但一台都没连上：多半不在同一个网，或者对方没打开应用 */
    data object Unreachable : SyncReport

    data object NoPeers : SyncReport
}

/**
 * 一次配对的结果。[applied] 单独拿出来：配对成了、同步没成是常事（对方刚好把应用关了），
 * 那时该说「已配对」，不该让用户把配对码再输一遍。
 */
internal data class PairOutcome(val deviceName: String, val applied: Int)

/**
 * 进程级单例（[getInstance]）：服务端要占一个端口、mDNS 广播在系统里也只有一个，多实例会「有时连得上有时连不上」。
 * 会话跟随应用前台状态起停（见 MainActivity）：后台常驻在 Android 15 以上走不通
 * （dataSync 前台服务每天只给 6 小时），而这类应用的使用节奏就是「打开看一眼」。
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
    private val server = SyncServer(records, peers, identity, pairing, ::backfillImagesInBackground)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private var discoveryJob: Job? = null
    private var syncTicker: Job? = null
    private var editWatcher: Job? = null
    private var sessionStarted = false
    private var backfilling = false
    private var pairingWindow: Job? = null

    /** 同一时刻只让一轮交换在跑，见 [syncNow] */
    private val syncLock = Mutex()

    /**
     * 正在同步的调用数，[syncing] 由它折算。用计数而不是布尔：第二个调用在 [syncLock] 外等时
     * 第一个可能刚好放锁，布尔会让指示器在那一瞬闪一下。
     */
    private val activeSyncs = AtomicInteger(0)

    val pairedPeers: Flow<List<SyncPeer>> = peers.peers

    val deviceName: String get() = identity.deviceName()

    private val _pairingCode: StateFlow<String?> = pairing.code

    val pairingCode: StateFlow<String?> = _pairingCode

    private val _discovered = MutableStateFlow<List<DiscoveredPeer>>(emptyList())

    val discovered: StateFlow<List<DiscoveredPeer>> = _discovered.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /**
     * 是否正在同步。给同步页那个按钮置灰用。
     *
     * 主页的下拉指示器刻意不读它：这里包含进前台自动同步、定时兜底、改动推送——都没有手势，
     * 而 M3 的下拉指示器是手势的可见反馈（会滑入并转圈，看着像有人替你下拉了一下）。主页那边
     * 自带一份只算自己那一轮的"refreshing"（见 HomeViewModel）。
     */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    // ---- 会话：应用进前台时起，离开时停 ----

    fun startSession() {
        // 用一个显式开关而不是「job 还活着吗」：会话里最后一步是同步，它跑完 job 就结束了，
        // 而那时服务端和 mDNS 广播都还在，用 job 的状态判会以为会话已经停了
        Log.i(TAG, "进前台：startSession（已在会话中=$sessionStarted）")
        if (sessionStarted) return
        sessionStarted = true
        session = scope.launch {
            Log.i(TAG, "会话协程开始")
            // 无论哪一步不成，都要接着往下走：本机主动去连别人根本不需要自己的服务端与广播，
            // 把这两件非核心的事做成前置条件，它们一挂就连同步一起停了
            val port = runCatching { server.start() }.getOrElse {
                Log.w(TAG, "服务端起不来，本次只作为客户端同步：${it.message}")
                null
            }
            if (port != null) {
                Log.i(TAG, "服务端已起，端口 $port")
                withTimeoutOrNull(NETWORK_STEP_TIMEOUT) {
                    discovery.advertise(identity.deviceId(), identity.deviceName(), port)
                }
                Log.i(TAG, "mDNS 广播完成（超时或被拒都不拦下同步）")
            }
            // 顺手清一次墓碑与孤儿照片：这两件事都要遍历全表或整个目录，
            // 放在用户点下「同步」的那一刻会让他多等，放在这里正好是应用刚打开的空档
            records.purge()
            Log.i(TAG, "墓碑与孤儿照片已清")
            // 打开应用就同步一轮：用户选的就是「在前台时同步」，而「打开看一眼」正是这台设备的
            // 全部使用节奏。没配过对时这一下直接返回，不发任何网络请求
            Log.i(TAG, "进前台自动同步结果：${syncNow()}")
        }
        startSyncTicker()
        startEditWatcher()
    }

    /**
     * 本地一有改动就推一轮，不等定时器那一轮。
     * 分工是「事件为主、轮询兜底」：这一条管「两台都开着、一台刚改完」——用户唯一会盯着屏幕等结果的那个场景。
     * 推不到的情形交给 [FOREGROUND_SYNC_INTERVAL]：改动时对方不在前台，或这次推送没送出去。
     * 去抖 [LOCAL_EDIT_DEBOUNCE]：一次操作可能连着写两次库，而一轮就是一次完整的整份状态交换。
     * 只在会话里听：「推」依赖前台（本机要有服务端、要知道往哪儿推）。
     */
    @OptIn(FlowPreview::class)
    private fun startEditWatcher() {
        if (editWatcher?.isActive == true) return
        editWatcher = scope.launch {
            ScanRecordRepository.localEdits
                .debounce(LOCAL_EDIT_DEBOUNCE)
                .collect { Log.i(TAG, "本地改动后同步结果：${syncNow()}") }
        }
    }

    /**
     * 前台期间每 [FOREGROUND_SYNC_INTERVAL] 同步一轮。
     * 只跟着会话来去：应用在前台才需要「对面动了本机也跟着动」，离开前台就该彻底停手。
     * 与界面停在哪一页无关——用户可能正停在列表上看，那正是最需要它新的时候。
     * 单独起协程而不是把循环并进 [session]：会话那个协程的最后一步是同步，跑完就该结束，
     * 判「是否已在会话中」因此用 [sessionStarted] 而不是 job 活着没有。
     * 失败不报错也不提示：它只是兜底，连不上就等下一轮（多半是对方没打开应用），
     * 每轮弹一句「没连上」只会让用户来关这个功能。要立刻知道结果，用户会去下拉。
     */
    private fun startSyncTicker() {
        if (syncTicker?.isActive == true) return
        syncTicker = scope.launch {
            while (isActive) {
                delay(FOREGROUND_SYNC_INTERVAL)
                Log.i(TAG, "前台定时同步结果：${syncNow()}")
            }
        }
    }

    fun stopSession() {
        sessionStarted = false
        session?.cancel()
        session = null
        syncTicker?.cancel()
        syncTicker = null
        editWatcher?.cancel()
        editWatcher = null
        discovery.stopAdvertising()
        server.stop()
        stopDiscovery()
    }

    // ---- 配对 ----

    /**
     * 两分钟后自动关：用户看到码就不再盯着屏幕了，而一个没人管的窗口等在那里，等于把「限时」
     * 这件事交给用户自己去记。超时长度写在文案里（「码两分钟内有效」），两处必须一致。
     */
    fun openPairingWindow(): String {
        val code = pairing.open()
        pairingWindow?.cancel()
        pairingWindow = scope.launch {
            delay(PAIRING_WINDOW)
            pairing.close()
        }
        return code
    }

    fun closePairingWindow() {
        pairingWindow?.cancel()
        pairingWindow = null
        pairing.close()
    }

    /**
     * 配对请求里的密钥由本机生成，对方存下来再回传同一把——两边拿到的是同一个值，
     * 之后谁发起同步都能解开对方的载荷。
     */
    suspend fun pairWith(peer: DiscoveredPeer, code: String): PairOutcome? =
        rememberPairing(address = peer.address, fallbackName = peer.deviceName, code = code)
            ?.let { syncRightAfterPairing(it) }

    /**
     * 配一台地址要手填的设备。
     *
     * 这条路是给「mDNS 用不了」的网络留的：酒店、企业网里客户端之间常常被隔开，组播直接被丢，
     * 发现页面一片空白，而两台设备明明就在同一个网段。少了这条路，那些网络上这个功能等于没有。
     *
     * 地址里不带端口（用 [DEFAULT_PORT]）：让用户在手机上多抄五个数字，抄错的代价是「配对码
     * 没错但就是配不上」，而用户没有任何办法看出问题出在哪一段。
     */
    suspend fun pairWithAddress(address: String, code: String): PairOutcome? =
        rememberPairing(address = withDefaultPort(address), fallbackName = "", code = code)
            ?.let { syncRightAfterPairing(it) }

    /**
     * 刚配完这一刻对方肯定在线（它刚处理完那个配对请求），这是最不用赌的同步时机。
     * 只同步刚配上的这一台：一次交换是双向的，对方处理这次请求时会把本机的数据一并合上。
     * 失败不报错：配对本身已经成了，这次没拉到数据下次进前台还会再来。
     */
    private suspend fun syncRightAfterPairing(peer: SyncPeer): PairOutcome {
        val address = peer.lastAddress.takeIf { it.isNotEmpty() }
        val outcome = address?.let { engine.syncWith(peer, it) }
        return PairOutcome(deviceName = peer.deviceName, applied = outcome?.applied ?: 0)
    }

    private suspend fun rememberPairing(
        address: String,
        fallbackName: String,
        code: String
    ): SyncPeer? {
        val response = client.pair(
            address = address,
            code = code,
            request = PairRequest(
                deviceId = identity.deviceId(),
                deviceName = identity.deviceName(),
                secret = SyncCrypto.newSecret().toSecretText()
            )
        ) ?: return null

        val peer = SyncPeer(
            deviceId = response.deviceId,
            deviceName = response.deviceName.ifBlank { fallbackName }
                .ifBlank { response.deviceId.take(8) },
            secret = response.secret,
            lastAddress = address,
            pairedAt = System.currentTimeMillis()
        )
        peers.remember(peer)
        return peer
    }

    private fun withDefaultPort(address: String): String {
        val trimmed = address.trim().removePrefix("http://").removeSuffix("/")
        return if (trimmed.contains(':')) trimmed else "$trimmed:$DEFAULT_PORT"
    }

    /**
     * 手动配对时念给对方听。
     * 取当前 WiFi 网络的地址而不是枚举所有网卡：VPN 隧道（tun0、vgate0）与移动数据网卡也满足
     * 「已启用、非回环」，对方在同一个 WiFi 下根本连不过去，全列出来只会让用户不知道该填哪个。
     * 拿不到 WiFi 地址时（本机开热点、系统不让查）退回枚举网卡，但剔掉 VPN 隧道——那种地址对方一定连不上，列出来纯是干扰。
     */
    suspend fun localAddresses(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = appContext.getSystemService(ConnectivityManager::class.java)
            val wifi = manager?.let(::wifiAddresses).orEmpty()
            (wifi.ifEmpty(::networkInterfaceAddresses)).distinct()
        }.getOrDefault(emptyList())
    }

    private fun wifiAddresses(manager: ConnectivityManager): List<String> =
        manager.allNetworks
            .filter { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                // 必须把 VPN 剔掉：VPN 建在 WiFi 上时，它的 NetworkCapabilities 会**同时**
                // 带 TRANSPORT_WIFI（底层网络的传输方式被继承下来），只按 WiFi 过滤会把
                // VPN 的地址一起捞进来——那正是对方连不过去的那个
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
            .flatMap { network -> manager.getLinkProperties(network)?.linkAddresses.orEmpty() }
            .mapNotNull { it.address }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }

    private fun networkInterfaceAddresses(): List<String> =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVpnTunnel() }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }

    /** VPN 隧道的接口名。vgate 是厂商自己起的名字（见真机上的 vgate0）*/
    private fun NetworkInterface.isVpnTunnel(): Boolean =
        name.lowercase().let { n -> VPN_INTERFACE_PREFIXES.any { n.startsWith(it) } }

    /**
     * 先通知对方再删本机这条：本地记录一删，用来认证的密钥就没了，通知发不出去。
     * 通知失败不影响解除本身——对方下次来同步时会从 401 的标记里自己发现（见 [SyncExchange.PeerUnknown]）；
     * 「解除了但对方还没发现」这个中间状态，比「解除了却告诉用户已解除」诚实。
     * 跑在 [scope] 上而不是调用方的作用域里：界面那侧是 viewModelScope，用户点完解除很可能马上离开同步页，
     * 通知会在半路被掉——而它恰恰是「让对方也把这条清掉」的主动手段
     * （剩下那条 401 自愈要求对方恰好在它自己下一轮发起时连得上本机）。
     */
    fun forget(deviceId: String) = scope.launch {
        val peer = peers.find(deviceId) ?: return@launch
        notifyUnpair(peer)
        peers.forget(deviceId)
    }

    /**
     * 两个地址都试（发现到的、以及记下的），与 [syncOne] 同一个道理：发现到的那个可能是过期
     * 条目（NsdManager 会缓存解析结果），只试它就等于把通知押在一次可能落空的投递上。而不同于
     * 同步，这里的失败是没有回音的——用户那边看起来「已经解除了」，对方却还挂着这台设备。
     */
    private suspend fun notifyUnpair(peer: SyncPeer) {
        val addresses = listOfNotNull(
            _discovered.value.firstOrNull { it.deviceId == peer.deviceId }?.address,
            peer.lastAddress.takeIf { it.isNotEmpty() }
        ).distinct()

        for (address in addresses) {
            if (client.unpair(address, identity.deviceId(), peer)) {
                Log.i(TAG, "已告知 ${peer.deviceName} 解除配对（$address）")
                return
            }
        }
        Log.w(TAG, "没能告知 ${peer.deviceName} 已解除配对，等它下次来同步时自己发现")
    }

    // ---- 发现 ----

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch {
            val me = identity.deviceId()
            discovery.discover().collect { found ->
                // 自己的广播自己也会收到（mDNS 会把服务同时报给发布者）。不过滤的话用户会在
                // 「附近设备」里看到自己，而点下去配出的是「自己跟自己配对」这个没意义的怪状态
                _discovered.value = found.filterNot { it.deviceId == me }
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _discovered.value = emptyList()
    }

    // ---- 同步 ----

    /**
     * 逐台来而不是并发：一次交换就是把整份状态发给对方，几台同时来会让手机在几秒里同时打包好几份、
     * 写好几轮库，收益只是省下一点点等待，代价是发热与更长的卡顿。
     * 进来先排队（[syncLock]）：进前台、定时、下拉刷新、同步页按钮四条路都通到这里，两轮叠在一起会同时
     * 打包、同时合并写库，还会各自认定「该补图」而重复拉一遍。第二个调用跑自己那一轮而不是复用第一轮的结果
     * ——发起方是一次刚做的手势，它要的是这一轮自己的回报。
     */
    suspend fun syncNow(): SyncReport {
        activeSyncs.incrementAndGet()
        _syncing.value = true
        try {
            return syncLock.withLock { exchangeAll() }
        } finally {
            if (activeSyncs.decrementAndGet() == 0) _syncing.value = false
        }
    }

    private suspend fun exchangeAll(): SyncReport {
        val all = peers.all()
        if (all.isEmpty()) return SyncReport.NoPeers

        val outcomes = all.mapNotNull { peer -> syncOne(peer) }
        // 连上过才补图：两端同时在线的时刻正是补图最省的时机，而图本身不在交换里。
        // 放到后台做，否则用户点一下同步要等到几百张图都拉完才能看到结果
        if (outcomes.isNotEmpty()) backfillImagesInBackground()
        return if (outcomes.isEmpty()) SyncReport.Unreachable else SyncReport.Done(outcomes)
    }

    /**
     * 先用记下来的地址试，不成才去做发现——这个顺序很要紧：发现要等 mDNS 出结果（几秒），
     * 而记住的地址在多数情况下直接就连上了。反过来写（先等发现再连）等于每次同步都白等
     * 几秒，而等出来的东西未必比手里的地址更有用。
     *
     * 两条路都不通就返回 null，由调用方按「这台没连上」处理。
     */
    private suspend fun syncOne(peer: SyncPeer): SyncOutcome? {
        peer.lastAddress.takeIf { it.isNotEmpty() }?.let { address ->
            Log.i(TAG, "与 ${peer.deviceName} 同步：先试记下的地址 $address")
            engine.syncWith(peer, address)?.let { return it }
        }

        awaitDiscovery()
        val discovered = _discovered.value.firstOrNull { it.deviceId == peer.deviceId }?.address
        if (discovered != null && discovered != peer.lastAddress) {
            Log.i(TAG, "与 ${peer.deviceName} 同步：改用发现到的地址 $discovered")
            engine.syncWith(peer, discovered)?.let { return it }
        }
        Log.w(TAG, "与 ${peer.deviceName} 同步失败：记下的地址与发现都没走通")
        return null
    }

    /** 本地都齐了就直接返回 true，不会去打扰对端 */
    suspend fun fetchPhoto(record: ScanRecord): Boolean {
        val peer = peers.find(record.updatedBy) ?: peers.all().firstOrNull() ?: return false
        val address = addressOf(peer) ?: return false
        return engine.fetchImages(record, peer, address)
    }

    /**
     * 两个入口：自己发起的一轮跑完（见 [exchangeAll]），以及收到对方推来的一轮之后（见 [SyncServer] 的 onIncomingSync）。
     * 后者不可省：交换里只有图片名，图本身不在交换里，而对方此刻一定在线；等自己下次发起时对方可能已退出应用。
     * 逐张而不是并发：一次取图就是一次 HTTP 往返加一次落盘，几十张并发只会让正在看列表的用户感到卡顿。
     */
    private suspend fun backfillImages() {
        val missing = records.recordsMissingImages()
        if (missing.isEmpty()) return
        val all = peers.all()
        if (all.isEmpty()) return

        for (record in missing) {
            // 优先问改过这条的那台设备；它那里没有（比如这条是从第三台设备中转过来的）
            // 就依次问其余的，多设备下不一定每台都存着所有图
            val order = all.sortedByDescending { it.deviceId == record.updatedBy }
            for (peer in order) {
                val address = addressOf(peer) ?: continue
                if (engine.fetchImages(record, peer, address)) break
            }
        }
    }

    private fun backfillImagesInBackground() {
        if (backfilling) return
        backfilling = true
        scope.launch {
            try {
                backfillImages()
            } finally {
                backfilling = false
            }
        }
    }

    private fun addressOf(peer: SyncPeer): String? =
        _discovered.value.firstOrNull { it.deviceId == peer.deviceId }?.address
            ?: peer.lastAddress.takeIf { it.isNotEmpty() }

    /**
     * 等的是短窗口而不是「等发现完」：mDNS 没有「发现完」这个时刻，
     * 它随设备上下线持续报告，而用户点下同步之后要的是尽快有结果。
     */
    private suspend fun awaitDiscovery(timeoutMillis: Long = DISCOVERY_GRACE) {
        if (_discovered.value.isNotEmpty()) return
        startDiscovery()
        withTimeoutOrNull(timeoutMillis) { _discovered.first { it.isNotEmpty() } }
    }

    companion object {
        private const val TAG = "FreshNowSync"

        /** VPN 隧道的接口名前缀，回退到枚举网卡时用 */
        private val VPN_INTERFACE_PREFIXES = listOf("tun", "tap", "ppp", "vgate")

        /**
         * 等发现出结果的上限。取到 6 秒是因为 mDNS 本来就慢，窗口一短这次同步就直接判失败，
         * 用户看到的就是「开了应用也没同步」；它跑在后台协程里，多等几秒不会卡住任何界面。
         */
        private const val DISCOVERY_GRACE = 6_000L

        /** 广播这类非核心步骤的等待上限：它们超时也不该拖住同步 */
        private const val NETWORK_STEP_TIMEOUT = 3_000L

        /**
         * 前台定时同步的间隔。
         * 它是**兜底**，不是主力：本地一改动就有一轮主动推送（见 [startEditWatcher]），这一轮管的是「推送送不到」
         * 的两种情况——改动发生时对方不在前台（它服务端没起，必然连不上），或者那次推送失败。
         * 跟着改动走的那条已经是即时的，这里再跟到两分钟只会让同一件事跑两遍。
         * 五分钟也落在同类实现的区间里：KDE Connect 把掉线检测的 keepalive 从 10 秒调到 60 秒，另有把空闲后的
         * 5 秒改成默认 9 分钟；Bitwarden 是 30 分钟，Syncthing 的全量重扫是 1 小时。
         * 再往上限看，Android 官方把「每 15 秒一次网络请求」列为耗电的反面例子（见《减少定期更新的影响》）。
         */
        private const val FOREGROUND_SYNC_INTERVAL = 5 * 60 * 1000L

        /**
         * 本地改动去抖的时长。
         *
         * 一次操作可能连着写两次库（比如扫描那条路：行与照片分两步），而每一轮都是一次完整的
         * 整份状态交换；一秒半够把同一个操作里的几次写入合成一轮，又短到用户看不出来。
         */
        private const val LOCAL_EDIT_DEBOUNCE = 1_500L

        /** 配对窗口的时长。与文案「码两分钟内有效」是同一个数 */
        private const val PAIRING_WINDOW = 2 * 60 * 1000L

        @Volatile
        private var instance: SyncCoordinator? = null

        fun getInstance(context: Context): SyncCoordinator =
            instance ?: synchronized(this) {
                instance ?: SyncCoordinator(context.applicationContext).also { instance = it }
            }
    }
}
