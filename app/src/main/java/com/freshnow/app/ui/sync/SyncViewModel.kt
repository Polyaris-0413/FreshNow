package com.freshnow.app.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.local.SyncPeer
import com.freshnow.app.data.sync.DiscoveredPeer
import com.freshnow.app.data.sync.SyncCoordinator
import com.freshnow.app.data.sync.SyncReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = SyncCoordinator.getInstance(application)

    val deviceName: String get() = coordinator.deviceName

    val pairingCode: StateFlow<String?> = coordinator.pairingCode

    val discovered: StateFlow<List<DiscoveredPeer>> = coordinator.discovered

    val syncing: StateFlow<Boolean> = coordinator.syncing

    /**
     * 已配对设备。订阅停止后留一段缓存：进出本页时列表不该闪一下空白。
     */
    val peers: StateFlow<List<SyncPeer>> = coordinator.pairedPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private val _message = MutableStateFlow<SyncMessage?>(null)

    /** 上一次操作的结果，界面报一句话之后由 [consumeMessage] 清掉 */
    val message: StateFlow<SyncMessage?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun openPairingCode() {
        coordinator.openPairingWindow()
    }

    fun closePairingCode() = coordinator.closePairingWindow()

    fun startDiscovery() = coordinator.startDiscovery()

    fun stopDiscovery() = coordinator.stopDiscovery()

    fun forget(deviceId: String) {
        viewModelScope.launch { coordinator.forget(deviceId) }
    }

    fun syncNow() {
        viewModelScope.launch {
            when (val report = coordinator.syncNow()) {
                SyncReport.NoPeers -> _message.value = SyncMessage.NoPeers
                SyncReport.Unreachable -> _message.value = SyncMessage.Unreachable
                is SyncReport.Done -> _message.value = SyncMessage.Synced(
                    peerName = report.outcomes.first().peerName,
                    applied = report.outcomes.sumOf { it.applied }
                )
            }
        }
    }

    fun pair(peer: DiscoveredPeer, code: String) {
        viewModelScope.launch {
            val paired = coordinator.pairWith(peer, code)
            _message.value = if (paired) {
                SyncMessage.Paired(peer.deviceName.ifBlank { peer.deviceId.take(8) })
            } else {
                SyncMessage.PairFailed
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

/** 一次操作的结果。带着参数而不是拼好的字符串，文案由界面那一侧按当前语言取 */
internal sealed interface SyncMessage {
    data class Paired(val deviceName: String) : SyncMessage
    data object PairFailed : SyncMessage
    data class Synced(val peerName: String, val applied: Int) : SyncMessage
    data object Unreachable : SyncMessage
    data object NoPeers : SyncMessage
}
