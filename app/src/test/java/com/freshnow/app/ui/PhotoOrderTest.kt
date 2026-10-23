package com.freshnow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「把第几张挪到最前」。编辑页与扫描页共用这一条判据：设为首图就是把那张挪到第一位，
 * 其余保持原序——顺序本身就是这一栏的全部含义（列表缩略图与详情页主图都取第一张）。
 */
class PhotoOrderTest {

    @Test
    fun movesTheChosenItemToTheFrontKeepingTheRest() {
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").movedToFront(2))
    }

    @Test
    fun firstItemStaysPut() {
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").movedToFront(0))
    }

    /** 越界（理论上点不到，但下标来自界面）不能抛异常：返回原列表即可 */
    @Test
    fun outOfRangeKeepsTheListAsIs() {
        assertEquals(listOf("a", "b"), listOf("a", "b").movedToFront(5))
        assertEquals(listOf("a", "b"), listOf("a", "b").movedToFront(-1))
        assertEquals(emptyList<String>(), emptyList<String>().movedToFront(0))
    }
}
