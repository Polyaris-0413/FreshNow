package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * 拼接卡片：一组行贴着摆成一张卡，只有整组朝外的那一侧取大圆角，块与块之间取小圆角。
 *
 * 外圈取规范给卡片类容器的 medium，内圈取最小档 extraSmall，让拼接的「外圆内方」看得出是一组
 * 而不是几张普通卡片；圆角只引用形状令牌，不另定义数值。
 *
 * 规则只此一份：扫描页的结果字段与设置页的分区都用它，两处的拼接外观因此不会各走各的。
 */
@Composable
fun splicedCardShape(index: Int, count: Int): Shape {
    val outer = MaterialTheme.shapes.medium
    val inner = MaterialTheme.shapes.extraSmall
    return when {
        // 只有一块时无所谓拼接，四角都取外圆
        count <= 1 -> outer
        index == 0 -> splicedShape(top = outer, bottom = inner)
        index == count - 1 -> splicedShape(top = inner, bottom = outer)
        else -> inner
    }
}

/**
 * 拼接中的一块：填充色与 M3 填充容器一致。
 *
 * 横向内边距由调用方给——设置页那些行是列表项、自带内边距，字段行没有，两者不能共用一个值。
 */
@Composable
fun SplicedCard(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        content()
    }
}

/** 上下各取一档形状令牌，拼成一个四角圆角矩形 */
private fun splicedShape(top: CornerBasedShape, bottom: CornerBasedShape) = RoundedCornerShape(
    topStart = top.topStart,
    topEnd = top.topEnd,
    bottomStart = bottom.bottomStart,
    bottomEnd = bottom.bottomEnd
)
