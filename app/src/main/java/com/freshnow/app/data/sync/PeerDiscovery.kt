package com.freshnow.app.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 局域网里发现的一台装了本应用的设备 */
internal data class DiscoveredPeer(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int
) {
    val address: String get() = "$host:$port"
}

/**
 * 用系统自带的 mDNS（NsdManager）互相发现，而不是自己发 UDP 广播。
 *
 * 广播要自己定报文格式、自己处理重发与去重，且在很多路由器上组播被直接过滤，出问题时用户
 * 看不出区别，只能一直等；NsdManager 是系统服务，同样的网络条件下能不能通，行为至少是可预期的。
 * 代价是它有自己的坑（见下），所以上层始终留着「手动填地址」这条路。
 *
 * 服务名用设备号的前 8 位而不是设备名：设备名可能两台一样（同型号的手机默认名相同），
 * 而 mDNS 里重名的服务会被自动改名（FreshNow-pixel → FreshNow-pixel (2)），
 * 用户看到两个自己都认不出来的名字。
 */
internal class PeerDiscovery(context: Context) {

    private val nsdManager: NsdManager? = context.getSystemService(NsdManager::class.java)
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(WifiManager::class.java)

    private var registration: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** 把自己播出去。端口是本机服务端实际监听的端口 */
    suspend fun advertise(deviceId: String, deviceName: String, port: Int) {
        val manager = nsdManager ?: return
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "FreshNow-${deviceId.take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(ATTR_DEVICE_ID, deviceId)
            setAttribute(ATTR_DEVICE_NAME, deviceName)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        suspendCancellableCoroutine { continuation ->
            runCatching {
                manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure { continuation.resume(Unit) }
            continuation.invokeOnCancellation { stopAdvertising() }
        }
    }

    fun stopAdvertising() {
        registration?.let { runCatching { nsdManager?.unregisterService(it) } }
        registration = null
    }

    /**
     * 持续报告局域网里有哪些设备。
     *
     * 拿不到组播锁时有些机型收不到 mDNS 报文（组播会被省电策略掐掉），所以这里一并持锁；
     * 锁的代价是它按引用计数泄漏，获取与释放必须成对，[awaitClose] 里释放是为了让调用方
     * 取消收集时也能还回去。
     */
    fun discover(): Flow<List<DiscoveredPeer>> = callbackFlow {
        val manager = nsdManager
        if (manager == null) {
            close()
            return@callbackFlow
        }
        val found = LinkedHashMap<String, DiscoveredPeer>()

        fun publish() = trySend(found.values.toList())

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // 解析是异步的，而且必须一台一台来：NsdManager 对同一时刻的解析请求数量有限，
                // 并发发起时后面的会失败，表现是「有时能发现有时不能」
                launch {
                    val peer = resolve(serviceInfo) ?: return@launch
                    // 自己也会被自己的服务找到，跳过它
                    found[peer.deviceId] = peer
                    publish()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
                publish()
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        acquireMulticastLock()
        // 同一个类型上只能有一个进行中的发现，重复发起会直接失败，所以先停一次
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure {
                releaseMulticastLock()
                close()
                return@callbackFlow
            }

        publish()

        awaitClose {
            runCatching { manager.stopServiceDiscovery(listener) }
            releaseMulticastLock()
        }
    }

    private suspend fun resolve(serviceInfo: NsdServiceInfo): DiscoveredPeer? =
        suspendCancellableCoroutine { continuation ->
            val manager = nsdManager
            if (manager == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) =
                    continuation.resume(null)

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val deviceId = resolved.attributes[ATTR_DEVICE_ID]?.toString(Charsets.UTF_8)
                    val host = resolved.host?.hostAddress
                    if (deviceId.isNullOrEmpty() || host.isNullOrEmpty()) {
                        continuation.resume(null)
                        return
                    }
                    continuation.resume(
                        DiscoveredPeer(
                            deviceId = deviceId,
                            deviceName = resolved.attributes[ATTR_DEVICE_NAME]
                                ?.toString(Charsets.UTF_8)
                                .orEmpty(),
                            host = host,
                            port = resolved.port
                        )
                    )
                }
            }
            // resolveService 在 API 34 起标记为废弃（推荐 registerServiceInfoCallback），
            // 但它在所有本应用支持的版本上仍然工作，而新接口要求 API 34 且要自己管 Executor——
            // 为一个只在发现阶段用一次的调用同时维护两条路径，出问题时更难查
            @Suppress("DEPRECATION")
            runCatching { manager.resolveService(serviceInfo, listener) }
                .onFailure { continuation.resume(null) }
        }

    private fun acquireMulticastLock() {
        releaseMulticastLock()
        multicastLock = runCatching {
            wifiManager?.createMulticastLock("freshnow-sync")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
    }

    private companion object {
        const val SERVICE_TYPE = "_freshnow._tcp."
        const val ATTR_DEVICE_ID = "deviceId"
        const val ATTR_DEVICE_NAME = "deviceName"
    }
}
