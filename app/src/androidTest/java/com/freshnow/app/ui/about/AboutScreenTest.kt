package com.freshnow.app.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.BuildConfig
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 关于页：这个应用是什么、出处在哪。
 *
 * 只摆 [AboutList] 与 [OpenSourceScreen] 本体：前者不含导航与浏览器（那两件事在 AboutScreen
 * 里接上），后者只读本地资源——两者都不碰设备上的数据，也因此不必去验 intent。
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 版本号是这一行的「值」，与标题同一行、在右端，而不是落在标题下面当说明。
     *
     * 判据用纵向交叠：同一行时两者的纵向区间重叠（值的顶在标题的底之上），放到下面则不重叠。
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

    /** 分区顺序：应用 → 项目 → 法律信息 */
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
     * 项目两行都去浏览器、各自带对链接；「开源声明」进的是页内那一页，不是浏览器。
     * 两者的区别要守住，否则点「开源声明」会跳到某个外链上。
     */
    @Test
    fun projectRowsOpenUrlsWhileOpenSourceStaysInApp() {
        val opened = mutableListOf<String>()
        var navigated = false
        setAboutList(onOpenUrl = { opened += it }, onOpenSourceClick = { navigated = true })

        composeRule.onNodeWithText("访问仓库").performClick()
        composeRule.onNodeWithText("问题反馈").performClick()
        composeRule.onNodeWithText("开源声明").performClick()

        assertEquals(
            listOf(
                "https://github.com/Polyaris-0413/FreshNow",
                "https://github.com/Polyaris-0413/FreshNow/issues"
            ),
            opened
        )
        assertTrue("「开源声明」应当是进页面而不是开浏览器", navigated)
    }

    /**
     * 开源声明页：项目清单与两份许可全文都要在。
     *
     * 判据取正文里的独有句而不是许可名：「Apache License 2.0」既是清单里那一行、又是全文的标题，
     * 按许可名断言会撞上两个节点，反而测不出全文在不在——而全文正是 GPL-3.0 要求随程序附的那份副本。
     */
    @Test
    fun openSourceScreenListsProjectsWithFullLicenses() {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                OpenSourceScreen(onBack = {})
            }
        }

        composeRule.onNodeWithText("book-story").assertIsDisplayed()
        composeRule.onNodeWithText("本应用的界面与动画实现取自该项目", substring = true).assertExists()
        composeRule.onNodeWithText("Version 2.0, January 2004", substring = true).assertExists()
        // 取 GPL 正文里的独有句：Apache 全文同样有「TERMS AND CONDITIONS」，用它会撞上两个节点
        composeRule.onNodeWithText("GNU GENERAL PUBLIC LICENSE", substring = true).assertExists()
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).getBoundsInRoot().top

    private fun setAboutList(
        onOpenUrl: (String) -> Unit = {},
        onOpenSourceClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                AboutList(onOpenUrl = onOpenUrl, onOpenSourceClick = onOpenSourceClick)
            }
        }
    }
}
