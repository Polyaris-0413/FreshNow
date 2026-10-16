package com.freshnow.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 本文件由 tools/generate-color-scheme.cjs 生成，请勿手改。
 *
 * 品牌 seed：#07C160（H152.8 C69.9 T68.8）
 * 警示 seed：#FFA000（H68.5 C61.1 T73.8）
 * 生成器：@material/material-color-utilities@0.3.0 的 themeFromSourceColor（经典色调映射）
 *
 * 色调映射完全以生成器为准，不要按 material-3 技能的映射表手改：那张表是设计参考，
 * 与实现在个别角色上并不一致（例如 background/surface 该实现取 neutral tone 99，技能表记的是 98；
 * 深色 surface 该实现取 tone 10，技能表记的是 6）。本文件的值全部来自生成器。
 *
 * 警示色的色相（amber）是产品选择，具体 seed 是工程选择，没有规范出处，随时可换：
 *   node tools/generate-color-scheme.cjs '#07C160' '<新的警示色>'
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
internal val Primary10 = Color(0xFF00210B)
internal val Primary20 = Color(0xFF003917)
internal val Primary30 = Color(0xFF005225)
internal val Primary40 = Color(0xFF006D33)
internal val Primary80 = Color(0xFF45E17C)
internal val Primary90 = Color(0xFF66FF95)
internal val Primary100 = Color(0xFFFFFFFF)
internal val Secondary10 = Color(0xFF0E1F12)
internal val Secondary20 = Color(0xFF233426)
internal val Secondary30 = Color(0xFF394B3B)
internal val Secondary40 = Color(0xFF506352)
internal val Secondary80 = Color(0xFFB7CCB7)
internal val Secondary90 = Color(0xFFD3E8D2)
internal val Secondary100 = Color(0xFFFFFFFF)
internal val Tertiary10 = Color(0xFF001F25)
internal val Tertiary20 = Color(0xFF00363E)
internal val Tertiary30 = Color(0xFF204D55)
internal val Tertiary40 = Color(0xFF39656E)
internal val Tertiary80 = Color(0xFFA2CED8)
internal val Tertiary90 = Color(0xFFBDEAF4)
internal val Tertiary100 = Color(0xFFFFFFFF)
internal val Neutral0 = Color(0xFF000000)
internal val Neutral4 = Color(0xFF0C0F0C)
internal val Neutral10 = Color(0xFF191C19)
internal val Neutral12 = Color(0xFF1E201D)
internal val Neutral17 = Color(0xFF282B27)
internal val Neutral20 = Color(0xFF2E312E)
internal val Neutral22 = Color(0xFF333532)
internal val NeutralVariant30 = Color(0xFF414941)
internal val NeutralVariant50 = Color(0xFF717970)
internal val NeutralVariant60 = Color(0xFF8B9389)
internal val NeutralVariant80 = Color(0xFFC1C9BE)
internal val NeutralVariant90 = Color(0xFFDDE5DA)
internal val Neutral90 = Color(0xFFE2E3DE)
internal val Neutral92 = Color(0xFFE7E9E3)
internal val Neutral94 = Color(0xFFEDEEE9)
internal val Neutral95 = Color(0xFFF0F1EC)
internal val Neutral96 = Color(0xFFF3F4EF)
internal val Neutral99 = Color(0xFFFCFDF7)
internal val Neutral100 = Color(0xFFFFFFFF)
internal val Error10 = Color(0xFF410002)
internal val Error20 = Color(0xFF690005)
internal val Error30 = Color(0xFF93000A)
internal val Error40 = Color(0xFFBA1A1A)
internal val Error80 = Color(0xFFFFB4AB)
internal val Error90 = Color(0xFFFFDAD6)
internal val Error100 = Color(0xFFFFFFFF)
internal val Warning10 = Color(0xFF2B1700)
internal val Warning20 = Color(0xFF482A00)
internal val Warning30 = Color(0xFF663D00)
internal val Warning40 = Color(0xFF875200)
internal val Warning80 = Color(0xFFFFB865)
internal val Warning90 = Color(0xFFFFDDBA)
internal val Warning100 = Color(0xFFFFFFFF)

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

internal val LightWarningColors = FreshNowWarningColors(
    warning = Warning40,
    onWarning = Warning100,
    warningContainer = Warning90,
    onWarningContainer = Warning10,
)

internal val DarkWarningColors = FreshNowWarningColors(
    warning = Warning80,
    onWarning = Warning20,
    warningContainer = Warning30,
    onWarningContainer = Warning90,
)
