package com.freshnow.app.ui.scan

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

    @Test
    fun hasScannedResult_actionsCanBeTriggered() {
        var cleared = 0
        var saved = 0
        setContent(enabled = true, onClear = { cleared++ }, onSave = { saved++ })

        composeRule.onNodeWithText("清空").assertIsEnabled().performClick()
        composeRule.onNodeWithText("保存").assertIsEnabled().performClick()

        assertEquals(1, cleared)
        assertEquals(1, saved)
    }

    private fun setContent(
        enabled: Boolean,
        onClear: () -> Unit = {},
        onSave: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                ScanActionBar(enabled = enabled, onClear = onClear, onSave = onSave)
            }
        }
    }
}
