package com.freshnow.app.ui.welcome

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.component.AiBasicConfigDraft
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 欢迎页的版式（[WelcomeSteps]），不含 ViewModel 与落盘：走真实的 WelcomeScreen 会读写设备上的
 * onboarding DataStore，跑一遍就把「还没看过引导」这个前提写掉，下次手动打开应用就看不到欢迎页了
 * ——这一点 SettingsScreenTest 里写过同样的理由。
 */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 跳过必须一直在：规范要求引导任何时候都能跳过 */
    @Test
    fun firstStepShowsSkipAndNext() {
        setContent()

        composeRule.onNodeWithText("跳过").assertExists()
        composeRule.onNodeWithText("下一步").assertExists()
        composeRule.onNodeWithText("拍下包装上的标签，AI 替你记住它什么时候过期。").assertExists()
    }

    @Test
    fun nextMovesToFlowStep() {
        setContent()

        composeRule.onNodeWithText("下一步").performClick()

        awaitText("识别由你的模型服务完成")
    }

    @Test
    fun configStepOffersTheThreeRequiredFields() {
        setContent()
        goToConfigStep()

        composeRule.onNodeWithText("API Base URL").assertExists()
        composeRule.onNodeWithText("模型名").assertExists()
        composeRule.onNodeWithText("API Key").assertExists()
    }

    /** 三个字段都没填时各报一次必填，不多不少 */
    @Test
    fun configStepShowsARequiredErrorPerEmptyField() {
        setContent(
            basicConfig = AiBasicConfigDraft(
                baseUrlError = true,
                modelNameError = true,
                apiKeyError = true
            )
        )
        goToConfigStep()

        composeRule.onAllNodesWithText("此项为必填项").assertCountEquals(3)
    }

    @Test
    fun savingConfigNotifiesCaller() {
        var saves = 0
        setContent(onSaveConfig = { saves += 1 })
        goToConfigStep()

        composeRule.onNodeWithText("保存并继续").performClick()

        assertEquals(1, saves)
    }

    /**
     * 配置真的落盘之后才推进到权限那一步：这里用外部状态模拟落盘完成，
     * 正是 WelcomeViewModel 的 configSaved 的语义（校验通过 ≠ 落盘完成）。
     */
    @Test
    fun savedConfigAdvancesToCameraStep() {
        val configSaved = mutableStateOf(false)
        setContent(
            configSaved = { configSaved.value },
            onSaveConfig = { configSaved.value = true }
        )
        goToConfigStep()

        composeRule.onNodeWithText("保存并继续").performClick()

        awaitText("扫描需要相机")
        composeRule.onNodeWithText("允许使用相机").assertExists()
    }

    /** 权限已被系统锁死时，主按钮换成去系统设置，并说明原因 */
    @Test
    fun blockedCameraOffersSystemSettings() {
        val configSaved = mutableStateOf(false)
        setContent(
            configSaved = { configSaved.value },
            cameraPermissionBlocked = true,
            onSaveConfig = { configSaved.value = true }
        )
        goToConfigStep()
        composeRule.onNodeWithText("保存并继续").performClick()
        awaitText("扫描需要相机")

        composeRule.onNodeWithText("相机权限已被拒绝，请到系统设置里开启").assertExists()
        composeRule.onNodeWithText("去设置").assertExists()
    }

    @Test
    fun skipNotifiesCaller() {
        var skips = 0
        setContent(onSkip = { skips += 1 })

        composeRule.onNodeWithText("跳过").performClick()

        assertEquals(1, skips)
    }

    /**
     * @param configSaved 「配置已落盘」由外部给：版式自己不认识 DataStore
     */
    private fun setContent(
        basicConfig: AiBasicConfigDraft = AiBasicConfigDraft(),
        configSaved: () -> Boolean = { false },
        cameraPermissionBlocked: Boolean = false,
        onSaveConfig: () -> Unit = {},
        onCameraAction: () -> Unit = {},
        onSkip: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme {
                WelcomeSteps(
                    basicConfig = basicConfig,
                    configSaved = configSaved(),
                    cameraPermissionBlocked = cameraPermissionBlocked,
                    onBaseUrlChange = {},
                    onModelNameChange = {},
                    onApiKeyChange = {},
                    onSaveConfig = onSaveConfig,
                    onCameraAction = onCameraAction,
                    onSkip = onSkip
                )
            }
        }
    }

    /** 从第 1 步走到配置那一步 */
    private fun goToConfigStep() {
        composeRule.onNodeWithText("下一步").performClick()
        awaitText("识别由你的模型服务完成")
        composeRule.onNodeWithText("下一步").performClick()
        awaitText("配置模型服务")
    }

    /** 翻页是动画，等到目标文案出现为止；等的是结果不是时长 */
    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
