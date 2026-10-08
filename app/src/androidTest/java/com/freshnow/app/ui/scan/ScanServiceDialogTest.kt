package com.freshnow.app.ui.scan

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「AI 服务用不了」说明对话框：正文只教怎么用日志，两个入口一个复制、一个回主页。
 *
 * 只摆对话框本体：它的出现时机由 ScanViewModel 决定，而设备上要复现「服务连不上」得真的把
 * 配置写坏或断网，那属于用户手点的那一遍。
 */
@RunWith(AndroidJUnit4::class)
class ScanServiceDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 正文只说明拿了日志怎么办，不摊开日志本身：日志是给别的 AI 读的，在对话框里再展一遍长报错
     * 只是白占地方。逐字断言，免得日后又被改回贴报错
     */
    @Test
    fun showsHowToUseTheLog() {
        setContent()

        composeRule.onNodeWithText("请点击「复制」，将剪贴板内容发给其他 AI。").assertIsDisplayed()
    }

    /**
     * 「复制」是主操作：本页的 AI 已经用不了了，把日志带给别的 AI 是此时唯一还有意义的动作。
     * 验证真落到剪贴板上而不是只调用了一个回调
     */
    @Test
    fun copyPutsLogOnClipboard() {
        val log = "HTTP 400 {\"error\":{\"message\":\"Model Not Exist\"}}"
        setContent(log = log)

        composeRule.onNodeWithText("复制").performClick()

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertEquals(log, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
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
        log: String = "HTTP 500 服务暂时不可用",
        body: String = context.getString(R.string.scan_service_dialog_message),
        onAcknowledge: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                ServiceUnavailableDialog(body = body, log = log, onAcknowledge = onAcknowledge)
            }
        }
    }
}
