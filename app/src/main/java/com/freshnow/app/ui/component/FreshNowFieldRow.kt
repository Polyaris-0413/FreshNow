package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 「标签 + 值」单行展示，空值统一显示为「未知」。
 *
 * 值的字阶取 Body Large，标签取 Body Medium + onSurfaceVariant，与 M3 列表项的主/次要文本分工一致：
 * 识别结果是主内容，字段名是次要内容。行高由上下各 16dp 内边距加正文行高构成，即 M3 单行列表项的 56dp。
 */
@Composable
fun FreshNowFieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
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
        Text(
            text = value.ifBlank { stringResource(R.string.scan_value_unknown) },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
