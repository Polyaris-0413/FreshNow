package com.freshnow.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.maxContrast
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 按下快门那一下，取景框要收一下再弹回来。
 *
 * 判据取边缘上的一小块像素：收下去的时候它落在这一块之外，露出的是页面底色。于是「按下前有色、
 * 那一拍变成底色、之后又有色」才说明它收过又回来了——照原样不动的实现在中间那一拍仍然有色。
 * 缩放是绘制期的变换，语义节点的 bounds 不跟着变（量 bounds 会全程都是原尺寸），所以只能比像素。
 */
@RunWith(AndroidJUnit4::class)
class ShutterSqueezeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun squeezeShrinksThenReturns() {
        var trigger by mutableIntStateOf(0)
        composeRule.setContent {
            FreshNowTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(BOX)
                            .shutterSqueeze(trigger)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag(TAG)
                    )
                }
            }
        }

        // 取样点贴着左边往里一点：这一块收到 0.97 时，左边沿会往右挪几个 dp，正好让开这里
        val bounds = composeRule.onNodeWithTag(TAG).getBoundsInRoot()
        val probe = DpRect(
            left = bounds.left + EDGE_INSET,
            top = bounds.top + bounds.height / 3,
            right = bounds.left + EDGE_INSET + PROBE_SIZE,
            bottom = bounds.top + bounds.height / 3 + PROBE_SIZE
        )

        val before = composeRule.maxContrast(probe)
        assertTrue("按下之前这一带应当是这一块的颜色：$before", before > 0)

        composeRule.mainClock.autoAdvance = false
        trigger = 1
        composeRule.mainClock.advanceTimeBy(FRAME_MS)
        val middle = composeRule.maxContrast(probe)

        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        val after = composeRule.maxContrast(probe)
        composeRule.mainClock.autoAdvance = true

        assertEquals("按下那一拍应当已经收进去了：middle=$middle", 0, middle)
        assertTrue("收完要弹回来：after=$after", after > 0)
    }

    private companion object {
        const val TAG = "squeeze"
        val BOX = 240.dp
        /** 取样点离左边沿多近：比 0.97 挪开的半个量（3.6dp）再往里去一点，避开边沿的抗锯齿 */
        val EDGE_INSET = 0.5.dp
        val PROBE_SIZE = 1.5.dp
        const val FRAME_MS = 16L
        /** 弹簧（fastSpatial）收住约两三百毫秒，推够它 */
        const val SETTLE_MS = 800L
    }
}
