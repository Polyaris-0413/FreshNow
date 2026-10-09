package com.freshnow.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import com.freshnow.app.ui.theme.FreshNowTransitions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 转场还没演完就再出发一次导航：那一下要等这一程演完，而不是把动画挤掉。
 *
 * 判据用仓库里已有的那一条（见 EditStepTransitionTest）：转场中途新旧两页同时在。硬切的那一帧
 * 只会剩一个——这就是「动画没了」在测试里的样子；真机上那一下黑闪是一两帧的事，抢不到截图。
 *
 * 时钟握在手里（autoAdvance = false）逐帧推：350ms 的窗口只有这么推才停得住。
 */
@RunWith(AndroidJUnit4::class)
class SettledNavigatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var navigator: SettledNavigator

    /** 当前没有转场在演时立刻出发：包装不能给正常路子加延迟 */
    @Test
    fun navigationWhileSettled_happensRightAway() {
        setContent()

        composeRule.runOnUiThread { navigator.navigate(B) }
        composeRule.mainClock.advanceTimeBy(TRANSITION_HALF_MS)

        // 半程就到了：若被推迟，这半程里只会剩 A
        composeRule.onNodeWithText(A).assertExists()
        composeRule.onNodeWithText(B).assertExists()
    }

    /** 转场中途的那一次导航要等这一程落定，落定之后照样是转场（不是硬切） */
    @Test
    fun navigationDuringTransition_waitsForItToSettle() {
        setContent()

        composeRule.runOnUiThread { navigator.navigate(B) }
        composeRule.mainClock.advanceTimeBy(TRANSITION_HALF_MS)
        composeRule.onNodeWithText(B).assertExists()

        // 这一程还没演完就要去 C：不插队
        composeRule.runOnUiThread { navigator.navigate(C) }
        composeRule.mainClock.advanceTimeBy(TRANSITION_HALF_MS)
        composeRule.onNodeWithText(C).assertDoesNotExist()

        // 走完 A→B，B 落定之后才轮到 C。推到 C 刚出现的那一帧：此刻 B 必须还在场
        // （硬切的话 B 会当场消失，新页一帧到位）
        awaitDestination(C)
        composeRule.onNodeWithText(B).assertExists()

        // 再走完，旧的收干净
        composeRule.mainClock.advanceTimeBy(TRANSITION_MS * 2)
        composeRule.onNodeWithText(C).assertExists()
        composeRule.onNodeWithText(B).assertDoesNotExist()
    }

    /**
     * 同一程里的第二下不排队：排队的话「连点两下前进」会前进两页。
     *
     * 计数用 [NavHostController.currentBackStack]，它把导航图那一层也算作一条（起点 + 图 + C）。
     */
    @Test
    fun repeatedRequestsDuringOneTransition_doNotQueue() {
        setContent()

        composeRule.runOnUiThread { navigator.navigate(B) }
        composeRule.mainClock.advanceTimeBy(TRANSITION_HALF_MS)
        composeRule.runOnUiThread { navigator.navigate(C) }
        composeRule.runOnUiThread { navigator.navigate(C) }
        composeRule.mainClock.advanceTimeBy(TRANSITION_MS * 4)

        composeRule.onNodeWithText(C).assertExists()
        assertEquals(
            "只该走一程：栈里应当是 导航图 + 起点 + B + C，C 只有一份",
            listOf(null, A, B, C),
            navController.currentBackStack.value.map { it.destination.route }
        )
    }

    /**
     * 一帧一帧推到 [text] 那一页出现为止。
     *
     * 不按「推多久」写死：转场结束、延迟那一程出发、新页开始进场，这几步各有一两帧的调度延迟，
     * 写死时长就会把断言卡在边界上。
     */
    private fun awaitDestination(text: String) {
        repeat(60) {
            if (composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            composeRule.mainClock.advanceTimeBy(16)
        }
        throw AssertionError("等了 60 帧也没等到「$text」")
    }

    private fun setContent() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            navController = rememberNavController()
            navigator = rememberSettledNavigator(navController)
            FreshNowTheme(dynamicColor = false) {
                NavHost(
                    navController = navController,
                    startDestination = A,
                    modifier = Modifier.fillMaxSize(),
                    // 与 FreshNowNavHost 同一套转场，判据才落在真东西上
                    enterTransition = { FreshNowTransitions.forwardEnter },
                    exitTransition = { FreshNowTransitions.forwardExit },
                    popEnterTransition = { FreshNowTransitions.backEnter },
                    popExitTransition = { FreshNowTransitions.backExit }
                ) {
                    composable(A) { Text(A) }
                    composable(B) { Text(B) }
                    composable(C) { Text(C) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val A = "第一页"
        const val B = "第二页"
        const val C = "第三页"

        /** 与 FreshNowTransitions 的页面切换同一档（350ms） */
        const val TRANSITION_MS = 350L
        const val TRANSITION_HALF_MS = TRANSITION_MS / 2
    }
}
