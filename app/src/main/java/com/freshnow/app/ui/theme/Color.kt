package com.freshnow.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 本文件由 tools/generate-color-scheme.cjs 生成，请勿手改。
 *
 * seed：#4285F4
 * 生成器：@material/material-color-utilities@0.3.0 的 themeFromSourceColor（经典色调映射）
 *
 * 色调映射完全以生成器为准，不要按 material-3 技能的映射表手改：那张表是设计参考，
 * 与实现在个别角色上并不一致（例如 background/surface 该实现取 neutral tone 99，技能表记的是 98；
 * 深色 surface 该实现取 tone 10，技能表记的是 6）。本文件的值全部来自生成器。
 *
 * 重新生成：node tools/generate-color-scheme.cjs '<新 seed>'
 * 之后必须同步 res/values/themes.xml 与 res/values-night/themes.xml 的 @color/window_background
 * （脚本会打印该值）——页面底色与启动图底色共用该资源，ThemeWindowBackgroundTest 断言两者相等。
 *
 * 表面容器五档生成器不提供，按技能 color-system.md:122-128 / :140-146 明文给出的色调，
 * 从同一条 neutral 色调板取值。
 *
 * 有意未生成：surfaceDim、surfaceBright、surfaceTint —— 生成器不提供、技能也未给出色调，故不臆造。
 * 本项目未使用 tonalElevation，暂不影响观感；将来要用之前须先补上这三个角色。
 */

// 色调板取值，命名规则为 <色调板><色调>
internal val Primary10 = Color(0xFF001A41)
internal val Primary20 = Color(0xFF002E69)
internal val Primary30 = Color(0xFF004494)
internal val Primary40 = Color(0xFF005AC1)
internal val Primary80 = Color(0xFFADC6FF)
internal val Primary90 = Color(0xFFD8E2FF)
internal val Primary100 = Color(0xFFFFFFFF)
internal val Secondary10 = Color(0xFF141B2C)
internal val Secondary20 = Color(0xFF293041)
internal val Secondary30 = Color(0xFF3F4759)
internal val Secondary40 = Color(0xFF575E71)
internal val Secondary80 = Color(0xFFBFC6DC)
internal val Secondary90 = Color(0xFFDBE2F9)
internal val Secondary100 = Color(0xFFFFFFFF)
internal val Tertiary10 = Color(0xFF29132D)
internal val Tertiary20 = Color(0xFF402843)
internal val Tertiary30 = Color(0xFF583E5B)
internal val Tertiary40 = Color(0xFF715573)
internal val Tertiary80 = Color(0xFFDEBCDF)
internal val Tertiary90 = Color(0xFFFBD7FC)
internal val Tertiary100 = Color(0xFFFFFFFF)
internal val Neutral0 = Color(0xFF000000)
internal val Neutral4 = Color(0xFF0D0E11)
internal val Neutral10 = Color(0xFF1B1B1F)
internal val Neutral12 = Color(0xFF1F1F23)
internal val Neutral17 = Color(0xFF292A2D)
internal val Neutral20 = Color(0xFF303033)
internal val Neutral22 = Color(0xFF343538)
internal val NeutralVariant30 = Color(0xFF44474F)
internal val NeutralVariant50 = Color(0xFF74777F)
internal val NeutralVariant60 = Color(0xFF8E9099)
internal val NeutralVariant80 = Color(0xFFC4C6D0)
internal val NeutralVariant90 = Color(0xFFE1E2EC)
internal val Neutral90 = Color(0xFFE3E2E6)
internal val Neutral92 = Color(0xFFE9E7EC)
internal val Neutral94 = Color(0xFFEFEDF1)
internal val Neutral95 = Color(0xFFF2F0F4)
internal val Neutral96 = Color(0xFFF5F3F7)
internal val Neutral99 = Color(0xFFFEFBFF)
internal val Neutral100 = Color(0xFFFFFFFF)
internal val Error10 = Color(0xFF410002)
internal val Error20 = Color(0xFF690005)
internal val Error30 = Color(0xFF93000A)
internal val Error40 = Color(0xFFBA1A1A)
internal val Error80 = Color(0xFFFFB4AB)
internal val Error90 = Color(0xFFFFDAD6)
internal val Error100 = Color(0xFFFFFFFF)

internal val LightColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Primary100,
    primaryContainer = Primary90,
    onPrimaryContainer = Primary10,
    inversePrimary = Primary80,
    secondary = Secondary40,
    onSecondary = Secondary100,
    secondaryContainer = Secondary90,
    onSecondaryContainer = Secondary10,
    tertiary = Tertiary40,
    onTertiary = Tertiary100,
    tertiaryContainer = Tertiary90,
    onTertiaryContainer = Tertiary10,
    error = Error40,
    onError = Error100,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    scrim = Neutral0,
)

internal val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    onPrimary = Primary20,
    primaryContainer = Primary30,
    onPrimaryContainer = Primary90,
    inversePrimary = Primary40,
    secondary = Secondary80,
    onSecondary = Secondary20,
    secondaryContainer = Secondary30,
    onSecondaryContainer = Secondary90,
    tertiary = Tertiary80,
    onTertiary = Tertiary20,
    tertiaryContainer = Tertiary30,
    onTertiaryContainer = Tertiary90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error80,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    scrim = Neutral0,
)
