package com.freshnow.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 页面切换动画设计源，界面只引用不另定义。
 *
 * 位移取容器宽度的 1/16 而非整屏：与 Material 的 shared axis X 位移量相当，
 * 整屏滑动在"列表 → 子页"这种层级跳转里显得笨重。
 *
 * 刻意不做淡入淡出。本项目顶栏用 surfaceContainer 与背景分层，两页的容器颜色因此不同，
 * 任何 alpha 交叉淡化都会在半透明期间露出底色差，表现为顶栏上一条随动画移动的暗带，
 * 且无法通过调参消除。只平移不淡化时两页同名容器颜色完全一致，接缝处不可见，
 * 只有真正的内容（文字、相机框）在移动。
 */
object FreshNowTransitions {

    private const val DURATION_MS = 350
    private const val SLIDE_FRACTION = 16

    /** 前进（进入子页）：新页面自右移入 */
    val forwardEnter: EnterTransition =
        slideInHorizontally(tween(DURATION_MS)) { width -> width / SLIDE_FRACTION }

    /** 前进：旧页面向左移出 */
    val forwardExit: ExitTransition =
        slideOutHorizontally(tween(DURATION_MS)) { width -> -width / SLIDE_FRACTION }

    /** 返回：新页面自左移入 */
    val backEnter: EnterTransition =
        slideInHorizontally(tween(DURATION_MS)) { width -> -width / SLIDE_FRACTION }

    /** 返回：旧页面向右移出 */
    val backExit: ExitTransition =
        slideOutHorizontally(tween(DURATION_MS)) { width -> width / SLIDE_FRACTION }
}
