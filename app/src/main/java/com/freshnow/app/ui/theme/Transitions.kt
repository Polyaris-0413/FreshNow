package com.freshnow.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 页面切换动画设计源，界面只引用不另定义。
 *
 * 位移取容器宽度的 1/16 而非整屏：等价于 Material 的 shared axis X（淡入淡出 + 轻微横移），
 * 整屏滑动在这种"列表 → 子页"的层级跳转里显得笨重。
 * 参考 book-story 的 ui/theme/Transitions.kt，此处只保留本项目用得到的四条。
 */
object FreshNowTransitions {

    private const val DURATION_MS = 350
    private const val SLIDE_FRACTION = 16

    /** 前进（进入子页）：新页面自右轻微移入 */
    val forwardEnter: EnterTransition = fadeIn(tween(DURATION_MS)) +
        slideInHorizontally(tween(DURATION_MS)) { width -> width / SLIDE_FRACTION }

    /** 前进：旧页面向左轻微移出 */
    val forwardExit: ExitTransition = fadeOut(tween(DURATION_MS)) +
        slideOutHorizontally(tween(DURATION_MS)) { width -> -width / SLIDE_FRACTION }

    /** 返回：新页面自左轻微移入 */
    val backEnter: EnterTransition = fadeIn(tween(DURATION_MS)) +
        slideInHorizontally(tween(DURATION_MS)) { width -> -width / SLIDE_FRACTION }

    /** 返回：旧页面向右轻微移出 */
    val backExit: ExitTransition = fadeOut(tween(DURATION_MS)) +
        slideOutHorizontally(tween(DURATION_MS)) { width -> width / SLIDE_FRACTION }
}
