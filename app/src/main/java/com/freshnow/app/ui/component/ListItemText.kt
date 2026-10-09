package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 列表项的两行文字：一行标题加一行说明。
 *
 * 不把说明交给 ListItem 的 `supportingContent`，而是并进标题这一段：M3 的 ListItem 在说明
 * 折行时会**把整项当成三行**（它的判据是说明超过 30sp 就算多行），而三行布局里的图标与尾部内容
 * 都是**顶对齐**的。对「一行标题 + 一行说明，只是屏幕窄或字号大时折了行」来说，那看起来就是
 * 图标莫名其妙地偏上，而这一项本来只有两行。
 *
 * 并成一段之后，ListItem 只看到一行内容，走的是两行以内的布局，图标与尾部内容才会垂直居中；
 * 说明也照常折行完整显示，不会被省略号截掉。
 *
 * 字号与颜色跟 ListItem 原本给 supportingContent 的那套一致（BodyMedium + onSurfaceVariant），
 * 所以换成这里之后视觉上看不出差别。配色另有含义时（比如列表里那三档过期预警）用
 * [supportingColor] 传进来，不要为了一行变色又把说明拆回去。
 *
 * [trailingLabel] 是跟在标题后面的小字标注（比如标一个功能还在测试）。
 * 用文字而不是 Badge 或 Chip：M3 的 Badge 是贴在图标上的计数，Chip 的四种变体
 * 都是给用户操作的东西，两者用在这里都是语义错位；而 M3 本来也没给「标注功能成熟度」
 * 留组件的位置。灰字跟在标题后既不唐突，也不会让人以为能点。
 */
@Composable
fun ListItemText(
    headline: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingLabel: String? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 字号沿用 ListItem 给标题的那一套，不在这里另写
            Text(text = headline)
            if (!trailingLabel.isNullOrEmpty()) {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = FreshNowSpacing.xs)
                )
            }
        }
        if (!supporting.isNullOrEmpty()) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = supportingColor
            )
        }
    }
}
