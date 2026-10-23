package com.freshnow.app.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * 照片动作面板：给哪些动作、动手之前先收起。
 * 收起不能省：直接把这页从组合里拿掉，收起动画没机会播，同一个 SheetState 停在展开态，
 * 下次打开时 `show()` 立刻完成，弹入动画就此消失（用户看到的是「删掉一张、马上点另一张，第二片面板突然冒出来」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class PhotoActionsSheetDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingFirstWaitsForTheSheetToHideBeforeReporting() {
        composeRule.mainClock.autoAdvance = false
        var setFirst = 0
        var removed = 0
        lateinit var sheetState: SheetState

        composeRule.setContent {
            FreshNowTheme {
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    PhotoActionsSheet(
                        sheetState = sheetState,
                        canSetFirst = true,
                        onDismissRequest = { visible = false },
                        onSetFirst = { setFirst++ },
                        onRemove = { removed++ }
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText(SET_FIRST).performClick()

        assertEquals("收起动画还没播完，动作不该已经发生", 0, setFirst)

        composeRule.mainClock.advanceTimeBy(2_000)

        assertEquals("收起之后动作要发生，且只发生一次", 1, setFirst)
        assertEquals("选了首图就不该顺手把照片删掉", 0, removed)
        assertEquals(
            "面板收起要落到 Hidden，否则下次打开没有弹入动画",
            SheetValue.Hidden,
            sheetState.currentValue
        )
    }

    /** 已经是首图的那一张不给「用作首图」：留着它就是一个按下去什么都不会变的动作 */
    @Test
    fun alreadyFirstOffersOnlyRemoval() {
        composeRule.setContent {
            FreshNowTheme {
                PhotoActionsSheet(
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    canSetFirst = false,
                    onDismissRequest = {},
                    onSetFirst = {},
                    onRemove = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText(SET_FIRST).assertDoesNotExist()
        composeRule.onNodeWithText(REMOVE).assertExists()
    }

    /**
     * 用户报的那一幕：删掉一张、马上点另一张，第二片面板直接蹦出来。
     * 同一个 SheetState 被复用，第一次收起后必须已归位：时钟没推进时应还在 Hidden。
     */
    @Test
    fun secondOpeningStartsFromHiddenAndAnimates() {
        composeRule.mainClock.autoAdvance = false
        var showSheet by mutableStateOf(true)
        lateinit var sheetState: SheetState

        composeRule.setContent {
            FreshNowTheme {
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                if (showSheet) {
                    PhotoActionsSheet(
                        sheetState = sheetState,
                        canSetFirst = true,
                        onDismissRequest = { showSheet = false },
                        onSetFirst = {},
                        onRemove = {}
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText(REMOVE).performClick()
        composeRule.mainClock.advanceTimeBy(2_000)

        // 再点另一张：同一个 state 重新弹一片
        showSheet = true
        composeRule.waitForIdle()

        assertEquals(
            "第二次打开时 state 必须从 Hidden 起步，否则 show() 会立刻完成、动画就没了",
            SheetValue.Hidden,
            sheetState.currentValue
        )

        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(SheetValue.Expanded, sheetState.currentValue)
    }

    private companion object {
        const val SET_FIRST = "用作首图"
        const val REMOVE = "移除照片"
    }
}
