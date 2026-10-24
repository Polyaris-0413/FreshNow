package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.data.UpdateSource

/**
 * 设置页「更新源」的单选内容。与 [SortOrderMenuItems] 同一套做法：
 * 选项文案只有 [label] 一处来源，当前项也交给这里标出，行上的说明文字与面板里的选项才不会对不上。
 */
@Composable
fun ColumnScope.UpdateSourceMenuItems(
    current: UpdateSource,
    onSelect: (UpdateSource) -> Unit,
    modifier: Modifier = Modifier
) {
    UpdateSource.entries.forEach { source ->
        MenuSheetItem(
            text = source.label(),
            selected = source == current,
            onClick = { onSelect(source) },
            modifier = modifier
        )
    }
}

/** 落盘的是枚举名，用户看到的说法只有这一处来源 */
@Composable
fun UpdateSource.label(): String = stringResource(
    when (this) {
        UpdateSource.GITHUB -> R.string.update_source_github
        UpdateSource.GITEE -> R.string.update_source_gitee
    }
)
