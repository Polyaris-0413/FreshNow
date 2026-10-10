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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    /** 还没和任何设备配对过 */
    data object NoPeers : SyncReport
}

/**
 * 一次配对的结果。
 *
 * [applied] 单独拿出来而不是并回配对成败：配对成了、同步没成是常事（对方刚好把应用关了），
 * 而那时该说「已配对」，不该让用户把配对码再输一遍。
 */
internal data class PairOutcome(val deviceName: String, val applied: Int)

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
    private var syncTicker: Job? = null
    private var sessionStarted = false
    private var backfilling = false
    private var pairingWindow: Job? = null

    /** 同一时刻只让一轮交换在跑，见 [syncNow] */
    private val syncLock = Mutex()

    /**
     * 正在同步的调用数，[syncing] 由它折算。
     *
     * 用计数而不是布尔：第二个调用在 [syncLock] 外等的时候，第一个可能刚好放锁，
     * 布尔会让指示器在那一瞬闪一下。
     */
    private val activeSyncs = AtomicInteger(0)

    /** 已配对的对端，设置页的设备列表用 */
    val pairedPeers: Flow<List<SyncPeer>> = peers.peers

    /** 本机显示给对方看的名字 */
    val deviceName: String get() = identity.deviceName()

    private val _pairingCode: StateFlow<String?> = pairing.code

    /** 当前显示给用户的配对码，null 表示配对窗口没开 */
    val pairingCode: StateFlow<String?> = _pairingCode

    private val _discovered = MutableStateFlow<List<DiscoveredPeer>>(emptyList())

    /** 局域网里当下能看到的设备 */
    val discovered: StateFlow<List<DiscoveredPeer>> = _discovered.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /**
     * 是否正在同步。同步页拿它把按钮置灰，主页拿它当下拉刷新的指示器。
     *
     * 定时那一轮与进前台那一轮也在内：它们确实在刷新列表，指示器转一下正是它该有的样子。
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
    }

    /**
     * 前台期间每 [FOREGROUND_SYNC_INTERVAL] 同步一轮。
     *
     * 只跟着会话来去：应用在前台才需要「对面动了本机也跟着动」，离开前台就该彻底停手。
     * 与界面停在哪一页无关——用户可能正停在列表上看，那正是最需要它新的时候。
     *
     * 单独起一个协程而不是把循环并进 [session]：会话那个协程的最后一步是同步，它跑完就该结束，
     * 上面判「是否已在会话中」用 [sessionStarted] 而不是 job 活着没有，正是因为这个。
     *
     * 失败不报错也不提示：两分钟一次的事，连不上就等下一轮（多半是对方没打开应用），
     * 每两分钟弹一句「没连上」只会让用户来关这个功能。要立刻知道结果，用户会去下拉。
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
        discovery.stopAdvertising()
        server.stop()
        stopDiscovery()
    }

    // ---- 配对 ----

    /**
     * 开配对窗口，返回要显示在屏幕上的码。
     *
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
     * 用 [peer] 的地址和用户输入的码去配上它，成功后本机也把它记下来，并立即同步一次。
     *
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
     * 配完就同步一次。
     *
     * 配对的意图就是让两台设备共享数据，而刚配完这一刻对方肯定在线（它刚处理完那个配对请求）——
     * 这是最不用赌的同步时机。不同步的话，用户配完什么都看不到，会以为没配上，
     * 而真正要做的那个「切到后台再切回来」没有任何地方提示他。
     *
     * 只同步刚配上的这一台就够了：同步是一次双向交换，对方处理这次请求时会把本机的数据一并合上，
     * 一个来回两边就都有了。
     *
     * 失败不报错：配对本身已经成了，这次没拉到数据下次进前台还会再来，
     * 而把它当成配对失败会让用户重输一遍配对码。
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

    /** 补上约定端口。用户已经写好端口时原样用 */
    private fun withDefaultPort(address: String): String {
        val trimmed = address.trim().removePrefix("http://").removeSuffix("/")
        return if (trimmed.contains(':')) trimmed else "$trimmed:$DEFAULT_PORT"
    }

    /**
     * 本机的局域网地址，手动配对时念给对方听。
     *
     * 取的是**当前 WiFi 网络**的地址，而不是枚举设备上所有网卡。真机上往往还挂着 VPN 隧道
     * （tun0、vgate0 之类）和移动数据的网卡，它们也有地址、也都满足「已启用、非回环」，
     * 但对方在同一个 WiFi 下根本连不过去。全列出来只会让用户对着三个地址不知道该填哪个。
     *
     * 拿不到 WiFi 地址时（本机开着热点，或系统不让查）退回枚举网卡，但把 VPN 隧道剔掉——
     * 那种地址对方一定连不上，列出来纯是干扰。
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
     * 解除与一台设备的配对。
     *
     * 先通知对方再删本机这条：本地记录一删，用来认证的密钥就没了，通知也就发不出去。
     * 通知失败不影响解除本身——对方下次来同步时会从 401 的那个标记里自己发现
     * （见 [SyncExchange.PeerUnknown]），而「解除了但对方还没发现」这个中间状态，
     * 比「解除了却告诉用户已解除」诚实。
     */
    suspend fun forget(deviceId: String) {
        val peer = peers.find(deviceId)
        if (peer != null) {
            addressOf(peer)?.let { address ->
                runCatching { client.unpair(address, identity.deviceId(), peer) }
            }
        }
        peers.forget(deviceId)
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
     * 与每一台已配对设备各交换一轮。
     *
     * 逐台来而不是并发：一次交换就是把整份状态发给对方，几台设备同时来会让手机在几秒里
     * 同时打包好几份、写好几轮库，收益只是省下一点点等待，代价是发热与更长的卡顿。
     *
     * 进来先排队（[syncLock]）：进前台、定时、下拉刷新、同步页按钮四条路都通到这里，两轮叠在
     * 一起会同时打包整份状态、同时合并写库，还会各自认定「该补图」而重复拉一遍。第二个调用等
     * 第一个跑完再跑自己那一轮，而不是复用第一轮的结果——发起方是一次刚做的手势，它要的回报
     * 是这一轮自己的结果。
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
     * 与一台对端交换一轮。
     *
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

    /** 把一条记录的照片从对端取回来。已经有的直接返回 true，不会去打扰对端 */
    suspend fun fetchPhoto(record: ScanRecord): Boolean {
        val peer = peers.find(record.updatedBy) ?: peers.all().firstOrNull() ?: return false
        val address = addressOf(peer) ?: return false
        return engine.fetchImage(record, peer, address)
    }

    /**
     * 把本地缺的照片逐张补回来。
     *
     * 逐张而不是并发：一次取图就是一次完整的 HTTP 往返加一次落盘，同时发几十张只会让手机
     * 在几秒里同时干几十件事，而用户此刻多半正在看列表，卡顿比多等一会儿更明显。
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
                if (engine.fetchImage(record, peer, address)) break
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
        private const val TAG = "FreshNowSync"

        /** VPN 隧道的接口名前缀，回退到枚举网卡时用 */
        private val VPN_INTERFACE_PREFIXES = listOf("tun", "tap", "ppp", "vgate")

        /**
         * 等发现出结果的上限。取到 6 秒是因为 mDNS 本来就慢：设备刚起来时它要先把查询发出去、
         * 等对方回，两三秒内没结果是常事，而窗口一短，这次同步就直接判失败了——用户看到的
         * 就是「开了应用也没同步」。同步跑在后台协程里，多等几秒不会卡住任何界面。
         */
        private const val DISCOVERY_GRACE = 6_000L

        /** 广播这类非核心步骤的等待上限：它们超时也不该拖住同步 */
        private const val NETWORK_STEP_TIMEOUT = 3_000L

        /**
         * 前台定时同步的间隔。
         *
         * 取两分钟，落在同类实现的区间中段：局域网配对类的 KDE Connect 把掉线检测的 keepalive
         * 从 10 秒调到 60 秒，另有一处把空闲后的 5 秒改成默认 9 分钟；云端同步类的 Bitwarden 是
         * 30 分钟，Syncthing 的全量重扫是 1 小时。往上限看，Android 官方把「每 15 秒一次网络
         * 请求」列为耗电的反面例子（见《减少定期更新的影响》）。
         *
         * 往哪边偏取决于数据变化得多快：这里变的是用户手动加改的记录，本身不快，但「两台一起用」
         * 要的正是「对方刚扫的这条我这边也看得见」，所以宁可取短。每分钟也站得住，只是轮数翻倍；
         * 秒级则既踩官方那条线，又没有意义——真要立刻看到，用户会下拉。
         */
        private const val FOREGROUND_SYNC_INTERVAL = 2 * 60 * 1000L

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
