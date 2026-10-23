package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.component.StepDirection
import com.freshnow.app.ui.component.StepTransition
import com.freshnow.app.ui.theme.FreshNowTheme
import com.freshnow.app.ui.theme.FreshNowTransitions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 编辑页三个整屏状态之间的转场。
 *
 * 要验的是「这不是硬切」：转场中途新旧两页必须同时在（旧的还在退场、新的已经进来），而硬切的那一帧
 * 只会剩一个。真机上几百毫秒的窗口抢不到截图，所以这里把动画时钟握在手里逐帧推。
 *
 * 只看节点在不在，不看是否落在屏幕内：退场中的那一页正在位移，位置本就不该被钉住。
 */
@RunWith(AndroidJUnit4::class)
class EditStepTransitionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteringCropKeepsBothStepsWhileTransitionRuns() {
        var step by mutableStateOf<EditStep>(EditStep.Edit)
        setContent { step }

        composeRule.onNodeWithText(EDIT).assertExists()

        step = EditStep.Crop(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        // 推进到转场中途
        composeRule.mainClock.advanceTimeBy(TRANSITION_MID_MS)
        composeRule.onNodeWithText(EDIT).assertExists()
        composeRule.onNodeWithText(CROP).assertExists()

        // 走完转场，旧页收干净
        composeRule.mainClock.advanceTimeBy(TRANSITION_MS * 3)
        composeRule.onNodeWithText(CROP).assertExists()
        composeRule.onNodeWithText(EDIT).assertDoesNotExist()
    }

    @Test
    fun leavingCropKeepsBothStepsWhileTransitionRuns() {
        var step by mutableStateOf<EditStep>(
            EditStep.Crop(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        )
        setContent { step }

        composeRule.onNodeWithText(CROP).assertExists()

        step = EditStep.Edit
        composeRule.mainClock.advanceTimeBy(TRANSITION_MID_MS)
        composeRule.onNodeWithText(CROP).assertExists()
        composeRule.onNodeWithText(EDIT).assertExists()

        composeRule.mainClock.advanceTimeBy(TRANSITION_MS * 3)
        composeRule.onNodeWithText(EDIT).assertExists()
        composeRule.onNodeWithText(CROP).assertDoesNotExist()
    }

    /**
     * 时钟握在手里（autoAdvance = false）：不推它转场就停在起始帧，「两页同时在」才断言得到。
     */
    private fun setContent(step: () -> EditStep) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreshNowTheme() {
                // 与编辑页里用的是同一套方向判据（见 RecordEditScreen）：回到表单是退回方向
                StepTransition(
                    step = step(),
                    direction = { from, to ->
                        when {
                            to is EditStep.Edit -> StepDirection.BACK
                            from is EditStep.Edit -> StepDirection.FORWARD
                            else -> StepDirection.SIDE
                        }
                    },
                    label = "editStep"
                ) { current ->
                    Text(text = if (current is EditStep.Crop) CROP else EDIT)
                }
            }
        }
    }

    private companion object {
        const val EDIT = "编辑表单"
        const val CROP = "裁剪照片"

        /** 与 FreshNowTransitions 的页面转场同一档 */
        val TRANSITION_MS = FreshNowTransitions.DURATION_MEDIUM2.toLong()

        /** 转场中途：两页同速，取一半就停在新旧两页都在场的那一段 */
        val TRANSITION_MID_MS = TRANSITION_MS / 2
    }
}
