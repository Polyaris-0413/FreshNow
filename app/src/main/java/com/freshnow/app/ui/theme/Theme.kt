package com.freshnow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.freshnow.app.R

/**
 * 主题只有深浅两套，不提供壁纸动态取色。
 *
 * 不把它做成设置里的一项：开启后 Android 12+ 上所有强调色都来自壁纸，应用等于没有自己的配色；
 * 而且「还剩几天 / 已过期 / 快过期」这类状态色也会跟着壁纸变，可预期性差。要加回来得连状态色
 * 一起重新设计（它们的可读性与语义都靠固定配色撑着），不是多一个开关的事，所以参数也不预留。
 */
@Composable
fun FreshNowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 页面底色与启动图、窗口底色共用 @color/window_background 这一个资源。
    // 方向只能是这个：XML 主题在 Compose 之前就被系统解析，读不到 colorScheme；反过来才行。
    val windowBackground = colorResource(R.color.window_background)

    MaterialTheme(
        colorScheme = scheme.copy(background = windowBackground),
        typography = Typography
    ) {
        ProvideWarningColors(darkTheme, content)
    }
}
