package com.freshnow.app.ui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「满九张时 + 消失」要看得见：到顶了还画着加号，用户按下去没反应，只能怀疑是自己点错了。
 *
 * 一格里有两个动作，报的都必须是被碰的那一格：点开看的是别一张、长按删的是别一张，
 * 都是「我指的东西不是我拿到的」——比按不动更难理解。
 */
@RunWith(AndroidJUnit4::class)
class PhotoGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addTileDisappearsAtTheLimit() {
        setContent(items = (1..MAX_RECORD_IMAGES).map { PhotoGridItem(key = "k$it", image = null) })

        composeRule.onNodeWithContentDescription(ADD).assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription(TILE).assertCountEquals(MAX_RECORD_IMAGES)
    }

    @Test
    fun addTileShowsBeforeTheLimit() {
        setContent(items = (1..6).map { PhotoGridItem(key = "k$it", image = null) })

        composeRule.onNodeWithContentDescription(ADD).assertIsDisplayed()
    }

    /** 一张都没有时也要有「+」，否则新记录无从开始加图 */
    @Test
    fun emptyGridStillOffersAdd() {
        setContent(items = emptyList())

        composeRule.onNodeWithContentDescription(ADD).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(TILE).assertCountEquals(0)
    }

    @Test
    fun tapOpensTheTappedIndex() {
        var opened = -1
        setContent(
            items = (1..3).map { PhotoGridItem(key = "k$it", image = null) },
            onOpen = { opened = it }
        )

        composeRule.onAllNodesWithContentDescription(TILE)[1].performClick()

        assertEquals("第二格要报回下标 1", 1, opened)
    }

    @Test
    fun longPressOffersActionsForTheTappedIndex() {
        var actions = -1
        var opened = -1
        setContent(
            items = (1..3).map { PhotoGridItem(key = "k$it", image = null) },
            onOpen = { opened = it },
            onActions = { actions = it }
        )

        composeRule.onAllNodesWithContentDescription(TILE)[2].performTouchInput { longClick() }

        assertEquals("第三格要报回下标 2", 2, actions)
        assertEquals("长按不该同时当成点击", -1, opened)
    }

    private fun setContent(
        items: List<PhotoGridItem>,
        onOpen: (Int) -> Unit = {},
        onActions: (Int) -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme {
                PhotoGrid(items = items, onOpen = onOpen, onActions = onActions, onAdd = {})
            }
        }
    }

    private companion object {
        const val ADD = "添加照片"
        /** 格子本身的描述：测试里都不给位图，所以是占位图那一档 */
        const val TILE = "无图片"
    }
}
