package com.freshnow.app.ui.welcome

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.component.AiBasicConfigDraft
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** 信息页上没有「跳过」：那里它与「下一步」做的是同一件事，摆两颗同义的按钮只会让人犹豫 */
    @Test
    fun firstStepOffersNextOnly() {
        setContent()

        composeRule.onNodeWithText("下一步").assertIsDisplayed()
        assertNotVisible("跳过")
        composeRule.onNodeWithText("借助模型管理食品保质期。").assertIsDisplayed()
        composeRule.onNodeWithText("多模态识别相比传统 OCR 的优势").assertIsDisplayed()
        composeRule.onNodeWithText("识别效率与准确度相当，成本更低").assertIsDisplayed()
    }

    /** 「跳过」只出现在确实有可以不做的事那两步：配置（不填）与权限（不授权） */
    @Test
    fun skipIsOfferedOnlyWhereThereIsSomethingToSkip() {
        setContent()
        assertNotVisible("跳过")

        goToConfigStep()

        composeRule.onNodeWithText("跳过").assertIsDisplayed()
    }

    /** 第 2 步起才有 */
    @Test
    fun progressBarAppearsFromSecondStep() {
        setContent()

        composeRule.onAllNodes(progressBar).assertCountEquals(0)

        clickSettled("下一步")
        awaitText("标签上的字交给模型读")

        composeRule.onAllNodes(progressBar).assertCountEquals(1)
    }

    @Test
    fun nextMovesToFlowStep() {
        setContent()

        clickSettled("下一步")

        awaitText("标签上的字交给模型读")
    }

    @Test
    fun previousReturnsToIntroStep() {
        setContent()
        clickSettled("下一步")
        awaitText("标签上的字交给模型读")

        clickSettled("上一步")

        awaitText("借助模型管理食品保质期。")
    }

    /**
     * 手指往左滑不换页。滑动在这里与「跳过」同义：配置这一步还没填，滑过去等于把没填的东西跳过，
     * 而屏幕上不给任何提示。M3 的 Gestures 只把横向滑动留给对等视图切换与完成动作。
     */
    @Test
    fun swipingForwardDoesNotChangeStep() {
        setContent()
        goToConfigStep()

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("填上你的模型").assertIsDisplayed()
        assertNotVisible("扫描时要用到相机")
    }

    /** 往回走只有「上一步」与系统返回键两条路 */
    @Test
    fun swipingBackwardDoesNotChangeStep() {
        setContent()
        clickSettled("下一步")
        awaitText("标签上的字交给模型读")

        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("标签上的字交给模型读").assertIsDisplayed()
        assertNotVisible("借助模型管理食品保质期。")
    }

    /**
     * 连点「上一步」不能崩：翻页过半时当前页已是 0，而正在淡出的按钮仍接得到点击，
     * 下标减一会取到 steps[-1]（真机上连点两次曾因此闪退）。
     */
    @Test
    fun tappingPreviousTwiceQuicklyDoesNotCrash() {
        setContent()
        clickSettled("下一步")
        awaitText("标签上的字交给模型读")

        // 两次点击之间不留落定的时间：第一次刚把当前页推到 0，第二次就落在它身上
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("上一步").performClick()
        composeRule.mainClock.advanceTimeBy(HALF_STATE_CHANGE_MS)
        val stillThere = composeRule.onAllNodesWithText("上一步").fetchSemanticsNodes().isNotEmpty()
        if (stillThere) composeRule.onNodeWithText("上一步").performClick()
        composeRule.mainClock.autoAdvance = true

        awaitText("借助模型管理食品保质期。")
    }

    /** 「跳过」跳过的是当前这一步，不是整段引导：配置那一步不填就是直接往前走，不该收场 */
    @Test
    fun skipMovesToNextStepInsteadOfEndingTheGuide() {
        var skips = 0
        setContent(onSkip = { skips += 1 })
        goToConfigStep()

        clickSettled("跳过")

        awaitText("扫描时要用到相机")
        assertEquals(0, skips)
    }

    @Test
    fun configStepOffersTheThreeRequiredFields() {
        setContent()
        goToConfigStep()

        composeRule.onNodeWithText("API Base URL").assertExists()
        composeRule.onNodeWithText("模型名").assertExists()
        composeRule.onNodeWithText("API Key").assertExists()
    }

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

        clickSettled("下一步")

        assertEquals(1, saves)
    }

    /**
     * 配置真的落盘之后才推进到权限那一步：这里用外部状态模拟落盘完成，
     * 正是 WelcomeViewModel 的 configSavedCount 的语义（校验通过 ≠ 落盘完成）。
     */
    @Test
    fun savedConfigAdvancesToCameraStep() {
        val savedCount = mutableStateOf(0)
        setContent(
            configSavedCount = { savedCount.value },
            onSaveConfig = { savedCount.value += 1 }
        )
        goToConfigStep()

        clickSettled("下一步")

        awaitText("扫描时要用到相机")
        composeRule.onNodeWithText("允许使用相机").assertExists()
    }

    /**
     * 点「允许使用相机」只发起请求：结果没回来之前必须留在这一页，
     * 否则用户在系统弹窗里点拒绝时，人已经站在收尾页上了。
     */
    @Test
    fun cameraRequestDoesNotAdvanceUntilGranted() {
        var requests = 0
        setContent(onRequestCamera = { requests += 1 })
        goToCameraStep()

        clickSettled("允许使用相机")

        assertEquals(1, requests)
        composeRule.onNodeWithText("扫描时要用到相机").assertIsDisplayed()
        assertNotVisible("都设置好了")
    }

    @Test
    fun grantedCameraAdvancesToDoneStep() {
        val grantedCount = mutableStateOf(0)
        setContent(
            cameraGrantedCount = { grantedCount.value },
            // 模拟用户在系统弹窗里点了「允许」
            onRequestCamera = { grantedCount.value += 1 }
        )
        goToCameraStep()

        clickSettled("允许使用相机")

        awaitText("都设置好了")
        composeRule.onNodeWithText("开始使用").assertExists()
    }

    /**
     * 信号早于「走到相机那一步」发生时不生效：进引导时权限本来就在（例如重装后系统仍记着），
     * 不该把人从第 1 步直接丢到收尾页。
     */
    @Test
    fun grantedPermissionBeforeCameraStepDoesNotSkipAhead() {
        setContent(cameraGrantedCount = { 1 })

        composeRule.onNodeWithText("借助模型管理食品保质期。").assertIsDisplayed()
        assertNotVisible("都设置好了")
    }

    @Test
    fun blockedCameraOffersSystemSettings() {
        var openedSettings = 0
        setContent(
            cameraPermissionBlocked = true,
            onOpenCameraSettings = { openedSettings += 1 }
        )
        goToCameraStep()

        composeRule.onNodeWithText("相机权限已被拒绝，请到系统设置里开启").assertExists()
        clickSettled("去设置")

        assertEquals(1, openedSettings)
    }

    /** 跳过相机授权也要走到收尾那一步，收尾页上才有真正的「结束引导」 */
    @Test
    fun skippingCameraGoesToDoneAndStartEndsTheGuide() {
        var skips = 0
        setContent(onSkip = { skips += 1 })
        goToCameraStep()

        clickSettled("跳过")
        awaitText("都设置好了")
        assertEquals(0, skips)

        clickSettled("开始使用")
        assertEquals(1, skips)
    }

    /** 收尾页不摆「跳过」：它与「开始使用」是同一件事 */
    @Test
    fun doneStepHasNoSkipButton() {
        val grantedCount = mutableStateOf(0)
        setContent(
            cameraGrantedCount = { grantedCount.value },
            onRequestCamera = { grantedCount.value += 1 }
        )
        goToCameraStep()
        clickSettled("允许使用相机")
        awaitText("都设置好了")

        assertNotVisible("跳过")
    }

    /**
     * @param configSavedCount 「配置已落盘」由外部给：版式自己不认识 DataStore
     * @param cameraGrantedCount 同理，「权限已授予」也是外部的事实
     */
    private fun setContent(
        basicConfig: AiBasicConfigDraft = AiBasicConfigDraft(),
        configSavedCount: () -> Int = { 0 },
        cameraGrantedCount: () -> Int = { 0 },
        cameraPermissionBlocked: Boolean = false,
        onSaveConfig: () -> Unit = {},
        onRequestCamera: () -> Unit = {},
        onOpenCameraSettings: () -> Unit = {},
        onSkip: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme {
                WelcomeSteps(
                    basicConfig = basicConfig,
                    configSavedCount = configSavedCount(),
                    cameraGrantedCount = cameraGrantedCount(),
                    cameraPermissionBlocked = cameraPermissionBlocked,
                    onBaseUrlChange = {},
                    onModelNameChange = {},
                    onApiKeyChange = {},
                    onSaveConfig = onSaveConfig,
                    onRequestCamera = onRequestCamera,
                    onOpenCameraSettings = onOpenCameraSettings,
                    onSkip = onSkip
                )
            }
        }
    }

    /**
     * 点一颗按钮。先等界面落定再点：翻页动画还没演完时相邻页也在组合中，同一段文案会同时挂在
     * 两页上（「下一步」每页都有），直接按文案找会撞上「匹配到多个节点」。
     */
    private fun clickSettled(text: String) {
        // 等到这一屏上只剩一份该文案：翻页动画没演完时相邻页也在组合里，同一段文案会同时挂在
        // 两页上，按文案找会撞上「匹配到多个节点」
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText(text).performClick()
    }

    private fun goToConfigStep() {
        clickSettled("下一步")
        awaitText("标签上的字交给模型读")
        clickSettled("下一步")
        awaitText("填上你的模型")
        // 翻页是动画，没落定之前相邻页也在组合里（同一段文案会同时挂在两页上、其中一份在屏幕外），
        // 所以走到位之后要等它停稳，后面的断言才不会撞上屏幕外那一份
        composeRule.waitForIdle()
    }

    private fun goToCameraStep() {
        goToConfigStep()
        clickSettled("跳过")
        awaitText("扫描时要用到相机")
        composeRule.waitForIdle()
    }

    /** 翻页是动画，等到目标文案出现为止；等的是结果不是时长 */
    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 这一屏上看不到这段文字。
     * 判的是「显没显示」而不是「存不存在」：pager 会把相邻页也组合进视口外，按存在算。
     */
    private fun assertNotVisible(text: String) {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val onScreen = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().any { node ->
            val bounds = node.boundsInRoot
            bounds.width > 0f && bounds.left >= root.left && bounds.right <= root.right
        }
        assertTrue("这段文字不该出现在当前这一屏上：$text", !onScreen)
    }

    private companion object {
        /** 页内状态切换时长的一半（见 FreshNowTransitions.stateChange 的 200ms） */
        const val HALF_STATE_CHANGE_MS = 100L

        /** 进度指示器在语义树里就是「带范围信息的节点」，M3 的 LinearProgressIndicator 自带 */
        val progressBar = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    }
}
