package com.freshnow.app.ui.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「AI 服务用不了」说明对话框：文案要把可能的情况列全，两个入口各走各的路。
 *
 * 只摆对话框本体：它的出现时机由 ScanViewModel 决定，而设备上要复现「服务连不上」得真的把
 * 配置写坏或断网，那属于用户手点的那一遍。
 */
@RunWith(AndroidJUnit4::class)
class ScanServiceDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 正文就是服务端返回的原话：转述过的「常见原因」对不上真正错在哪里，这里只负责把线索原样交给用户。
     * 用一句带错误码的假报错断言，顺便盯住它没有被截断或改写
     */
    @Test
    fun showsServerMessageVerbatim() {
        val detail = "HTTP 401 {\"error\":{\"message\":\"Authentication Fails, Your api key is invalid\"}}"
        setContent(message = detail)

        composeRule.onNodeWithText(detail).assertIsDisplayed()
    }

    /** 「去设置」是主操作：本对话框的价值就在于能一步走到改配置的地方 */
    @Test
    fun openSettingsLeavesForSettings() {
        var opened = 0
        setContent(onOpenSettings = { opened++ })

        composeRule.onNodeWithText("去设置").performClick()

        assertEquals(1, opened)
    }

    /**
     * 关掉只是关掉：不自己跑去别的地方，跳转是调用方的事
     */
    @Test
    fun acknowledgeOnlyReportsBack() {
        var acknowledged = 0
        setContent(onAcknowledge = { acknowledged++ })

        composeRule.onNodeWithText("知道了").performClick()

        assertEquals(1, acknowledged)
    }

    private fun setContent(
        message: String = "HTTP 500 服务暂时不可用",
        onOpenSettings: () -> Unit = {},
        onAcknowledge: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                ServiceUnavailableDialog(
                    message = message,
                    onOpenSettings = onOpenSettings,
                    onAcknowledge = onAcknowledge
                )
            }
        }
    }
}
