package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 看图的版式与手势。
 *
 * 手势没有可见的结果可以断言（缩放走 graphicsLayer，节点本身的尺寸不变），所以这里用「照片占了
 * 多少像素」来判：截图数一遍照片的颜色，捏合之后应该变多。这比只断言「没崩」有信息量。
 */
@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed = 0

    /** 单击关闭：全屏图不能点的话，用户会反复戳屏幕（微信、微博的看图页都是点一下退出） */
    @Test
    fun tappingClosesTheViewer() {
        setContent()

        composeRule.onRoot().performClick()

        // 注册了双击，单击要等过双击的超时才会到；用等结果而不是睡固定时长
        composeRule.waitUntil(timeoutMillis = 5_000) { dismissed == 1 }
    }

    @Test
    fun doubleTapEnlargesThePhoto() {
        setContent()
        val before = photoPixels()

        composeRule.onRoot().performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        val after = photoPixels()
        assertTrue("双击后照片应占更多像素：前 $before，后 $after", after > before)
    }

    @Test
    fun pinchEnlargesThePhoto() {
        setContent()
        val before = photoPixels()

        // 两指从中间往两边拉开：放大
        composeRule.onRoot().performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f),
                end0 = center + Offset(-400f, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(400f, 0f)
            )
        }
        composeRule.waitForIdle()

        val after = photoPixels()
        assertTrue("捏合后照片应占更多像素：前 $before，后 $after", after > before)
    }

    /** 截图里照片那种颜色的像素个数 */
    private fun photoPixels(): Int {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y] == PHOTO_COLOR) count++
            }
        }
        return count
    }

    private fun setContent() {
        composeRule.setContent {
            FreshNowTheme {
                PhotoViewer(image = photo, onDismiss = { dismissed += 1 })
            }
        }
    }

    private companion object {
        /** 宽高 2:1：竖屏里按 Fit 铺开是一条横带，放大时它会长高，像素数因此会变 */
        val PHOTO_COLOR = Color.Red
        val photo = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
    }
}
