package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
        composeRule.onAllNodesWithContentDescription(REMOVE).assertCountEquals(MAX_RECORD_IMAGES)
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
        composeRule.onAllNodesWithContentDescription(REMOVE).assertCountEquals(0)
    }

    /** 点 ✕ 报的是那一格的序号（0 起），不是别的格 */
    @Test
    fun removeReportsTheTappedIndex() {
        var removed = -1
        setContent(
            items = (1..3).map { PhotoGridItem(key = "k$it", image = null) },
            onRemove = { removed = it }
        )

        composeRule.onAllNodesWithContentDescription(REMOVE)[1].performClick()

        assertEquals("第二格的 ✕ 要报回下标 1", 1, removed)
    }

    /**
     * 长按拖动换位：把第二张拖到第一格上，松手时提交的是「1 → 0」。
     *
     * 拖动期间列表不动（换位在松手那一刻一次提交），所以这一条也顺带钉住了「拖过去的路上
     * 不会来回闪」——那种闪正是因为列表在拖动中就被改了。
     */
    @Test
    fun draggingSubmitsTheNewOrderOnRelease() {
        var moved: Pair<Int, Int>? = null
        setContent(
            items = (1..2).map { PhotoGridItem(key = "k$it", image = null) },
            onMove = { from, to -> moved = from to to }
        )

        val second = composeRule.onAllNodesWithContentDescription(REMOVE)[1]
        val bounds = second.getBoundsInRoot()
        val cell = bounds.right - bounds.left
        second.performTouchInput {
            longClick()
            // 往左挪一格多一点：一格宽加一条缝
            moveBy(Offset(-(cell.toPx() + 8.dp.toPx()), 0f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals("第二张应当被移到第一格", 1 to 0, moved)
    }

    private fun setContent(
        items: List<PhotoGridItem>,
        onRemove: (Int) -> Unit = {},
        onMove: (Int, Int) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            FreshNowTheme {
                PhotoGrid(items = items, onRemove = onRemove, onMove = onMove, onAdd = {})
            }
        }
    }

    private companion object {
        const val ADD = "添加照片"
        const val REMOVE = "移除照片"
    }
}
