package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R

/**
 * 二级页面骨架：顶栏 + 返回键，页面内容由 content 填充。
 * [actions] 是顶栏动作位（如编辑页的「保存」），默认没有动作
 *
 * 【内容的约定】顶栏只在内容滚到它下面时变色（见 [FreshNowTopAppBar]），所以 content 里的
 * 滚动容器必须**铺满整个 Scaffold**、把 top inset 交给自己的内边距，而不是用
 * `Modifier.padding(innerPadding)` 把视口整体推下去：
 * - LazyColumn 一类的页：`contentPadding = innerPadding`
 * - Column + verticalScroll 的页：`Modifier.verticalScrollUnderTopBar(innerPadding)`
 * - 不滚动的页（裁剪、拍照、扫描预览这类固定布局）：照旧 `padding(innerPadding)` 即可，
 *   它们永远等不到滚动量，顶栏也就停在静止态配色——这不是遗漏，是 M3 的语义
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshNowSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit = {}
) {
    // pinned：顶栏留在原地、不收起，滚动改变的只是它的底色——本应用要的是「看得见边界」，
    // 不是「腾出高度」。官方 PinnedTopAppBar 示例就是 pinned + nestedScroll 这一对
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                actions = actions,
                scrollBehavior = scrollBehavior
            )
        },
        content = content
    )
}
