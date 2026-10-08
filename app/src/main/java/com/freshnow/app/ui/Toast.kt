package com.freshnow.app.ui

import android.content.Context
import android.widget.Toast

/**
 * 短提示。
 *
 * 用 Toast 而不是 Snackbar：全应用没有一处 SnackbarHost，而这些提示都不承载可撤销的操作——
 * 为一句「已复制」在每个页面各搭一套 Snackbar 宿主不成比例。
 */
fun showToast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
