package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 一次扫描的三项结果（生产日期 / 过期日期 / 保质期）。
 *
 * 三项结果成组，按 M3 卡片（containment）装在一个填充卡片里，项间用 M3 分隔线分开；
 * 过期日期优先用标签上的印刷值，没有印刷值才由程序按生产日期 + 保质期推算。
 */
@Composable
fun FreshNowDateFields(
    productionDate: String,
    expiry: ExpiryOutcome,
    shelfLife: String,
    modifier: Modifier = Modifier
) {
    val expiryText = when (expiry) {
        is ExpiryOutcome.Resolved -> expiry.date
        ExpiryOutcome.UnparseableShelfLife -> stringResource(R.string.scan_expiry_unparseable)
        ExpiryOutcome.InsufficientInput -> ""
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        FreshNowFieldRow(
            label = stringResource(R.string.scan_production_date),
            value = productionDate,
            modifier = Modifier.padding(horizontal = FreshNowSpacing.sm)
        )
        FieldDivider()
        FreshNowFieldRow(
            label = stringResource(R.string.scan_expiry_date),
            value = expiryText,
            modifier = Modifier.padding(horizontal = FreshNowSpacing.sm)
        )
        FieldDivider()
        FreshNowFieldRow(
            label = stringResource(R.string.scan_shelf_life),
            value = shelfLife,
            modifier = Modifier.padding(horizontal = FreshNowSpacing.sm)
        )
    }
}

/**
 * 分隔线取两端内缩，与卡片内文字左右对齐；颜色用组件默认值（M3 的 outline-variant）
 */
@Composable
private fun FieldDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = FreshNowSpacing.sm))
}
