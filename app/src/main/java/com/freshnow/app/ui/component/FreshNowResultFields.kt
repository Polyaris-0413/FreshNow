package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 一次扫描的结果：品名单独成块放在最上面，下面三项日期与保质期拼接成一组。
 *
 * 品名不与日期组相连（两块之间的间距大于日期组内部的间距），但块内排法与日期三项一致，
 * 都是标签在左、值在右；只有值的字阶用 titleMedium 略作强调，因为品名是这组结果的主语。
 *
 * 日期组三块拼接展示：首尾两块朝向页面外的一侧取大圆角、朝向中间的一侧取小圆角，中间一块四角都用小圆角。
 * 外圈取规范给卡片类容器的 medium，与页面上其他圆角容器保持一致；内圈取最小档 extraSmall，
 * 让拼接的「外圆内方」更清晰。圆角只引用形状令牌，不另定义数值。
 *
 * 过期日期优先用标签上的印刷值，没有印刷值才由程序按生产日期 + 保质期推算。
 */
@Composable
fun FreshNowResultFields(
    productName: String,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        ProductNameBlock(productName)

        // 顺序按推算顺序排：生产日期 + 保质期 是标签上印的两项，过期日期是二者算出来的结论，故排在最后，
        // 使「生产 + 保质期 → 过期」的顺序可以直接核对。
        // 三块拼成一组：形状由 splicedCardShape 按位置给出（见 SplicedCard），缝取 4dp
        Column(verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxs)) {
            FieldSegment(shape = splicedCardShape(index = 0, count = FIELD_COUNT)) {
                FreshNowFieldRow(
                    label = stringResource(R.string.scan_production_date),
                    value = productionDate
                )
            }
            FieldSegment(shape = splicedCardShape(index = 1, count = FIELD_COUNT)) {
                FreshNowFieldRow(
                    label = stringResource(R.string.scan_shelf_life),
                    value = shelfLife
                )
            }
            FieldSegment(shape = splicedCardShape(index = 2, count = FIELD_COUNT)) {
                FreshNowFieldRow(
                    label = stringResource(R.string.scan_expiry_date),
                    value = expiryText
                )
            }
        }
    }
}

/** 拼成一组的三块日期与保质期 */
private const val FIELD_COUNT = 3

/**
 * 品名块：单独一张卡片，四角都取规范给卡片类容器的 medium
 */
@Composable
private fun ProductNameBlock(productName: String, modifier: Modifier = Modifier) {
    FieldSegment(shape = splicedCardShape(index = 0, count = 1), modifier = modifier) {
        FreshNowFieldRow(
            label = stringResource(R.string.scan_product_name),
            value = productName,
            valueStyle = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * 拼接中的一块字段行：形状来自 [splicedCardShape]，左右留白由这里补——[FreshNowFieldRow]
 * 自身只有上下内边距（它同时被用在不需要左右留白的地方）
 */
@Composable
private fun FieldSegment(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SplicedCard(shape = shape, modifier = modifier) {
        Box(modifier = Modifier.padding(horizontal = FreshNowSpacing.sm)) {
            content()
        }
    }
}
