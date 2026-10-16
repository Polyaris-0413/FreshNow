package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 拼接组：一组读起来是一个整体的填充块。
 *
 * M3 里这叫分段列表（segmented list，Expressive）：**同组的填充块之间只用一条缝分开，首尾两块朝外的
 * 一侧取大圆角**。规范给的理由是——列表有容器时该用缝隙分组，分隔线留给没有容器的复杂列表
 * （Lists 指南：*"Use gaps for contained lists... Limit dividers to uncontained lists"*）。
 * 缝的宽度见 [FreshNowSpacing.xxxs]。
 *
 * 组里的每一段都由这里发形状，调用方只说"这是第几段内容"：[SplicedGroupScope.row]。这样"一共几段"
 * 只有一个来处——手写形状的话，往组里加一段而忘了改总数，最后一段会悄悄变成中间那档圆角。
 *
 * 用它装什么由调用方决定：结果字段装的是「标签 + 值」的行，设置页与关于页装的是 ListItem。
 */
@Composable
internal fun SplicedGroup(
    modifier: Modifier = Modifier,
    content: SplicedGroupScope.() -> Unit
) {
    val outerCorner = MaterialTheme.shapes.medium
    val innerCorner = MaterialTheme.shapes.extraSmall

    // 只在本次组合里收集，不跨帧留着：组里有几段完全由调用方这次写了什么决定
    val scope = SplicedGroupScope()
    scope.content()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)
    ) {
        scope.segments.forEachIndexed { index, segment ->
            SplicedSegment(
                shape = splicedShape(
                    top = if (index == 0) outerCorner else innerCorner,
                    bottom = if (index == scope.segments.lastIndex) outerCorner else innerCorner
                )
            ) {
                segment()
            }
        }
    }
}

/** [SplicedGroup] 的作用域：往组里加一段内容 */
internal class SplicedGroupScope {
    internal val segments = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        segments += content
    }
}

/**
 * 拼接组里的一段：一块填充容器，形状由它在组里的位置给出（见 [SplicedGroup]）。
 *
 * 填充色取 M3 的填充容器（与结果字段、动作面板里的卡片同一个角色）。左右内边距默认不给：装 ListItem
 * 时它自己带 16dp，再套一层就成两倍缩进；装「标签 + 值」那种行时才需要调用方补上（见
 * [FreshNowResultFields]）。
 */
@Composable
internal fun SplicedSegment(
    shape: Shape,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(modifier = Modifier.padding(horizontal = horizontalPadding)) {
            content()
        }
    }
}

/**
 * 把上下两档圆角拼成一个四角圆角矩形，用来做拼接组里的单块形状。
 *
 * [SplicedGroup] 已经按位置算好了该用哪两档，这里单列出来是给「组不是一列」的场合用——比如
 * 动作面板里两个来源上下拼成的一组（见 ChangePhotoSheet）。
 */
internal fun splicedShape(top: CornerBasedShape, bottom: CornerBasedShape) = RoundedCornerShape(
    topStart = top.topStart,
    topEnd = top.topEnd,
    bottomStart = bottom.bottomStart,
    bottomEnd = bottom.bottomEnd
)
