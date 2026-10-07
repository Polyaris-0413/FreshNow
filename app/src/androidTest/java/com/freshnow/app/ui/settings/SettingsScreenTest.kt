package com.freshnow.app.ui.settings

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页只放可调项：AI 与调试。应用信息、项目入口、法律信息都在关于页（见 AboutScreenTest）。
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

    /** 应用信息、项目与法律入口都不在设置里——它们归关于页，别又长回来一份 */
    @Test
    fun aboutContentIsNotOnSettings() {
        setContent()

        listOf("版本", "检查更新", "访问仓库", "问题反馈", "开源声明").forEach { text ->
            composeRule.onNodeWithText(text).assertDoesNotExist()
        }
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).getBoundsInRoot().top

    private fun setContent(onShowReasoningChange: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                SettingsList(
                    summary = "未配置",
                    extraSummary = "跟随服务商默认",
                    showReasoning = true,
                    onBasicConfigClick = {},
                    onExtraRequestClick = {},
                    onShowReasoningChange = onShowReasoningChange
                )
            }
        }
    }
}
