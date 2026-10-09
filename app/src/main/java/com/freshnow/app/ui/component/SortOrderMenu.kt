package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.data.SortOrder

/**
 * 排序方式的两张卡：主页顶栏的排序动作与设置页的「排序方式」共用同一份。
 *
 * 同一件事在两个入口长成一个样，连文案都不必各写一遍（[label] 是文案的唯一来源）；
 * 把「当前项」也交给这里标出来，两处才不会一个标一个不标。
 */
@Composable
fun ColumnScope.SortOrderMenuItems(
    current: SortOrder,
    onSelect: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    SortOrder.entries.forEach { order ->
        MenuSheetItem(
            text = order.label(),
            selected = order == current,
            onClick = { onSelect(order) },
            modifier = modifier
        )
    }
}

/** 排序方式在界面上的名字。落盘的是枚举名，用户看到的说法只有这一处来源 */
@Composable
fun SortOrder.label(): String = stringResource(
    when (this) {
        SortOrder.CREATED_AT -> R.string.sort_order_created_at
        SortOrder.EXPIRY_DATE -> R.string.sort_order_expiry_date
    }
)
