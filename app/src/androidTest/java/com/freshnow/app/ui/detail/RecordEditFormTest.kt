package com.freshnow.app.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 编辑表单：四项都在、改动能传出去、过期日期的来源交代清楚。
 *
 * 只摆表单本体（不含读库与落盘）：走真的 RecordEditScreen 要连数据库一起测进来，
 * 而这里要盯的是「留空即跟随推算、填了值就以它为准」这条关系在界面上说得清不清楚。
 */
@RunWith(AndroidJUnit4::class)
class RecordEditFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 四项一项都不能少：编辑页的意义就是把这四项都改一遍 */
    @Test
    fun showsEveryEditableField() {
        setContent()

        listOf("名称", "生产日期", "保质期", "过期日期").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    /** 输入要能传到调用方，否则字段只是个摆设 */
    @Test
    fun typingReportsNewValue() {
        val changed = mutableListOf<String>()
        setContent(uiState = EMPTY, onProductNameChange = { changed += it })

        composeRule.onNodeWithText("名称").performTextInput("牛奶")

        assertEquals(listOf("牛奶"), changed)
    }

    /** 过期日期留空时，推算结果显示在同一屏里——这正是编辑页存在的理由 */
    @Test
    fun blankExpiryShowsDerivedDate() {
        setContent(uiState = EMPTY.copy(derivedExpiry = ExpiryOutcome.Resolved("2027-04-01")))

        composeRule.onNodeWithText("2027-04-01", substring = true).assertIsDisplayed()
    }

    /** 填了值就是标签印刷值，以它为准，推算提示收起来免得两种说法并存 */
    @Test
    fun filledExpiryHidesDerivedHint() {
        setContent(
            uiState = EMPTY.copy(
                expiryDate = "2027-05-01",
                derivedExpiry = ExpiryOutcome.Resolved("2027-04-01")
            )
        )

        composeRule.onNodeWithText("留空则按生产日期与保质期推算", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("2027-04-01", substring = true).assertDoesNotExist()
    }

    /** 推算不出来时要说清是缺项，还是保质期认不出写法，不能拿一句「无法推算」把两种原因混成一种 */
    @Test
    fun unparseableShelfLifeSaysSo() {
        setContent(uiState = EMPTY.copy(derivedExpiry = ExpiryOutcome.UnparseableShelfLife))

        composeRule.onNodeWithText("保质期认不出写法", substring = true).assertIsDisplayed()
    }

    @Test
    fun missingInputSaysSo() {
        setContent(uiState = EMPTY.copy(derivedExpiry = ExpiryOutcome.InsufficientInput))

        composeRule.onNodeWithText("缺了其中一项", substring = true).assertIsDisplayed()
    }

    private fun setContent(
        uiState: RecordEditUiState = EMPTY,
        onProductNameChange: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                RecordEditForm(
                    uiState = uiState,
                    onProductNameChange = onProductNameChange,
                    onProductionDateChange = {},
                    onExpiryDateChange = {},
                    onShelfLifeChange = {}
                )
            }
        }
    }

    private companion object {
        val EMPTY = RecordEditUiState(loaded = true, found = true)
    }
}
