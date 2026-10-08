package com.freshnow.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R

/**
 * 发现新版本：告知版本号，给一条去发布页的路。
 *
 * 冷启动自动检查与关于页的手动检查共用这一个对话框，两处的差别只在「关闭」之后做什么
 * （自动检查记住该版本，手动检查不记，见各自的调用处）。
 *
 * 应用内不做下载与安装：那要申请「安装未知应用」权限、还得处理下载失败与校验，而发布页本身
 * 就能下——一个只有单一产物的应用，把这条路留给浏览器更省事，也不用为此多要一项权限。
 */
@Composable
fun UpdateDialog(
    version: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.update_found_title)) },
        // 正文带上版本号：用户要判断的是「这个版本值不值得去下载」，光说「有新版本」等于没说
        text = { Text(text = stringResource(R.string.update_found_message, version)) },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(text = stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.update_close))
            }
        }
    )
}
