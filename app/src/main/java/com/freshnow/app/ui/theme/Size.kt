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

    /**
     * 小格子里那一枚图标。
     *
     * M3 的图标就是 24dp（图标按钮也是在 48dp 的触控区里摆一枚 24dp 图标），而 [icon] 那一档
     * 48dp 是给整屏空态用的。[thumbnail] 那样 48dp 的方块里摆 48dp 的图标会顶满整格，看着挤，
     * 所以小格子取这一档。主页列表那枚缩略图无图时画的也是 24dp（它靠 Icon 的默认值拿到）。
     */
    val smallIcon = 24.dp

    /**
     * 滚动区底边渐隐带的高度。
     *
     * M3 没有这一档取值（渐隐不是 M3 组件，是 Google 自己一直用的手法），这是自定值：
     * 够把被裁的下一行内容边缘化开，又不至于盖掉一整行正文。
     * 不放进 [FreshNowSpacing]：那是间距刻度，拿它当尺寸用的话，同一个 24dp 既表示段间距又表示
     * 渐隐带高度，改动其一必然误伤另一处。
     */
    val scrollEdgeFade = 24.dp

    /**
     * 输入框描边的宽度。
     *
     * M3 的 outlined text field 用的就是 1dp（TextField 那组 token 里的 outline width），
     * 项目没有另设值。写进设计源是因为别处要跟输入框同款描边（编辑页「更换照片」那一块），
     * 而 M3 没有公开这一档宽度，只能在这里落一个数——要改就两处一起改。
     */
    val fieldOutlineWidth = 1.dp

    /**
     * 列表选中态的描边宽度。
     *
     * M3 没有「选中描边宽度」这档 token，描边组件的取值是 1dp（技能里 outlined card /
     * outlined button 的示例都是 1px）。这里取 2dp 是自定值：这条线本身就是「这一项将被删除」
     * 的唯一提示，1dp 摆在列表里更像一条分隔线，读不出「选中」的意思。
     */
    val selectionOutlineWidth = 2.dp

    /**
     * 欢迎页每一步的主视觉边长。
     *
     * M3 没有这一档：它是整页唯一的主视觉，取空态图标（48dp）的两倍，避免与列表里的缩略图同档
     */
    val welcomeHero = 96.dp

    /**
     * 点按对焦的角标尺寸。
     *
     * 【规范缺席：M3 没有相机取景的组件，也没有这一档】取 64dp：比列表缩略图（48dp）大一圈，
     * 在满宽的取景框里看得清落点；图形是 [R.drawable.ic_focus]（Material Symbols 的
     * filter_center_focus），它的角标与中心点都按 24dp 网格画，放大后线粗随尺寸一起长，
     * 这一档在真机上约 2.7dp。
     */
    val focusBracket = 64.dp

    /**
     * 角标底下那层深色衬边相对本体的放大倍数。
     *
     * 图标是实心填充，描不了边，只能把整枚图形放大一份垫在底下，露出那一圈深色就是可见性。
     * 取 1.06：64dp 上每侧多出约 2dp，恰好是一道看得见的边；再大图形自身也会变形得能被看出来。
     */
    val focusMarkUnderlayScale = 1.06f

    /**
     * M3 线形进度指示器的轨道高度（规范值 4dp）。欢迎页的进度条在第 1 步是隐着的，
     * 用同高的空位撑住，否则它一出现底部按钮就往下跳一格
     */
    val progressTrack = 4.dp
}
