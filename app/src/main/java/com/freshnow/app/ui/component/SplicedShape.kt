package com.freshnow.app.ui.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 拼接的意思是：同一组里的几块填充容器读起来是一个整体（外圈大圆角、相接处小圆角），
 * 块与块之间只留 [com.freshnow.app.ui.theme.FreshNowSpacing.xxxs] 那么细的一条缝。
 * M3 里这叫分段列表（segmented list，Expressive）——它给的正是这套做法：同组的填充块用缝分开、
 * 首尾两块朝外的一侧取大圆角。
 *
 * 调用方各取各的两档：同一组里只有第一块该在朝外的一侧取大圆角，所以 [top] 与 [bottom] 由调用处
 * 按「这块在组里的位置」给出，这里不替它判断。
 */
internal fun splicedShape(top: CornerBasedShape, bottom: CornerBasedShape) = RoundedCornerShape(
    topStart = top.topStart,
    topEnd = top.topEnd,
    bottomStart = bottom.bottomStart,
    bottomEnd = bottom.bottomEnd
)
