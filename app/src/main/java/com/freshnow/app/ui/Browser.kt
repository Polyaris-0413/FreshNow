package com.freshnow.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 用系统浏览器打开一个链接。
 *
 * 起不来的情况（设备上没有浏览器、或链接被系统策略拦掉）不抛给调用方：这是「点一下看看」
 * 的辅助动作，失败时崩掉整个应用不成比例。调用方也无从补救——缺的是浏览器，不是权限。
 */
fun openInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
