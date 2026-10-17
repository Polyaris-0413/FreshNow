package com.freshnow.app.ui.component

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
 * 「这次的内容还没存」那一问：正文说的是代价（会丢），两个出入口分别是「保存」与「不保存」。
 *
 * 出现时机由各页自己判（编辑页那条判据在 JVM 测试里逐条盖过，见 RecordEditUnsavedChangesTest），
 * 这里验的是组件本身的三个出入口各自通到哪儿。文案由调用方给，所以扫描页那份也从这里过一遍。
 */
@RunWith(AndroidJUnit4::class)
class LeaveDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsQuestionAndBothActions() {
        setContent()

        composeRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(MESSAGE).assertIsDisplayed()
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

    /** 扫描页那份文案也走这个组件：两处问的是同一件事，只是页面名不同 */
    @Test
    fun scanWordingShowsTheSameActions() {
        val title = context.getString(R.string.scan_save_dialog_title)
        setContent(title = title, message = context.getString(R.string.scan_save_dialog_message))

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsEnabled()
        composeRule.onNodeWithText("不保存").assertIsEnabled()
    }

    private fun setContent(
        title: String = TITLE,
        message: String = MESSAGE,
        canSave: Boolean = true,
        onSave: () -> Unit = {},
        onDiscard: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme {
                LeaveDialog(
                    title = title,
                    message = message,
                    onSave = onSave,
                    onDiscard = onDiscard,
                    onDismiss = onDismiss,
                    canSave = canSave
                )
            }
        }
    }

    private companion object {
        const val TITLE = "离开此页？"
        const val MESSAGE = "本次输入尚未保存。"
    }
}
