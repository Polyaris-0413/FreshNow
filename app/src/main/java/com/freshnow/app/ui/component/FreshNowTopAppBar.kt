package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 全应用统一顶栏。
 *
 * 标题是插槽而不是字符串：选中计数那类内容需要在标题这个位置自己做动画（见 HomeScreen），
 * 收成一个 String 就把这个位置锁死了。M3 的 TopAppBar 本身也是插槽。
 * TopAppBar 会给标题位置套上标题样式，所以调用方直接放 Text 即可，不必自己写 style。
 *
 * 颜色分层只有一个触发器：**内容滚到顶栏下面**。M3 顶栏规范（m3.material.io/components/
 * app-bars/specs 的 Color 一节）写的是「All app bars share the same color roles. On scroll,
 * the container changes color to surface container」，对应 material3 1.4.0 的
 * tokens/AppBarTokens.kt：ContainerColor = Surface、ContainerElevation = Level0、
 * OnScrollContainerColor = SurfaceContainer、OnScrollContainerElevation = Level2。
 * 所以静止态与页面同色（background 与 surface 在 M3 色板里是同一个值），滚动时渐变成
 * surfaceContainer，把顶栏从内容里分出来。常驻分层的角色是导航面（NavigationBarTokens.ContainerColor
 * = SurfaceContainer），不是顶栏。
 *
 * 【曾经刻意不分层，那条理由已作废】早先所有跳转都是整页淡化，两个页面的容器一旦有色差，
 * 半透明期间就会露出随动画移动的暗带。现在子页跳转走 shared axis 纯位移（两页都实心，色差
 * 无害），只有顶层平行页之间还是 fade through；那一组残留的表现是转场那 90ms 里顶栏的一次
 * 色调过渡，属于这套分层方案的固有代价，不是缺陷。
 *
 * 两种颜色都从设计源取（colorScheme 的语义角色），不另写色值；scrolledContainerColor 显式给
 * 出来——虽然恰好等于 token 默认值，但「滚动时用哪一档容器色」是本组件的设计决策，
 * 不该随 material3 版本的默认值漂移。
 *
 * @param scrollBehavior 由页面的 Scaffold 提供、由它挂到 nestedScroll 上（见 [FreshNowSubPage]、
 * HomeScreen），顶栏本身只消费它的状态。为 null 时顶栏不随内容滚动，也就永远停在静止态配色
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshNowTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        scrollBehavior = scrollBehavior
    )
}
