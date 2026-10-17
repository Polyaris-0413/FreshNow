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
 * 移除照片面板要先收起、再动手。
 *
 * 直接把这页从组合里拿掉（不等收起动画）会连带丢掉两件东西：那一下收起动画根本没机会播；
 * 同一个 SheetState 停在展开态，下次打开时 `show()` 立刻完成——弹入动画就此消失。
 * 用户看到的是「删掉一张、马上点另一张，第二片面板是突然冒出来的」。
 *
 * 时钟握在手里：要验的正是「动手发生在收起动画之后」，而不是「点了就一定回调」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class RemovePhotoSheetDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmingWaitsForTheSheetToHideBeforeReporting() {        composeRule.mainClock.autoAdvance = false
        var confirmed = 0
        lateinit var sheetState: SheetState

        composeRule.setContent {
            FreshNowTheme {
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    RemovePhotoSheet(
                        sheetState = sheetState,
                        onDismissRequest = { visible = false },
                        onConfirm = { confirmed++ }
                    )
                }
            }
        }
        // 面板弹出来
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("移除照片").performClick()

        assertEquals("收起动画还没播完，动作不该已经发生", 0, confirmed)

        composeRule.mainClock.advanceTimeBy(2_000)

        assertEquals("收起之后动作要发生，且只发生一次", 1, confirmed)
        assertEquals(
            "面板收起要落到 Hidden，否则下次打开没有弹入动画",
            SheetValue.Hidden,
            sheetState.currentValue
        )
    }

    /**
     * 用户报的那一幕：删掉一张、马上点另一张，第二片面板直接蹦出来。
     *
     * 同一个 SheetState 被两次打开复用（与页面里一致）；第一次收起之后它必须已经归位，
     * 第二次打开才能从头播弹入动画——时钟没往前推时它应当还在 Hidden，而不是已经 Expanded。
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
                    RemovePhotoSheet(
                        sheetState = sheetState,
                        onDismissRequest = { showSheet = false },
                        onConfirm = {}
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("移除照片").performClick()
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
}
