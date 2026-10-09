package com.freshnow.app.ui.settings

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.data.SortOrder
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页只放可调项：AI、行为与调试。应用信息、项目入口、法律信息都在关于页（见 AboutScreenTest）。
 *
 * 只摆 [SettingsList] 本体（不含落盘与编辑面板）：走真实的 SettingsScreen 会连设备上真实的
 * DataStore，跑一次就把用户的 AI 配置覆盖掉——这一点 AiSettingsRepositoryTest 里已写明，
 * 这里同样避开。端到端那一遍留给用户手点。
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
     * 「手动输入」归在「行为」分区下，且行为那一段夹在「AI」与「调试」之间。
     *
     * 分区是纯版式概念、没有语义节点，只能按位置验。顺序的理：面向用户的开关排在前，调试排最后。
     */
    @Test
    fun manualEntrySitsUnderBehaviorSection() {
        setContent()

        val aiTitle = top("AI")
        val behaviorTitle = top("行为")
        val manualEntryRow = top("手动输入")
        val debugTitle = top("调试")

        assertTrue("「行为」应当排在「AI」之后：ai=$aiTitle behavior=$behaviorTitle", behaviorTitle > aiTitle)
        assertTrue(
            "「手动输入」应当在「行为」标题之下：behavior=$behaviorTitle row=$manualEntryRow",
            manualEntryRow > behaviorTitle
        )
        assertTrue("「调试」应当排在「行为」之后：behavior=$behaviorTitle debug=$debugTitle", debugTitle > behaviorTitle)
    }

    /** 与「显示思维链」同一条规矩：整行可点即切换 */
    @Test
    fun tappingManualEntryRowTogglesIt() {
        val changes = mutableListOf<Boolean>()
        setContent(onManualEntryChange = { changes += it })

        composeRule.onNodeWithText("手动输入").performClick()

        assertEquals("开关关着时点一下应当是打开", listOf(true), changes)
    }

    /** 「排序方式」也归「行为」段，行上的说明文字就是当前用的是哪种 */
    @Test
    fun sortOrderRow_showsCurrentOrderUnderBehavior() {
        setContent(sortOrder = SortOrder.EXPIRY_DATE)

        assertTrue("应当在「行为」标题之下", top("排序方式") > top("行为"))
        assertTrue("排序方式应当排在「调试」之前", top("排序方式") < top("调试"))
        composeRule.onNodeWithText("按过期日期").assertExists()
    }

    /** 整行可点：选哪一个不在这行上做，而是点开那个单面面板（与主页顶栏共用同一份内容） */
    @Test
    fun tappingSortOrderRow_opensTheChooser() {
        var clicks = 0
        setContent(onSortOrderClick = { clicks++ })

        composeRule.onNodeWithText("排序方式").performClick()

        assertEquals(1, clicks)
    }

    /** 应用信息、项目与法律入口都不在设置里——它们归关于页，别又长回来一份 */
    @Test
    fun aboutContentIsNotOnSettings() {
        setContent()

        listOf("版本", "检查更新", "访问仓库", "问题反馈", "开源声明").forEach { text ->
            composeRule.onNodeWithText(text).assertDoesNotExist()
        }
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).getBoundsInRoot().top

    private fun setContent(
        onShowReasoningChange: (Boolean) -> Unit = {},
        onManualEntryChange: (Boolean) -> Unit = {},
        sortOrder: SortOrder = SortOrder.CREATED_AT,
        onSortOrderClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                SettingsList(
                    summary = "未配置",
                    extraSummary = "跟随服务商默认",
                    showReasoning = true,
                    manualEntry = false,
                    sortOrder = sortOrder,
                    onBasicConfigClick = {},
                    onExtraRequestClick = {},
                    onShowReasoningChange = onShowReasoningChange,
                    onManualEntryChange = onManualEntryChange,
                    onSortOrderClick = onSortOrderClick
                )
            }
        }
    }
}
