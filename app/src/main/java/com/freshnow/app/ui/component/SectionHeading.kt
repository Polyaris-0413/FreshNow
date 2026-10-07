package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 分区标题：M3 无专门组件，采用 Settings 惯例的 Title Small + primary。
 *
 * 左右留白写在这里，与上下留白分开：上下间距随场景不同（页首、段间、页内小节），调用方用
 * modifier 给，左右对齐则各处必须一致——标题与它下面的行要朝同一条边对齐。
 */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(
            start = FreshNowSpacing.sm,
            end = FreshNowSpacing.sm,
            bottom = FreshNowSpacing.xxs
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
