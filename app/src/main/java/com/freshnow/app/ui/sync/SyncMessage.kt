package com.freshnow.app.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.data.sync.SyncReport

/**
 * 一次同步操作的结果。带着参数而不是拼好的字符串，文案由界面那一侧按当前语言取。
 *
 * 同步页与主页（下拉刷新）都报这一句话，所以这一套词汇单独放一个文件：两处各拼一遍的话，
 * 同一件事迟早会长出两种说法。
 *
 * 不是 internal：主页那一屏是公开的，它的参数里带着这个类型（与 [com.freshnow.app.ui.home.HomeRecordItem]
 * 同一个道理）。从 [SyncReport] 往这边的转换反而是内部的，外面拿不到 SyncReport。
 */
sealed interface SyncMessage {
    /** [applied] 是配完那次同步从对方拿回来几条，0 表示这次没连上 */
    data class Paired(val deviceName: String, val applied: Int) : SyncMessage
    data object PairFailed : SyncMessage
    data class Synced(val peerName: String, val applied: Int) : SyncMessage
    data object Unreachable : SyncMessage
    data object NoPeers : SyncMessage
}

/**
 * 一次同步（不是配对）的结果。
 *
 * 多台设备时只报第一台的名字、报合起来的条数：用户要知道的是「同步成功了没有、多了几条」，
 * 逐台列出来在一条 Toast 里也读不完。
 */
internal fun SyncReport.toMessage(): SyncMessage = when (this) {
    SyncReport.NoPeers -> SyncMessage.NoPeers
    SyncReport.Unreachable -> SyncMessage.Unreachable
    is SyncReport.Done -> SyncMessage.Synced(
        peerName = outcomes.first().peerName,
        applied = outcomes.sumOf { it.applied }
    )
}

@Composable
internal fun SyncMessage.text(): String = when (this) {
    // 配完那次同步没拿到数据时不说「同步 0 条」：那是句没信息量的话，
    // 而用户刚做完配对，想知道的只是「配上了没有」
    is SyncMessage.Paired -> if (applied > 0) {
        stringResource(R.string.sync_pair_succeeded_with_data, deviceName, applied)
    } else {
        stringResource(R.string.sync_pair_succeeded, deviceName)
    }
    SyncMessage.PairFailed -> stringResource(R.string.sync_pair_failed)
    is SyncMessage.Synced -> stringResource(R.string.sync_result_done, peerName, applied)
    SyncMessage.Unreachable -> stringResource(R.string.sync_result_unreachable)
    SyncMessage.NoPeers -> stringResource(R.string.sync_result_no_peers)
}
