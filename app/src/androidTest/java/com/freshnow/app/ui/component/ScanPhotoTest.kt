package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 照片块在「有图」与「没图」时占同一块地方：两种情形都是正方形。
 *
 * 判据取「宽高相等」而不是某个具体 dp：宽度是随屏幕铺满的，高度跟着宽度走，
 * 钉住具体数值只会把版式写死。容差是给像素取整留的。
 */
@RunWith(AndroidJUnit4::class)
class ScanPhotoTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 没图时的占位块 */
    @Test
    fun placeholderIsSquare() {
        setContent(image = null)

        assertSquare(composeRule.onNodeWithContentDescription("无图片").getBoundsInRoot())
    }

    /** 有图时的照片块（图片本身在落盘前已裁方，见 ScanImageCodec） */
    @Test
    fun photoIsSquare() {
        setContent(image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))

        assertSquare(composeRule.onNodeWithContentDescription("扫描照片").getBoundsInRoot())
    }

    private fun assertSquare(bounds: androidx.compose.ui.unit.DpRect) {
        assertEquals(
            "照片块应当是正方形：w=${bounds.width} h=${bounds.height}",
            bounds.width.value,
            bounds.height.value,
            // 宽度与高度都由同一个尺寸推出来，差别只可能来自取整
            1f
        )
    }

    private fun setContent(image: Bitmap?) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                ScanPhoto(image = image)
            }
        }
    }
}
