package com.freshnow.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 启动图与窗口的底色只能写在 XML 主题里：主题在 Compose 之前就被系统解析，引用不到
 * MaterialTheme.colorScheme，所以这个值必然在两处各存一份。这里断言两份相等，
 * 避免只改了 Compose 侧、主题侧没跟上——那会直接表现为启动图与首帧之间的色差。
 *
 * 断言的目标是 M3 基线。开启动态取色时应用实际背景由壁纸在运行时派生，与基线本就不同，
 * 那部分色差是平台限制：启动图底色无法在运行时计算，只能取一个静态值。
 */
class ThemeWindowBackgroundTest {

    @Test
    fun lightTheme_matchesBaselineBackground() {
        assertWindowBackground("src/main/res/values/themes.xml", lightColorScheme().background.toArgb())
    }

    @Test
    fun nightTheme_matchesBaselineBackground() {
        assertWindowBackground("src/main/res/values-night/themes.xml", darkColorScheme().background.toArgb())
    }

    private fun assertWindowBackground(relativePath: String, expectedArgb: Int) {
        val file = listOf(File(relativePath), File("app/$relativePath"))
            .firstOrNull { it.isFile }
            ?: error("找不到 $relativePath（当前工作目录 ${File("").absolutePath}）")

        val actual = WINDOW_BACKGROUND.find(file.readText())?.groupValues?.get(1)
            ?: error("$relativePath 里没有 window_background 颜色声明")

        assertEquals(
            "$relativePath 的窗口底色与 Compose 基线背景不一致（基线为 #%06X）"
                .format(expectedArgb and 0xFFFFFF),
            "%06X".format(expectedArgb and 0xFFFFFF),
            actual.uppercase()
        )
    }

    private companion object {
        val WINDOW_BACKGROUND = Regex("""<color name="window_background">#([0-9A-Fa-f]{6})</color>""")
    }
}
