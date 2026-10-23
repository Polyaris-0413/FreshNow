package com.freshnow.app.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 照片块在「有图」与「没图」时占同一块地方：两种情形都是正方形。
 * 判据取「宽高相等」而不是某个具体 dp：宽度随屏幕铺满、高度跟着宽度走，钉住数值只会把版式写死；
 * 容差是给像素取整留的。
 * 量的必须是**整块**：语义挂在占位块上（见 ScanPhoto），若改挂到里面那枚 48dp 图标上，这里量到的就成了
 * 图标——无论占位块多高都会通过，这条断言也就没了咬合力。
 * 出现方式的判据见 ScanPhoto：只有「移除照片」这一向演淡化（它是唯一没被转场盖住的一向），
 * 占位换成照片直接切。
 */
@RunWith(AndroidJUnit4::class)
class ScanPhotoTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun placeholderIsSquare() {
        setContent { null }

        assertSquare(composeRule.onNodeWithContentDescription(MISSING).getBoundsInRoot())
    }

    /** 有图时的照片块（图片本身在落盘前已裁方，见 ScanImageCodec） */
    @Test
    fun photoIsSquare() {
        setContent { square(Color.RED) }

        assertSquare(composeRule.onNodeWithContentDescription(PHOTO).getBoundsInRoot())
    }

    /**
     * 编辑页「移除照片」是淡化，不是硬切。
     *
     * 判据取「中途两段同时在」：硬切的实现中间没有一帧能同时看到两段。时钟捏在手里才停得住
     * 这个只持续一档页内状态切换（见 FreshNowTransitions.stateChange）的中间态。
     */
    @Test
    fun removalCrossFadesInsteadOfHardCut() {
        var image by mutableStateOf<Bitmap?>(square(Color.RED))
        setContent { image }

        composeRule.mainClock.autoAdvance = false
        image = null
        composeRule.mainClock.advanceTimeBy(FADE_MS / 2)

        composeRule.onNodeWithContentDescription(MISSING).assertExists()
        composeRule.onNodeWithContentDescription(PHOTO).assertExists()

        composeRule.mainClock.advanceTimeBy(FADE_MS * 3)

        composeRule.onNodeWithContentDescription(MISSING).assertExists()
        composeRule.onNodeWithContentDescription(PHOTO).assertDoesNotExist()
    }

    /**
     * 占位换成照片是直接切，不演淡化。
     *
     * 这一向都在整页转场里（进详情页、从裁剪或拍照步骤返回），叠一层淡化也看不见；更要紧的是
     * 照片解码只要十几毫秒，淡化会把一段看不见的等待拉长成看得见的——见 ScanPhoto 的注释。
     * 这条是反向判据：推过一整档淡化还有余量，谁把这一向改回淡化，这里就会红。
     */
    @Test
    fun arrivalIsNotFaded() {
        var image by mutableStateOf<Bitmap?>(null)
        setContent { image }

        composeRule.mainClock.autoAdvance = false
        image = square(Color.RED)
        composeRule.mainClock.advanceTimeBy(FRAME_MS * SAMPLE_FRAMES)

        val arrival = photoPixels()
        assertNotNull("占位换成照片时，这一拍就该有图", arrival)
        composeRule.mainClock.advanceTimeBy(WATCH_MS)

        assertArrayEquals("出现那一拍就该是终态，中间不该有淡化", arrival, photoPixels())
        composeRule.onNodeWithContentDescription(MISSING).assertDoesNotExist()
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

    /**
     * 照片那一块的像素；还没图返回 null。
     * 用 IntArray 不用 List：铺满宽的百万级像素装箱会把测试自己 OOM 掉。
     */
    private fun photoPixels(): IntArray? {
        if (composeRule.onAllNodesWithContentDescription(PHOTO).fetchSemanticsNodes().isEmpty()) {
            return null
        }
        return composeRule.onNodeWithContentDescription(PHOTO).captureToImage().toPixelMap().buffer
    }

    /** 每次调用都是一张新的位图：换照片那条靠的就是「不是同一个对象」 */
    private fun square(color: Int): Bitmap =
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun setContent(image: () -> Bitmap?) {
        composeRule.setContent {
            FreshNowTheme() {
                ScanPhoto(image = image())
            }
        }
    }

    private companion object {
        const val MISSING = "无图片"
        const val PHOTO = "扫描照片"
        /** 与 FreshNowTransitions.stateChange() 同一档 */
        const val FADE_MS = 200L
        const val FRAME_MS = 16L

        /**
         * 取样从第二帧起，不是第一帧。
         *
         * Crossfade 给新进来那段内容的第一帧总是从 alpha 0 起走（snap 只让它在下一帧到位）：那一帧
         * 露出的是这一格自己的底色，落在眼里是一帧空白，认不出来；而真正的淡化两帧后还在半路
         * （约 alpha 0.16），[arrivalIsNotFaded] 照样红。
         */
        const val SAMPLE_FRAMES = 2L

        /** 比一档淡化长：谁给「出现」加回淡化，[arrivalIsNotFaded] 就会红 */
        const val WATCH_MS = 400L
    }
}
