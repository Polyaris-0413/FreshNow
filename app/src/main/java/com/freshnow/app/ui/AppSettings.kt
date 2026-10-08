package com.freshnow.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 打开本应用在系统设置里的详情页。
 *
 * 用途是权限被永久拒绝之后：那时系统弹窗已经不出现了，再调用一次请求不会有任何反应，
 * 用户只能自己去设置页开权限（见 Android 的 shouldShowRequestPermissionRationale 判据）。
 */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    )
}
