package com.freshnow.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTransitions

/**
 * 空值统一显示为「未知」。
 * 值的字阶取 Body Large、标签 Body Medium + onSurfaceVariant，与 M3 列表项的主/次要文本分工一致；
 * 行高为 M3 单行列表项的 56dp（上下各 16dp 内边距加正文行高）。
 * 值换掉时新旧两段交叉淡化，宽度一并跟动（「未知」与一个日期差得远）。
 * 【不要给这块加底色回执】试过「值到达时亮一下主色」：同一屏几块各闪各的，且与淡化重复表达同一件事。
 */
@Composable
fun FreshNowFieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FreshNowSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedContent(
            targetState = scanValueText(value),
            transitionSpec = { FreshNowTransitions.fadeSwapAnimatedSize() },
            label = "fieldValue"
        ) { current ->
            Text(text = current, style = valueStyle)
        }
    }
}

/**
 * 空值表示这一项没读到，显示为「未知」
 */
@Composable
fun scanValueText(value: String): String = value.ifBlank { stringResource(R.string.scan_value_unknown) }
