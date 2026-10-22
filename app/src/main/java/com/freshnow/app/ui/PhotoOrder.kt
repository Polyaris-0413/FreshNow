package com.freshnow.app.ui

/**
 * 把第 [index] 项挪到最前，其余保持原序。
 *
 * 「用作首图」就是这一件事：照片那一栏的含义全在顺序里（列表缩略图与详情页主图都取第一张），
 * 而草稿只是一个列表。编辑页与扫描页共用这一条，两处才不会一处挪动、一处交换。
 *
 * 下标来自界面，可能越界；越界或本来就在最前时原样返回——不抛异常，也不把顺序抖乱。
 */
internal fun <T> List<T>.movedToFront(index: Int): List<T> {
    if (index !in indices || index == 0) return this
    return listOf(this[index]) + filterIndexed { i, _ -> i != index }
}
