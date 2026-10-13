package com.freshnow.app.ui.scan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 取景框左下角那个状态胶囊说什么。三种取值互斥，其中「没什么可说」必须真的什么都不显示——
 * 空着一个实心胶囊摆在那儿，比不显示更像出了故障。
 */
@RunWith(AndroidJUnit4::class)
class ScanStatusChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recognizingShowsTheWaitWord() {
        setContent(ScanStatus.Recognizing)

        composeRule.onNodeWithText("正在识别…").assertIsDisplayed()
    }

    @Test
    fun foundShowsHowManyFieldsAreFilled() {
        setContent(ScanStatus.Found(3))

        composeRule.onNodeWithText("已读到 3 项").assertIsDisplayed()
    }

    @Test
    fun hiddenShowsNoWordAtAll() {
        setContent(ScanStatus.Hidden)

        composeRule.onNodeWithText("正在识别…").assertDoesNotExist()
        composeRule.onAllNodesWithText("已读到", substring = true).assertCountEquals(0)
    }

    private fun setContent(status: ScanStatus) {
        composeRule.setContent {
            FreshNowTheme() {
                ScanStatusChip(status = status)
            }
        }
    }
}
