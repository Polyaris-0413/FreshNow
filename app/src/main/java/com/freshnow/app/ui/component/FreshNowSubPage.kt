package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R

/**
 * 二级页面骨架：顶栏 + 返回键，页面内容由 content 填充。
 * [actions] 是顶栏动作位（如编辑页的「保存」），默认没有动作
 *
 * 【内容的约定】顶栏是全应用统一的常驻分层底色（见 [FreshNowTopAppBar]），与滚动无关。
 * 内容仍建议**铺满整个 Scaffold**、把 top inset 交给自己的内边距，这样滚动内容会在顶栏下沿
 * 被裁切，而不是在顶栏上停出一条空白带：
 * - LazyColumn 一类的页：`contentPadding = innerPadding`
 * - Column + verticalScroll 的页：`Modifier.verticalScrollUnderTopBar(innerPadding)`
 * - 不滚动的页（裁剪、拍照、扫描预览这类固定布局）：照旧 `padding(innerPadding)` 即可，
 *   内容本来就不会跑到顶栏后面
 */
@Composable
fun FreshNowSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FreshNowTopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = actions
            )
        },
        content = content
    )
}
