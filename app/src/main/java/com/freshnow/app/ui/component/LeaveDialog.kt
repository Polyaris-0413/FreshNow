package com.freshnow.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R

/**
 * 「这次的内容还没存就要走」的那一问。
 * 扫描页与编辑页（含手动输入）问的是同一件事，差别只在文案（编辑页有两个名字，所以只说「此页」）
 * 以及保存能不能按下：编辑页一项都填不出来、或日期认不出时那颗是灰的。
 * 合成一个组件而不是各写一份：动作最多两个、确认位是「保存」、退出位是「不保存」这类规矩，
 * 分两处写迟早会各漂各的。
 */
@Composable
internal fun LeaveDialog(
    title: String,
    message: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    canSave: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        // 正文说清这一问的代价。只有标题的话，标题下面空出一块，对话框显得又扁又空；
        // 主页那个删除对话框同样带正文，两者就此一致
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(text = stringResource(R.string.action_discard))
            }
        }
    )
}
