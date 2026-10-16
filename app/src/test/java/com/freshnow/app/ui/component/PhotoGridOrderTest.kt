package com.freshnow.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 拖动期间那份「给界面看的顺序」。
 *
 * 它是整个拖动交互的地基：手指位置与格子位置能在同一帧里对齐，全靠这份顺序是**当帧算出来**的
 * （而不是等列表更新到下一帧）。所以这里把它的边界也钉住——越界与找不到那一格时都必须原样返回，
 * 算错的表现就是「拖到边缘时跳一下」。
 */
class PhotoGridOrderTest {

    private fun items(vararg keys: String) = keys.map { PhotoGridItem(key = it, image = null) }

    @Test
    fun `往后拖时被拖的那一格占住目标位，其余依次前移`() {
        val order = items("a", "b", "c").withDraggedAt(key = "a", hoverIndex = 2)

        assertEquals(listOf("b", "c", "a"), order.map { it.key })
    }

    @Test
    fun `往前拖时同理`() {
        val order = items("a", "b", "c").withDraggedAt(key = "c", hoverIndex = 0)

        assertEquals(listOf("c", "a", "b"), order.map { it.key })
    }

    @Test
    fun `没在拖的时候顺序原样`() {
        val source = items("a", "b")

        assertEquals(listOf("a", "b"), source.withDraggedAt(key = null, hoverIndex = 1).map { it.key })
        assertEquals(listOf("a", "b"), source.withDraggedAt(key = "a", hoverIndex = null).map { it.key })
    }

    @Test
    fun `目标位越界时收到最后一格`() {
        val order = items("a", "b", "c").withDraggedAt(key = "a", hoverIndex = 99)

        assertEquals(listOf("b", "c", "a"), order.map { it.key })
    }

    @Test
    fun `找不到那一格时顺序原样`() {
        val source = items("a", "b")

        assertEquals(listOf("a", "b"), source.withDraggedAt(key = "zz", hoverIndex = 0).map { it.key })
    }
}
