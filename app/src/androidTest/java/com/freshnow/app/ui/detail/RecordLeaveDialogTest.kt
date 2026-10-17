package com.freshnow.app.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 编辑记录页返回时的「这次输入还没存」那一问。
 *
 * 只摆对话框本体：出现时机由 `isDirtyAgainst` 判（那条判据在 JVM 测试里逐条盖过，
 * 见 RecordEditUnsavedChangesTest），设备上要验的是三个出入口各自通到哪儿。
 */
@RunWith(AndroidJUnit4::class)
class RecordLeaveDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 正文说的是代价（这次输入会丢），两个出入口各自是「保存」与「不保存」 */
    @Test
    fun showsQuestionAndBothActions() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.record_leave_dialog_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.record_leave_dialog_message)).assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsEnabled()
        composeRule.onNodeWithText("不保存").assertIsEnabled()
    }

    /** 新记录一项都没填、或日期认不出时，保存按钮与表单那颗一样是灰的（按下去也写不进库） */
    @Test
    fun saveIsDisabledWhenNothingCanBeSaved() {
        setContent(canSave = false)

        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test
    fun saveOnlyReportsBack() {
        var saved = 0
        setContent(onSave = { saved++ })

        composeRule.onNodeWithText("保存").performClick()

        assertEquals(1, saved)
    }

    @Test
    fun discardOnlyReportsBack() {
        var discarded = 0
        setContent(onDiscard = { discarded++ })

        composeRule.onNodeWithText("不保存").performClick()

        assertEquals(1, discarded)
    }

    /** 点空白处/返回键只是收起这一问，人还留在本页——要走得自己选一个 */
    @Test
    fun dismissingKeepsUserOnPage() {
        var dismissed = 0
        var saved = 0
        var discarded = 0
        setContent(onSave = { saved++ }, onDiscard = { discarded++ }, onDismiss = { dismissed++ })

        Espresso.pressBack()

        assertEquals(1, dismissed)
        assertEquals(0, saved)
        assertEquals(0, discarded)
    }

    private fun setContent(
        canSave: Boolean = true,
        onSave: () -> Unit = {},
        onDiscard: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme {
                RecordLeaveDialog(
                    canSave = canSave,
                    onSave = onSave,
                    onDiscard = onDiscard,
                    onDismiss = onDismiss
                )
            }
        }
    }
}
