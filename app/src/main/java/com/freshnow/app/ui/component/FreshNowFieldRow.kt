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
 * 「标签 + 值」单行展示，空值统一显示为「未知」。
 *
 * 值的字阶取 Body Large，标签取 Body Medium + onSurfaceVariant，与 M3 列表项的主/次要文本分工一致：
 * 识别结果是主内容，字段名是次要内容。行高由上下各 16dp 内边距加正文行高构成，即 M3 单行列表项的 56dp。
 *
 * 值换掉时新旧两段交叉淡化，宽度一并跟着动（「未知」与一个日期差得远，不跟就会在行内跳一下）。
 *
 * 【不要给这块加底色回执】试过「值到达时那一块亮一下主色」：一是同一屏里几块各闪各的，颜色太乱；
 * 二是它与这里的淡化重复表达同一件事——值换了本来就看得出。真机上拿掉之后反而更干净。
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
 * 扫描值的统一显示规则：空值表示这一项没读到，显示为「未知」
 */
@Composable
fun scanValueText(value: String): String = value.ifBlank { stringResource(R.string.scan_value_unknown) }
