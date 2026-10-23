package com.freshnow.app.ui.about

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.BuildConfig
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 关于页：这个应用是什么、出处在哪。只摆 [AboutList] 与 [OpenSourceScreen] 本体，
 * 两者都不碰设备上的数据，因此不必验 intent。
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 版本号是这一行的「值」，与标题同一行、在右端。判据用纵向交叠：同一行时区间重叠（值的顶在标题的底之上），放到下面则不重叠。
     */
    @Test
    fun versionShowsBesideItsLabel() {
        setAboutList()

        val label = composeRule.onNodeWithText("版本").getBoundsInRoot()
        val value = composeRule.onNodeWithText(BuildConfig.VERSION_NAME).getBoundsInRoot()

        assertTrue(
            "版本号应当与「版本」同一行（在右端）：label=$label value=$value",
            value.top < label.bottom
        )
    }

    @Test
    fun sectionsAreOrdered() {
        setAboutList()

        val app = top("应用")
        val project = top("项目")
        val legal = top("法律信息")

        assertTrue("项目应当在应用之后：app=$app project=$project", project > app)
        assertTrue("法律信息应当在项目之后：project=$project legal=$legal", legal > project)
    }

    /**
     * 项目各行都去浏览器并各带对链接；「开源声明」进页内那一页，两者的区别要守住，否则会跳到某个外链上。
     * 作者行给作者主页（给仓库就成了「访问仓库」的重复入口），群聊行给 QQ 加群页——那一页是对外的入口，不是应用内的页面。
     */
    @Test
    fun projectRowsOpenUrlsWhileOpenSourceStaysInApp() {
        val opened = mutableListOf<String>()
        var navigated = false
        setAboutList(onOpenUrl = { opened += it }, onOpenSourceClick = { navigated = true })

        composeRule.onNodeWithText("作者").performClick()
        composeRule.onNodeWithText("访问仓库").performClick()
        composeRule.onNodeWithText("问题反馈").performClick()
        composeRule.onNodeWithText("加入群聊").performClick()
        composeRule.onNodeWithText("开源声明").performClick()

        assertEquals(
            listOf(
                "https://github.com/Polyaris-0413",
                "https://github.com/Polyaris-0413/FreshNow",
                "https://github.com/Polyaris-0413/FreshNow/issues",
                "https://qm.qq.com/q/mtL1olGWwo"
            ),
            opened
        )
        assertTrue("「开源声明」应当是进页面而不是开浏览器", navigated)
    }

    /**
     * 开源声明页：依赖图里的每一个都占一行（不归并、不遗漏），且不铺许可全文——
     * 副本由仓库的 LICENSE 承担，不摊在界面上。
     */
    @Test
    fun openSourceScreenListsEveryDependencyWithoutLicenseText() {
        composeRule.setContent {
            FreshNowTheme() {
                OpenSourceScreen(onBack = {})
            }
        }

        // 清单是从 assets 异步读的：等第一条到位再断言，否则空列表也能让后面的断言糊过去
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Activity").fetchSemanticsNodes().isNotEmpty()
        }

        // 逐条列出：首屏就不止一个依赖
        assertTrue(
            "首屏应当列出多个依赖，而不是被归并成几行",
            composeRule.onAllNodesWithText("Apache License 2.0").fetchSemanticsNodes().size >= 5
        )

        // 能滚到列表末尾那条（SLF4J），说明确实是完整清单而不是挑着列的
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("SLF4J API Module"))
        composeRule.onNodeWithText("SLF4J API Module").assertExists()

        // 页面只列清单，不铺许可全文（副本由仓库的 LICENSE 承担）：全文里有独有句，清单里没有
        composeRule.onAllNodesWithText("Version 2.0, January 2004", substring = true)
            .assertCountEquals(0)
    }

    /**
     * 「版本」（彩蛋）与「检查更新」（敬请期待）两行各自报回调，不去浏览器也不导航。
     * 判据取回调有没有被叫到：[AboutList] 只负责往外报，弹什么由 AboutScreen 决定。
     */
    @Test
    fun versionAndCheckUpdateInvokeTheirOwnActions() {
        val opened = mutableListOf<String>()
        var navigated = false
        var versionClicks = 0
        var checkUpdateClicks = 0
        setAboutList(
            onOpenUrl = { opened += it },
            onOpenSourceClick = { navigated = true },
            onVersionClick = { versionClicks++ },
            onCheckUpdateClick = { checkUpdateClicks++ }
        )

        composeRule.onNodeWithText("版本").performClick()
        composeRule.onNodeWithText("检查更新").performClick()

        assertEquals("版本那一行应当报一次自己的动作", 1, versionClicks)
        assertEquals("检查更新那一行应当报一次自己的动作", 1, checkUpdateClicks)
        assertEquals("这两行不该去浏览器", emptyList<String>(), opened)
        assertFalse("这两行不该导航", navigated)
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).getBoundsInRoot().top

    private fun setAboutList(
        onOpenUrl: (String) -> Unit = {},
        onOpenSourceClick: () -> Unit = {},
        onVersionClick: () -> Unit = {},
        onCheckUpdateClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme() {
                AboutList(
                    onOpenUrl = onOpenUrl,
                    onOpenSourceClick = onOpenSourceClick,
                    onVersionClick = onVersionClick,
                    onCheckUpdateClick = onCheckUpdateClick
                )
            }
        }
    }
}
