package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R

/**
 * 「标签 + 值」两行展示，空值统一显示为「未知」
 */
@Composable
fun FreshNowFieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { stringResource(R.string.scan_value_unknown) },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
