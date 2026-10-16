package com.freshnow.app.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 补充信息页的表单：位置在、改动能传出去。
 *
 * 只摆表单本体，不含读库与落盘——写入那一段在 RecordSupplementSaveTest 里走真库验。
 */
@RunWith(AndroidJUnit4::class)
class RecordSupplementFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheLocationField() {
        setContent()

        composeRule.onNodeWithText("位置").assertIsDisplayed()
    }

    /** 输入要能传到调用方，否则这个字段只是个摆设 */
    @Test
    fun typingReportsNewValue() {
        val changed = mutableListOf<String>()
        setContent(onLocationChange = { changed += it })

        composeRule.onNodeWithText("位置").performTextInput("冰箱冷藏室")

        assertEquals(listOf("冰箱冷藏室"), changed)
    }

    /** 已填过的位置要显示出来：进来一眼看见现在写的是什么，而不是一个空框 */
    @Test
    fun showsTheStoredValue() {
        setContent(uiState = RecordSupplementUiState(loaded = true, found = true, location = "冰箱冷冻室"))

        composeRule.onNodeWithText("冰箱冷冻室").assertIsDisplayed()
    }

    private fun setContent(
        uiState: RecordSupplementUiState = RecordSupplementUiState(loaded = true, found = true),
        onLocationChange: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme() {
                RecordSupplementForm(uiState = uiState, onLocationChange = onLocationChange)
            }
        }
    }
}
