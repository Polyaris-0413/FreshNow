package com.freshnow.app.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * 整页可滚内容：能滚到顶栏下面，而首屏仍从顶栏下方开始。
 *
 * 顶栏分层只在内容滚到它下面时出现（见 [FreshNowTopAppBar]），而「滚到下面」要求滚动视口本身
 * 覆盖顶栏那块地方。所以 Scaffold 给的 innerPadding **不能**用 `Modifier.padding(innerPadding)`
 * 包在外层：那样视口整个被推到顶栏下方，内容滚到边界就停住，顶栏永远等不到滚动量。
 * Scaffold 把内容摆在 (0,0) 再画顶栏（material3 1.4.0 的 Scaffold.kt: bodyContentPlaceable
 * .place(0, 0) 之后才是 topBarPlaceable.place(0, 0)），所以视口铺满是安全的，顶栏会盖在上面。
 *
 * 这个修饰符把 innerPadding 拆成两半：左右与底部留在外层（视口不被导航栏、刘海压住），
 * 顶部挪到 [verticalScroll] 之后，成为滚内容的第一段空白——静止时首项仍从头栏下方开始，
 * 滚起来就从顶栏底下穿过去。
 *
 * LazyColumn 的页面不用它，直接 `contentPadding = innerPadding` 即可，那是同一个意思。
 */
@Composable
fun Modifier.verticalScrollUnderTopBar(
    innerPadding: PaddingValues,
    state: ScrollState = rememberScrollState()
): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return this
        .padding(
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding()
        )
        .verticalScroll(state)
        .padding(top = innerPadding.calculateTopPadding())
}
