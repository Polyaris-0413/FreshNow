package com.freshnow.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.freshnow.app.ui.theme.FreshNowSize

/**
 * 滚动区底边的渐隐：内容被折叠线裁掉时，用渐隐代替一条硬边，提示「下面还有」。
 *
 * 只在 [visible]（即调用方读的 `ScrollState.canScrollForward`）为真时出现：还能往下滚才提示。
 * 用渐隐而不是滚动条：M3 的滚动条组件要 material3 1.5（本项目锁在 1.4.0 稳定线），而渐隐是
 * Google 自己一贯的手法（Android 15 起平台对内容滚到系统栏下方也做同样的边缘渐隐），且它表达的
 * 正是我们缺的那个语义——「内容还在往下延伸」，而不是「滚到了哪儿」。
 *
 * 自身不占布局：叠在滚动区之上，所以调用方要把它放进与滚动区同一个 Box 里、对齐到底部。
 * 不做淡入淡出：它随滚动位置出现/消失，本来就是个瞬时信号，加动画反而多一层状态。
 *
 * 提为 internal 是为了能在仪器化测试里直接断言显隐两态。
 */
@Composable
internal fun ScrollEdgeFade(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FreshNowSize.scrollEdgeFade)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            )
    )
}
