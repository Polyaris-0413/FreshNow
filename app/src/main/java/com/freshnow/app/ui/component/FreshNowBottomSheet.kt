package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * 本项目所有 M3 底部面板的唯一入口。除了照原样转给 [ModalBottomSheet]，只多做一件事：
 * 让拖动手柄不出现涟漪。
 *
 * 那圈涟漪的形状是错的。手柄本身是一枚 32×4dp 的胶囊，可它外面那圈 48dp 高的触摸区在 Compose
 * 里被 [ModalBottomSheet] 用一层不带形状的 clickable 包着（material3 1.4.0 的
 * ModalBottomSheet.kt 里就是 `Modifier.clickable { … }` 配 `DragHandleWithTooltip`），涟漪于是
 * 按矩形铺开——圆的胶囊配一块方块。而手柄并不需要按压反馈：
 * - M3 规范把面板顶部 48dp 定为触摸区，没有给手柄定义状态层，反馈由面板本身的移动给出；
 * - Google 自己的 View 实现（material-components 的 BottomSheetDragHandleView）也没有涟漪：
 *   48dp 触摸区里只有一枚 32×4dp、圆角 2dp 的 drawable，样式里既无 background 也无状态 selector。
 *
 * 所以直接关掉：给 [LocalRippleConfiguration] 传 null 正是 M3 为这类场合留的开关（见 Ripple.kt
 * 的 KDoc：「disable it entirely by providing null」）。
 *
 * 代价说清楚：涟漪那一层同时还画手柄的焦点态，关掉后键盘/切换控制的焦点指示一并消失（与上面那
 * 个官方 View 实现同等水平）；面板仍可由遮罩、返回键关闭，手柄的 dismiss/expand/collapse 无障碍
 * 动作与长按 tooltip 都不受影响。面板内容必须把默认配置恢复回去，否则里面卡片、按钮的按压反馈
 * 会一起被关掉。
 *
 * 约定由 `BottomSheetDragHandleRippleTest` 守着：别处直接调用 [ModalBottomSheet] 会测试失败，
 * 新增面板自然走这里，不必再记一遍这件事。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshNowBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    // 先取调用方的那份，再在子树里换成 null：调用方可能已经自定义过涟漪
    val callerRippleConfiguration = LocalRippleConfiguration.current

    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = modifier
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides callerRippleConfiguration) {
                content()
            }
        }
    }
}
