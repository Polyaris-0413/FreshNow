package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 全应用统一顶栏。
 *
 * 标题是插槽而不是字符串：选中计数那类内容需要在标题这个位置自己做动画（见 HomeScreen），
 * 收成一个 String 就把这个位置锁死了。M3 的 TopAppBar 本身也是插槽。
 * TopAppBar 会给标题位置套上标题样式，所以调用方直接放 Text 即可，不必自己写 style。
 *
 * 底色一律取 surfaceContainer —— 全应用所有顶栏都分层，不看滚动状态。
 *
 * M3 给顶栏规定的是「静止 surface、内容滚到它下面时切 surfaceContainer」
 * （m3.material.io/components/app-bars/specs：All app bars share the same color roles.
 * On scroll, the container changes color to surface container；token 见 material3 的
 * tokens/AppBarTokens.kt 的 ContainerColor / OnScrollContainerColor）。本应用刻意不跟滚动走：
 * 常驻分层的角色在 M3 里是导航面（NavigationBarTokens.ContainerColor = SurfaceContainer），
 * 拿到顶栏上来用，换来的是「每一页的边界都看得见」——不必让用户去猜哪一页的内容够长、
 * 也不会有「滚到底部时底色忽然退回」这种状态起落。
 *
 * 【代价，已知并接受】顶栏不再表达「内容是否滚到它下面」这个状态；同一份颜色同时用于有滚动
 * 与无滚动的页面。要恢复成跟滚动走，得给 Scaffold 接 pinnedScrollBehavior + nestedScroll，
 * 并把内容的内边距交给滚动容器（见 FreshNowSubPage 的说明）。
 *
 * 色值来自设计源（colorScheme 的语义角色），不另写色值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshNowTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
