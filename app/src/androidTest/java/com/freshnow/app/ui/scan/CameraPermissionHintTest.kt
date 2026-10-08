package com.freshnow.app.ui.scan

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
 * 没有相机权限时的空态：两种形态文案与按钮各不同，动作都由调用方给。
 *
 * 只摆空态本体：它出现在取景框里，而「什么时候算永久拒绝」由 ScanScreen 按系统接口判定
 * （要复现得把系统弹窗连拒两次），这里只验形态。
 */
@RunWith(AndroidJUnit4::class)
class CameraPermissionHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 还没拒过：文案说需要权限，按钮是「授予权限」，点下去是再请求一次 */
    @Test
    fun notBlocked_asksForPermission() {
        var granted = 0
        setContent(onGrantPermission = { granted++ })

        composeRule.onNodeWithText("需要相机权限才能开始扫描").assertIsDisplayed()
        composeRule.onNodeWithText("授予权限").performClick()

        assertEquals(1, granted)
    }

    /**
     * 系统弹窗已经不会再出现：文案得说清是「已被拒绝、要去系统设置」，否则用户点按钮发现没反应，
     * 只会以为坏了；按钮随之换成去设置
     */
    @Test
    fun blocked_pointsAtSystemSettings() {
        var granted = 0
        setContent(permissionBlocked = true, onGrantPermission = { granted++ })

        composeRule.onNodeWithText("相机权限已被拒绝，请到系统设置里开启").assertIsDisplayed()
        composeRule.onNodeWithText("授予权限").assertDoesNotExist()
        composeRule.onNodeWithText("去设置").performClick()

        // 被拒绝时点击走的仍是同一个动作位，由调用方决定跳设置页还是再请求
        assertEquals(1, granted)
    }

    private fun setContent(
        permissionBlocked: Boolean = false,
        onGrantPermission: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                CameraPermissionHint(
                    permissionBlocked = permissionBlocked,
                    onGrantPermission = onGrantPermission
                )
            }
        }
    }
}
