package com.freshnow.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸设计源。[FreshNowSpacing] 只管间距，组件自身的尺寸取这里，
 * 否则就会出现「拿间距令牌当尺寸用」——同一个 48dp 既表示段间距又表示缩略图边长，改动其一必然误伤另一处。
 *
 * 取值来自 Material Design 3：48dp 同时是列表缩略图与空态图标的常用边长，也是触控目标下限
 */
object FreshNowSize {
    /** 列表缩略图边长 */
    val thumbnail = 48.dp

    /** 空态/占位图标边长，与缩略图同档 */
    val icon = 48.dp

    /** 详情页无图时的占位块高度：刻意比真实照片矮，它只是占位、不假装是照片 */
    val imagePlaceholderHeight = 144.dp

    /** 需要内部滚动的文本面板高度上限 */
    val scrollableTextPanelHeight = 192.dp

    /**
     * 列表选中态的描边宽度。
     *
     * M3 没有「选中描边宽度」这档 token，描边组件的取值是 1dp（技能里 outlined card /
     * outlined button 的示例都是 1px）。这里取 2dp 是自定值：这条线本身就是「这一项将被删除」
     * 的唯一提示，1dp 摆在列表里更像一条分隔线，读不出「选中」的意思。
     */
    val selectionOutlineWidth = 2.dp
}
