package com.freshnow.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.freshnow.app.R

/**
 * @param dynamicColor 壁纸动态取色。默认关闭：开启时 Android 12+ 上所有强调色都来自壁纸，
 * 应用等于没有自己的配色；而且「还剩几天 / 已过期」这类状态色也会跟着壁纸变，可预期性差。
 * 保留这个开关是为了将来把它做成设置里的一项，而不是留一段死代码。
 */
@Composable
fun FreshNowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 页面底色与启动图、窗口底色共用 @color/window_background 这一个资源。
    // 方向只能是这个：XML 主题在 Compose 之前就被系统解析，读不到 colorScheme；反过来才行。
    // 动态取色开着时其余角色仍随壁纸，只有页面底色固定——启动图无法在运行时算色，只能这样对齐。
    val windowBackground = colorResource(R.color.window_background)

    MaterialTheme(
        colorScheme = scheme.copy(background = windowBackground),
        typography = Typography,
        content = content
    )
}
