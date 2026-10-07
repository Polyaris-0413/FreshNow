package com.freshnow.app.ui.settings

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
 * 设置页分区归属的守卫。
 *
 * 只摆 [SettingsList] 本体（不含落盘与编辑面板）：走真实的 SettingsScreen 会连上设备上真实的
 * DataStore，跑一次就把用户的 AI 配置覆盖掉——这一点 AiSettingsRepositoryTest 里已写明，这里
 * 同样避开。端到端那一遍留给用户手点。
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 「显示思维链」归在「调试」分区下：分区标题在它上方，且「调试」排在「AI」那一段之后。
     *
     * 分区是纯版式概念、没有语义节点，只能按位置验；顺带盯住「思考参数」仍在「调试」之前，
     * 免得日后连带把 AI 的项也挪出那一段。
     */
    @Test
    fun showReasoningSitsUnderDebugSection() {
        setContent()

        val aiTitle = top("AI")
        val extraRow = top("思考参数")
        val debugTitle = top("调试")
        val reasoningRow = top("显示思维链")

        assertTrue("「调试」分区应当排在「AI」那一段之后：ai=$aiTitle debug=$debugTitle", debugTitle > aiTitle)
        assertTrue("「思考参数」仍在 AI 段内（在「调试」之前）：extra=$extraRow debug=$debugTitle", extraRow < debugTitle)
        assertTrue("「显示思维链」应当在「调试」标题之下：debug=$debugTitle reasoning=$reasoningRow", reasoningRow > debugTitle)
    }

    /** 整行可点即切换，不只点开关才算：开关自己不吃点击（onCheckedChange = null） */
    @Test
    fun tappingReasoningRowTogglesIt() {
        val changes = mutableListOf<Boolean>()
        setContent(onShowReasoningChange = { changes += it })

        composeRule.onNodeWithText("显示思维链").performClick()

        assertEquals(listOf(false), changes)
    }

    /**
     * 应用分区：版本号是这一行的「值」，与标题同一行、在右端，而不是落在标题下面当说明。
     *
     * 判据用纵向交叠：同一行时两者的纵向区间重叠（值的顶在标题的底之上），放到下面则不重叠。
     */
    @Test
    fun appSectionShowsVersionBesideItsLabel() {
        setContent()

        val debugTitle = top("调试")
        val appTitle = top("应用")
        assertTrue("「应用」分区应当排在原有分区之后：debug=$debugTitle app=$appTitle", appTitle > debugTitle)

        composeRule.onNodeWithText("检查更新").assertIsDisplayed()

        val label = composeRule.onNodeWithText("版本").getBoundsInRoot()
        val value = composeRule.onNodeWithText(BuildConfig.VERSION_NAME).getBoundsInRoot()
        assertTrue(
            "版本号应当与「版本」同一行（在右端）：label=$label value=$value",
            value.top < label.bottom
        )
    }

    /**
     * 项目分区两行都去浏览器，且各自带对链接；法律信息的「开源声明」进的是页内那一页，
     * 不是浏览器——两者的区别要守住，否则点「开源声明」会跳到某个外链上。
     */
    @Test
    fun projectRowsOpenUrlsWhileOpenSourceStaysInApp() {
        val opened = mutableListOf<String>()
        var navigated = false
        setContent(onOpenUrl = { opened += it }, onOpenSourceClick = { navigated = true })

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

    /** 分区顺序：应用 → 项目 → 法律信息，都在原有分区之后 */
    @Test
    fun newSectionsComeAfterExistingOnes() {
        setContent()

        val app = top("应用")
        val project = top("项目")
        val legal = top("法律信息")

        assertTrue("项目应当在应用之后：app=$app project=$project", project > app)
        assertTrue("法律信息应当在项目之后：project=$project legal=$legal", legal > project)
    }

    /**
     * 开源声明页：项目清单与两份许可全文都要在。
     *
     * 判据取正文里的独有句子而不是许可名：「Apache License 2.0」既是清单里那一行、又是全文的标题，
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

    private fun setContent(
        onShowReasoningChange: (Boolean) -> Unit = {},
        onOpenUrl: (String) -> Unit = {},
        onOpenSourceClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                SettingsList(
                    summary = "未配置",
                    extraSummary = "跟随服务商默认",
                    showReasoning = true,
                    onBasicConfigClick = {},
                    onExtraRequestClick = {},
                    onShowReasoningChange = onShowReasoningChange,
                    onOpenUrl = onOpenUrl,
                    onOpenSourceClick = onOpenSourceClick
                )
            }
        }
    }
}
