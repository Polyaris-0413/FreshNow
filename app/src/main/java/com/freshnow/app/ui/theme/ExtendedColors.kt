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
 * （取值在 Color.kt，由 tools/generate-color-scheme.cjs 生成；本文件管 warning 的结构与
 * 取用方式，图片浮层色则直接定义在文件末尾）。
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

/**
 * 压在图片上的浮层（详情页那枚页码）的底色与文字色。
 * 【为什么放在这里】Color.kt 由 tools/generate-color-scheme.cjs 生成，生成器只输出能从 seed 推导的标准角色；
 * 这组色不从 seed 推导，写进 Color.kt 会在下次重新生成时被删掉。
 * 【为什么固定黑/白】浮层底下是任意照片，不是主题表面，主题色在深色主题下会「深底深字」。
 * 固定成 80% 黑底（`0xCC`）加白字后，纯白照片上的合成色约 `#333`，对比度约 12.6:1。
 * 【规范缺席】M3 没有「压在图片上的浮层」这一档，这是本项目自定角色。
 */
internal val OverImageScrim = Color(0xCC000000)

/** [OverImageScrim] 上的文字色；白字与该底色的对比度见 [OverImageScrim] 的说明。 */
internal val OnOverImageScrim = Color(0xFFFFFFFF)
