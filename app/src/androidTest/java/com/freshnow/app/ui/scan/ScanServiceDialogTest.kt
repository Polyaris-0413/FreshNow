package com.freshnow.app.ui.scan

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「AI 服务用不了」说明对话框：正文原样照登服务端返回，两个入口一个复制、一个回主页。
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

    /**
     * 「复制」是主操作：本页的 AI 已经用不了了，把报错带给别的 AI 是此时唯一还有意义的动作。
     * 验证真落到剪贴板上而不是只调用了一个回调
     */
    @Test
    fun copyPutsMessageOnClipboard() {
        val detail = "HTTP 400 {\"error\":{\"message\":\"Model Not Exist\"}}"
        setContent(message = detail)

        composeRule.onNodeWithText("复制").performClick()

        val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(ClipboardManager::class.java)
        assertEquals(detail, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
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
        onAcknowledge: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                ServiceUnavailableDialog(message = message, onAcknowledge = onAcknowledge)
            }
        }
    }
}
