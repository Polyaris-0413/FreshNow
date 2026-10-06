package com.freshnow.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 页面切换动画设计源，界面只引用不另定义。
 *
 * 缓动与时长取自 M3 动效规范：transition 仍走"缓动 + 时长"体系（弹簧体系只用于组件）。
 * 进入用 Emphasized decelerate 400ms，退出用 Emphasized accelerate 200ms —— 两者时长
 * 刻意不对称，退场要快、进场要稳。曲线即规范给出的 cubic-bezier 值：
 * enter (0.05, 0.7, 0.1, 1)，exit (0.3, 0, 0.8, 0.15)。
 *
 * 位移取容器宽度的 1/16：规范未规定 shared axis X 的位移量（Material 文档用固定 30dp），
 * 这里按屏宽取比例，大屏上观感更一致；当前手机上约 22.5dp，与 30dp 接近。
 *
 * 淡化与容器颜色的约束见 FreshNowTopAppBar 的注释。
 */
object FreshNowTransitions {

    private val enterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val exitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private const val ENTER_DURATION_MS = 400
    private const val EXIT_DURATION_MS = 200
    private const val SLIDE_FRACTION = 16

    /** 前进（进入子页）：新页面自右移入 */
    val forwardEnter: EnterTransition =
        fadeIn(tween(ENTER_DURATION_MS, easing = enterEasing)) +
            slideInHorizontally(tween(ENTER_DURATION_MS, easing = enterEasing)) { it / SLIDE_FRACTION }

    /** 前进：旧页面向左移出 */
    val forwardExit: ExitTransition =
        fadeOut(tween(EXIT_DURATION_MS, easing = exitEasing)) +
            slideOutHorizontally(tween(EXIT_DURATION_MS, easing = exitEasing)) { -it / SLIDE_FRACTION }

    /** 返回：新页面自左移入 */
    val backEnter: EnterTransition =
        fadeIn(tween(ENTER_DURATION_MS, easing = enterEasing)) +
            slideInHorizontally(tween(ENTER_DURATION_MS, easing = enterEasing)) { -it / SLIDE_FRACTION }

    /** 返回：旧页面向右移出 */
    val backExit: ExitTransition =
        fadeOut(tween(EXIT_DURATION_MS, easing = exitEasing)) +
            slideOutHorizontally(tween(EXIT_DURATION_MS, easing = exitEasing)) { it / SLIDE_FRACTION }
}
