package com.freshnow.app.ui.scan

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanActionBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 还没扫到内容时两个入口必须置灰：此时点保存等于存一条空记录 */
    @Test
    fun nothingScanned_disablesBothActions() {
        setContent(enabled = false)

        composeRule.onNodeWithText("清空").assertIsNotEnabled()
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    /**
     * 开关打开时那颗按钮叫「下一步」：那一按下去还要接着填位置，写着「保存」会让人以为这一步完了。
     * 关着时它就是「保存」——两边的说法都不能少，否则用户从按钮上看不出接下来还有没有事
     */
    @Test
    fun primaryButtonSaysWhatComesNext() {
        setContent(enabled = true)

        composeRule.onNodeWithText("下一步").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertDoesNotExist()
    }

    @Test
    fun hasScannedResult_actionsCanBeTriggered() {
        var cleared = 0
        var saved = 0
        setContent(enabled = true, onClear = { cleared++ }, onNextStep = { saved++ })

        composeRule.onNodeWithText("清空").assertIsEnabled().performClick()
        composeRule.onNodeWithText("保存").assertIsEnabled().performClick()

        assertEquals(1, cleared)
        assertEquals(1, saved)
    }

    private fun setContent(
        enabled: Boolean,
        onClear: () -> Unit = {},
        onNextStep: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme() {
                ScanActionBar(
                    enabled = enabled,
                    onClear = onClear,
                    onNextStep = onNextStep
                )
            }
        }
    }
}
