package com.freshnow.app.ui.component

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 界面上不直接给 ListItem 传 `supportingContent`。
 *
 * M3 的 ListItem 在说明文字折行时会把整项当成三行——判据写在它的
 * `isSupportingMultilineHeuristic` 里，说明超过 30sp 就算多行；而三行布局里的图标与尾部内容
 * 都是**顶对齐**的（`y = if (isThreeLine) topPadding else CenterVertically.align(...)`）。
 * 对「一行标题 + 一行说明，只是屏幕窄或字号大时折了行」来说，那看起来就是图标莫名其妙偏上，
 * 而这一项本来只有两行。
 *
 * 这个毛病在截图里一眼能看见，在代码里却看不出来，代码评审很容易放过——所以用一条断言
 * 把它挡在提交之前：想写两行文字就走 [ListItemText]，它把说明并进标题那一段，
 * ListItem 只看到一行内容，走的是两行以内的布局，图标与尾部内容才会垂直居中。
 *
 * 与 ThemeWindowBackgroundTest 同一个路数：这类「必须保持一致、而编译器管不了」的事，
 * 只能靠测试守住。有了它，以后再新增页面时不需要谁记得这条规矩。
 *
 * 它管不到的地方也说清楚：不用 M3 的 ListItem 时（比如自己写列表行）这条检查拦不住；
 * M3 若有一天把三行的顶对齐改成居中，这条检查就该跟着撤。
 */
class ListItemLayoutTest {

    @Test
    fun screensDoNotPassSupportingContentToListItem() {
        val sources = mainSources()
        // 先确认真的扫到了东西：路径写错时这条检查会真空通过，那比没有还糟——
        // 它给人的是一份虚假的安心
        assertTrue("没有扫描到任何源码文件，这条检查就成了摆设", sources.isNotEmpty())

        val offenders = sources
            .filter { SUPPORTING_CONTENT.containsMatchIn(it.readText()) }
            .map { it.path }

        assertTrue(
            "这些文件直接给 ListItem 传了 supportingContent：\n" +
                offenders.joinToString("\n") +
                "\n说明折行时 M3 会把整项当成三行，图标与尾部内容会顶对齐（看起来就是偏上）。" +
                "改用 ui/component/ListItemText.kt 里的 ListItemText。",
            offenders.isEmpty()
        )
    }

    private fun mainSources(): List<File> {
        // 工作目录可能是模块目录也可能是仓库根，两种都试一次（同 ThemeWindowBackgroundTest）
        val root = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("找不到 src/main/java（当前工作目录 ${File("").absolutePath}）")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private companion object {
        /**
         * 匹配的是**赋值**而不是这个词本身：ListItemText 的注释里也提到了 supportingContent，
         * 那是在解释为什么不用它，不该被当成违规。
         */
        val SUPPORTING_CONTENT = Regex("""supportingContent\s*=""")
    }
}
