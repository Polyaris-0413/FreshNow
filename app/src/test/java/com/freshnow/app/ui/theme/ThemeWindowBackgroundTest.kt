package com.freshnow.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 启动图与窗口的底色只能写在 XML 主题里：主题在 Compose 之前就被系统解析，引用不到 colorScheme，
 * 所以 Color.kt 里的 background 必然在主题侧还有一份副本。
 *
 * 这里断言两份相等。换 seed 重新生成 Color.kt 后若忘了同步主题，启动图与首帧之间就会出现色差，
 * 而本测试会先失败把问题挡下来。
 */
class ThemeWindowBackgroundTest {

    @Test
    fun lightTheme_matchesLightColorSchemeBackground() {
        assertWindowBackground("src/main/res/values/themes.xml", LightColorScheme.background.toArgb())
    }

    @Test
    fun nightTheme_matchesDarkColorSchemeBackground() {
        assertWindowBackground("src/main/res/values-night/themes.xml", DarkColorScheme.background.toArgb())
    }

    private fun assertWindowBackground(relativePath: String, expectedArgb: Int) {
        val file = listOf(File(relativePath), File("app/$relativePath"))
            .firstOrNull { it.isFile }
            ?: error("找不到 $relativePath（当前工作目录 ${File("").absolutePath}）")

        val actual = WINDOW_BACKGROUND.find(file.readText())?.groupValues?.get(1)
            ?: error("$relativePath 里没有 window_background 颜色声明")

        assertEquals(
            "$relativePath 的窗口底色与 Color.kt 的 background 不一致（应为 #%06X）"
                .format(expectedArgb and 0xFFFFFF),
            "%06X".format(expectedArgb and 0xFFFFFF),
            actual.uppercase()
        )
    }

    private companion object {
        val WINDOW_BACKGROUND = Regex("""<color name="window_background">#([0-9A-Fa-f]{6})</color>""")
    }
}
