package com.freshnow.app.ui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 九宫格：能放几格、到顶了会怎样、点哪一格报哪一格。
 *
 * 「满九张时 + 消失」要看得见：到顶了还画着加号，用户按下去没反应，只能怀疑是自己点错了。
 *
 * 删除报的是被点那一格的序号——报错一位就会删掉别的照片，而这是这一屏里唯一破坏性的动作。
 */
@RunWith(AndroidJUnit4::class)
class PhotoGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 满九张时「+」必须消失 */
    @Test
    fun addTileDisappearsAtTheLimit() {
        setContent(items = (1..MAX_RECORD_IMAGES).map { PhotoGridItem(key = "k$it", image = null) })

        composeRule.onNodeWithContentDescription(ADD).assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription(TILE).assertCountEquals(MAX_RECORD_IMAGES)
    }

    /** 六张时还留着一格「+」 */
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

    /** 点某一格报的是那一格的序号（0 起），不是别的格 */
    @Test
    fun removeReportsTheTappedIndex() {
        var removed = -1
        setContent(
            items = (1..3).map { PhotoGridItem(key = "k$it", image = null) },
            onRemove = { removed = it }
        )

        composeRule.onAllNodesWithContentDescription(TILE)[1].performClick()

        assertEquals("第二格要报回下标 1", 1, removed)
    }

    private fun setContent(
        items: List<PhotoGridItem>,
        onRemove: (Int) -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme {
                PhotoGrid(items = items, onRemove = onRemove, onAdd = {})
            }
        }
    }

    private companion object {
        const val ADD = "添加照片"
        /** 格子本身的描述：测试里都不给位图，所以是占位图那一档 */
        const val TILE = "无图片"
    }
}
