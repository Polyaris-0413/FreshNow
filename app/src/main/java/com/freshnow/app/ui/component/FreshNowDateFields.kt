package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 一次扫描的三项结果（生产日期 / 过期日期 / 保质期）。
 *
 * 三项拼接展示：首尾两块朝向页面外的一侧取大圆角、朝向中间的一侧取小圆角，中间一块四角都用小圆角，
 * 块之间留出间距，不使用分隔线。圆角只引用 M3 形状令牌（MaterialTheme.shapes），不另定义数值。
 *
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
    val largeCorner = MaterialTheme.shapes.large
    val smallCorner = MaterialTheme.shapes.small

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xs)
    ) {
        FieldSegment(shape = splicedShape(top = largeCorner, bottom = smallCorner)) {
            FreshNowFieldRow(
                label = stringResource(R.string.scan_production_date),
                value = productionDate
            )
        }
        FieldSegment(shape = smallCorner) {
            FreshNowFieldRow(
                label = stringResource(R.string.scan_expiry_date),
                value = expiryText
            )
        }
        FieldSegment(shape = splicedShape(top = smallCorner, bottom = largeCorner)) {
            FreshNowFieldRow(
                label = stringResource(R.string.scan_shelf_life),
                value = shelfLife
            )
        }
    }
}

/**
 * 上下各取一档形状令牌，拼成一个四角圆角矩形
 */
private fun splicedShape(top: CornerBasedShape, bottom: CornerBasedShape) = RoundedCornerShape(
    topStart = top.topStart,
    topEnd = top.topEnd,
    bottomStart = bottom.bottomStart,
    bottomEnd = bottom.bottomEnd
)

/**
 * 拼接中的单块：填充色与 M3 填充容器一致，左右内边距由容器给出，行本身只负责上下内边距
 */
@Composable
private fun FieldSegment(
    shape: Shape,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(modifier = Modifier.padding(horizontal = FreshNowSpacing.sm)) {
            content()
        }
    }
}
