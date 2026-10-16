package com.freshnow.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距设计源，取值来自 Material Design 3 间距体系：
 * 4dp 基础网格，组件内边距 4/8/12/16/24，组件间距 8/12/16/24，段间距 24/32/48，紧凑屏页边距 16
 * 界面只引用，不得另行定义字面量
 *
 * 这六档是 M3 技能给定的完整刻度，不是随手挑的值：某一档当前无人引用属正常，
 * 不要当作无用代码删掉——刻度断了，下次需要时只能退回写字面量。
 *
 * [xxxs] 不在技能那六档里，是项目为「拼接块之间的缝」加的一档，用途见它自己的 KDoc。
 */
object FreshNowSpacing {
    /**
     * 拼接块之间的缝。
     *
     * M3 的分段列表（segmented list，Expressive）就是用一条很细的缝把同组的填充块分开，
     * 实现里那个值叫 SegmentedGap，比技能给的「组件之间 8/12/16/24」还小一档，所以另开一档。
     * 它只用于同一组内部相邻两块之间，不当中通用间距——缝宽了，那两块就散了，「拼成一组」
     * 的意思也就没了。
     */
    val xxxs = 2.dp

    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}
