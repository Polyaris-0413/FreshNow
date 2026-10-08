package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 编辑页三个整屏状态之间的转场。
 *
 * 要验的是「这不是硬切」：转场中途新旧两页必须同时在（旧的还在退场、新的已经进来），而硬切的那一帧
 * 只会剩一个。真机上 350ms 的窗口抢不到截图，所以这里把动画时钟握在手里逐帧推。
 *
 * 只看节点在不在，不看是否落在屏幕内：退场中的那一页正在位移，位置本就不该被钉住。
 */
@RunWith(AndroidJUnit4::class)
class EditStepTransitionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 从表单进裁剪页：转场期间两页都在，动画结束后只剩裁剪页 */
    @Test
    fun enteringCropKeepsBothStepsWhileTransitionRuns() {
        var step by mutableStateOf<EditStep>(EditStep.Edit)
        setContent { step }

        composeRule.onNodeWithText(EDIT).assertExists()

        step = EditStep.Crop(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        // 推进半个转场
        composeRule.mainClock.advanceTimeBy(TRANSITION_MS / 2)
        composeRule.onNodeWithText(EDIT).assertExists()
        composeRule.onNodeWithText(CROP).assertExists()

        // 走完转场，旧页收干净
        composeRule.mainClock.advanceTimeBy(TRANSITION_MS * 3)
        composeRule.onNodeWithText(CROP).assertExists()
        composeRule.onNodeWithText(EDIT).assertDoesNotExist()
    }

    /** 退回表单同样是转场而不是硬切 */
    @Test
    fun leavingCropKeepsBothStepsWhileTransitionRuns() {
        var step by mutableStateOf<EditStep>(
            EditStep.Crop(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        )
        setContent { step }

        composeRule.onNodeWithText(CROP).assertExists()

        step = EditStep.Edit
        composeRule.mainClock.advanceTimeBy(TRANSITION_MS / 2)
        composeRule.onNodeWithText(CROP).assertExists()
        composeRule.onNodeWithText(EDIT).assertExists()

        composeRule.mainClock.advanceTimeBy(TRANSITION_MS * 3)
        composeRule.onNodeWithText(EDIT).assertExists()
        composeRule.onNodeWithText(CROP).assertDoesNotExist()
    }

    /**
     * 时钟握在手里（autoAdvance = false）：不推它，转场就停在起始那一帧，
     * 于是「两页同时在」这种只持续几百毫秒的状态才断言得到。
     */
    private fun setContent(step: () -> EditStep) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                EditStepTransition(step = step()) { current ->
                    Text(text = if (current is EditStep.Crop) CROP else EDIT)
                }
            }
        }
    }

    private companion object {
        const val EDIT = "编辑表单"
        const val CROP = "裁剪照片"

        /** 与 FreshNowTransitions 的页面切换同一档（350ms） */
        const val TRANSITION_MS = 350L
    }
}
