package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 品名不与日期组相连（两块之间的间距大于日期组内部的间距），但块内排法与日期三项一致，都是标签在左、值在右；
 * 只有值的字阶用 titleMedium 略作强调，因为品名是这组结果的主语。
 * [location] 非 null 时（详情页）品名与位置拼成一组：品名回答「这是什么」，位置回答「它在哪」，
 * 而日期那三块是一条推算链（生产日期 + 保质期 → 过期日期），位置插在中间会把链子打断。
 * 为 null 时（扫描页）只画品名一块，四角都取大圆角——位置永远不是模型读到的（见 ScanRecord.location）。
 * 日期组三块拼接展示：首尾两块朝外的一侧取大圆角、相接的一侧取小圆角，中间一块四角都用小圆角。
 * 外圈取卡片类容器的 medium，内圈取最小档 extraSmall，拼接的「外圆内方」才读得出来。
 * 块之间的缝取 [FreshNowSpacing.xxxs]（M3 分段列表给同组填充块之间留的缝宽）；圆角只引用形状令牌。
 * 过期日期优先用标签上的印刷值，没有印刷值才按生产日期 + 保质期推算。
 */
@Composable
fun FreshNowResultFields(
    productName: String,
    productionDate: String,
    expiry: ExpiryOutcome,
    shelfLife: String,
    location: String? = null,
    modifier: Modifier = Modifier
) {
    val expiryText = when (expiry) {
        is ExpiryOutcome.Resolved -> expiry.date
        ExpiryOutcome.UnparseableShelfLife -> stringResource(R.string.scan_expiry_unparseable)
        ExpiryOutcome.InsufficientInput -> ""
    }
    val outerCorner = MaterialTheme.shapes.medium
    val innerCorner = MaterialTheme.shapes.extraSmall

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        ProductNameBlock(productName, location)

        // 顺序按推算顺序排：生产日期 + 保质期 是标签上印的两项，过期日期是二者算出来的结论，故排在最后，
        // 使「生产 + 保质期 → 过期」的顺序可以直接核对
        Column(verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)) {
            FieldSegment(shape = splicedShape(top = outerCorner, bottom = innerCorner)) {
                FreshNowFieldRow(
                    label = stringResource(R.string.scan_production_date),
                    value = productionDate
                )
            }
            FieldSegment(shape = innerCorner) {
                FreshNowFieldRow(
                    label = stringResource(R.string.scan_shelf_life),
                    value = shelfLife
                )
            }
            FieldSegment(shape = splicedShape(top = innerCorner, bottom = outerCorner)) {
                FreshNowFieldRow(
                    label = stringResource(R.string.scan_expiry_date),
                    value = expiryText
                )
            }
        }
    }
}

/**
 * [location] 为 null 时只有品名一块，四角都取规范给卡片类容器的 medium；
 * 非 null 时两块拼成一组，朝外的一侧取大圆角、相接的一侧取小圆角。
 */
@Composable
private fun ProductNameBlock(
    productName: String,
    location: String?,
    modifier: Modifier = Modifier
) {
    val outerCorner = MaterialTheme.shapes.medium
    val innerCorner = MaterialTheme.shapes.extraSmall

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)
    ) {
        FieldSegment(
            shape = if (location == null) outerCorner else splicedShape(top = outerCorner, bottom = innerCorner)
        ) {
            FreshNowFieldRow(
                label = stringResource(R.string.scan_product_name),
                value = productName,
                valueStyle = MaterialTheme.typography.titleMedium
            )
        }

        if (location != null) {
            FieldSegment(shape = splicedShape(top = innerCorner, bottom = outerCorner)) {
                FreshNowFieldRow(
                    label = stringResource(R.string.record_location),
                    value = scanValueText(location)
                )
            }
        }
    }
}

/**
 * 填充色与 M3 填充容器一致，左右内边距由容器给出，行本身只负责上下内边距
 */
@Composable
private fun FieldSegment(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(modifier = Modifier.padding(horizontal = FreshNowSpacing.sm)) {
            content()
        }
    }
}
