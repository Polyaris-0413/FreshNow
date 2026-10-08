package com.freshnow.app.ui.component

import androidx.compose.ui.test.assertIsDisplayed
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
 * 发现新版本对话框：版本号要看得见，两条出路都要能按。
 *
 * 版本号是本对话框里唯一让用户判断「值不值得去下载」的信息，缺了它这句提示等于没说；
 * 两个按钮分别对应「去下载」与「关闭」，各自的后续（浏览器、记住忽略）由调用方接上。
 */
@RunWith(AndroidJUnit4::class)
class UpdateDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsVersionAndBothActions() {
        setContent()

        composeRule.onNodeWithText("发现新版本").assertIsDisplayed()
        composeRule.onNodeWithText("v1.0.1", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("下载").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").assertIsDisplayed()
    }

    @Test
    fun downloadReportsClick() {
        var downloads = 0
        setContent(onDownload = { downloads++ })

        composeRule.onNodeWithText("下载").performClick()

        assertEquals(1, downloads)
    }

    @Test
    fun closeReportsClick() {
        var closed = 0
        setContent(onDismiss = { closed++ })

        composeRule.onNodeWithText("关闭").performClick()

        assertEquals(1, closed)
    }

    private fun setContent(
        onDownload: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                UpdateDialog(version = "v1.0.1", onDownload = onDownload, onDismiss = onDismiss)
            }
        }
    }
}
