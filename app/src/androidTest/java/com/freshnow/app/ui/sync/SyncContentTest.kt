package com.freshnow.app.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.data.local.SyncPeer
import com.freshnow.app.data.sync.DiscoveredPeer
import com.freshnow.app.ui.maxContrast
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 同步页上会自己变的那几处是换装，不是硬切：附近设备从「正在查找」变成一行行设备、已配对从
 * 「还没有配对」变成一行行设备。
 *
 * 判据取像素，不取节点：淡出中的条目已经不在语义树里了（见 PixelProbe 的说明）。取样落在
 * **换掉之前**那句话所在的区域上，所以半程测的是「这一带正在从旧样子变成新样子」——硬切的实现
 * 里，这一拍它已经是新样子了。
 *
 * 这一页原先没有可测的缝：内容与 ViewModel 缠在一起。现在内容吃的是数据与回调（见 SyncContent
 * 的说明），这里直接喂数据即可。
 */
@RunWith(AndroidJUnit4::class)
class SyncContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nearbySearchingToFoundIsASwap() {
        var discovered by mutableStateOf(emptyList<DiscoveredPeer>())
        setContent(discovered = { discovered })

        assertSwap(outgoing = SEARCHING) { discovered = listOf(found(KITCHEN)) }
    }

    @Test
    fun pairedEmptyToDeviceIsASwap() {
        var peers by mutableStateOf(emptyList<SyncPeer>())
        setContent(peers = { peers })

        assertSwap(outgoing = NO_PAIRED) { peers = listOf(paired(STUDY)) }
    }

    /** 附近：正在查找 → 确实没有（两句话之间的换装，与上面同一套，但说的是另一件事） */
    @Test
    fun nearbySearchingToEmptyIsASwap() {
        // 一开始就已经配上 KITCHEN：这样下面那次变化只动「附近」这一块，上面两块不动，
        // 取样的那一带不会被别的位移搅进来
        var discovered by mutableStateOf(emptyList<DiscoveredPeer>())
        val peers = listOf(paired(KITCHEN))
        setContent(discovered = { discovered }, peers = { peers })

        // 发现到的那台已经配对过，所以它不进「附近」，这一块该转成「没有找到设备」
        assertSwap(outgoing = SEARCHING) { discovered = listOf(found(KITCHEN)) }
    }

    private fun assertSwap(outgoing: String, change: () -> Unit) {
        val region = composeRule.onNodeWithText(outgoing).getBoundsInRoot()
        val before = composeRule.maxContrast(region)

        composeRule.mainClock.autoAdvance = false
        change()
        composeRule.mainClock.advanceTimeBy(FADE_MS / 2)
        val middle = composeRule.maxContrast(region)

        composeRule.mainClock.advanceTimeBy(FADE_MS * 3)
        val after = composeRule.maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue(
            "半程应当还在过渡，不是一步到位：before=$before middle=$middle after=$after",
            middle in 1 until after
        )
        // 收尾：旧的那句要走干净
        composeRule.onNodeWithText(outgoing).assertDoesNotExist()
    }

    private fun setContent(
        discovered: () -> List<DiscoveredPeer> = { emptyList() },
        peers: () -> List<SyncPeer> = { emptyList() }
    ) {
        composeRule.setContent {
            FreshNowTheme {
                SyncContent(
                    deviceName = DEVICE_NAME,
                    peers = peers(),
                    discovered = discovered(),
                    permissionGranted = true,
                    permissionBlocked = false,
                    onRequestPermission = {},
                    onShowPairingCode = {},
                    onManualPair = {},
                    onPick = {},
                    onForget = {}
                )
            }
        }
    }

    private fun found(name: String) = DiscoveredPeer(
        deviceId = name,
        deviceName = name,
        host = "10.0.0.2",
        port = 8899
    )

    private fun paired(name: String) = SyncPeer(
        deviceId = name,
        deviceName = name,
        secret = "secret",
        lastAddress = "",
        pairedAt = 0L
    )

    private companion object {
        const val DEVICE_NAME = "本机"
        const val KITCHEN = "厨房那台"
        const val STUDY = "书房那台"
        const val SEARCHING = "正在查找同一网络下的设备…"
        const val NO_PAIRED = "还没有配对的设备"
        /** 与 FreshNowTransitions.stateChange() 同一档 */
        const val FADE_MS = 200L
    }
}
