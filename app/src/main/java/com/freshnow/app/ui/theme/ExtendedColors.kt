package com.freshnow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 标准 ColorScheme 之外的扩展角色。
 *
 * M3 没有 warning 角色，但「快过期」需要一个既不是正常、也还不是失败的中间态，
 * 所以按 M3 对「标准角色之外的颜色」的做法，用同一个生成器从另一个 seed 产出这组色
 * （取值在 Color.kt，由 tools/generate-color-scheme.cjs 生成；本文件只管结构与取用方式）。
 */
@Immutable
data class FreshNowWarningColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

private val LocalWarningColors = staticCompositionLocalOf { LightWarningColors }

/**
 * 扩展角色的取用入口，用法与 MaterialTheme.colorScheme 一致：
 * `MaterialTheme.warningColors.warning`
 */
val MaterialTheme.warningColors: FreshNowWarningColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWarningColors.current

/** 由 [FreshNowTheme] 调用。抽成函数只是为了让上面的 CompositionLocal 保持私有 */
@Composable
internal fun ProvideWarningColors(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWarningColors provides if (darkTheme) DarkWarningColors else LightWarningColors,
        content = content
    )
}
