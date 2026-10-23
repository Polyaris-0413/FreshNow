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
 * containerColor 刻意与页面背景同色（background），不另设色调分层：
 * 页面切换带淡入淡出，两个页面的容器只要存在色差，半透明期间就会露出底色差，
 * 表现为顶栏上一条随动画移动的暗带，且无法靠调参消除。
 * 若今后要重新加回分层，需同时放弃淡化（改为纯位移）。
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
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
