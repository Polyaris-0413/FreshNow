package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 分区标题：M3 无专门组件，采用 Settings 惯例的 Title Small + primary。
 *
 * **左右留白不在本组件里**，由调用方那层给（页面内容区的页边距，或者它自己的 modifier）。
 * 理由是标题该对齐的是「它下面那块东西的边」，而那块东西在各页不同：
 *
 * - 下面是一块拼接容器时（设置页、关于页），对齐到容器的边——那层页边距已经把容器推到 16dp，
 *   标题跟着落到同一个位置，不要再自己加一档，否则卡片左边会多出一块无主的空白
 * - 下面是平铺的行时（同步页），对齐到行的文字——行自己带 16dp 内边距，标题也得补上同一档
 *
 * 上下间距本来就是调用方用 modifier 给（页首、段间、页内小节各不相同），这一档跟着它们一起。
 * [textAlign] 给欢迎页那种整体居中的版式：调用方传 [TextAlign.Center] 并给 [Modifier.fillMaxWidth]，
 * 居中才有余地。
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = FreshNowSpacing.xxs),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = textAlign
    )
}
