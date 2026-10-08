package com.freshnow.app.ui.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 滚动区的溢出提示：只在「还能往下滚」时出现。
 *
 * 这里摆的是真的滚动容器与真的 [androidx.compose.foundation.ScrollState]：要验的正是
 * `canScrollForward` 与显隐的对应关系，滚到底还挂着提示就是假话。
 */
@RunWith(AndroidJUnit4::class)
class ScrollEdgeFadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fadeFollowsScrollPosition() {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.height(VIEWPORT).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // 内容比视口高：初次合成时应当能继续往下滚
                        Box(modifier = Modifier.height(CONTENT).fillMaxWidth())
                    }
                    ScrollEdgeFade(
                        visible = scrollState.canScrollForward,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag(FADE_TAG)
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FADE_TAG).assertIsDisplayed()

        // 一次上滑足以滚到底（内容只比视口高这一档）
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }

        composeRule.onNodeWithTag(FADE_TAG).assertDoesNotExist()
    }

    private companion object {
        const val FADE_TAG = "scrollEdgeFade"
        val VIEWPORT = 200.dp
        val CONTENT = 320.dp
    }
}
