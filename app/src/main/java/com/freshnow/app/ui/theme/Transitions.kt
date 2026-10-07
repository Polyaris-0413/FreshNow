package com.freshnow.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 动画设计源，界面只引用不另定义。
 *
 * 【有意偏离 M3 规范，请勿"按规范"改回去】
 * 规范对页面过渡给的是 Emphasized 曲线 + 不对称时长（进入 decelerate 400ms、
 * 退出 accelerate 200ms）。这两套都实测比较过，Emphasized 那套退场偏急、进场偏拖，
 * 观感不如现在这组，因此保留：tween 默认曲线（FastOutSlowInEasing，即 Material 的
 * standard 曲线，只是并非 Expressive 的最新推荐）+ 对称 350ms。
 * 取值与参考项目 book-story 的 ui/theme/Transitions.kt 一致。
 * 今后若要再调，请先实测对比，不要仅仅因为它"不合规范"就回退。
 *
 * 位移取容器宽度的 1/16 而非整屏：等价于 shared axis X 的轻微横移，
 * 整屏滑动在"列表 → 子页"的层级跳转里显得笨重。
 *
 * 淡化与容器颜色的约束见 FreshNowTopAppBar 的注释。
 */
object FreshNowTransitions {

    private const val DURATION_MS = 350
    private const val SLIDE_FRACTION = 16
    private const val STATE_CHANGE_MS = 150

    /**
     * 页内状态切换：顶栏在「列表」与「选择模式」之间换装、列表条目选中框的出现与消失。
     *
     * 取 M3 时长令牌 short3（150ms，技能 typography-and-shape.md 的 Duration Scale 表里
     * 归为「小转场」，正对这类变化），不与页面切换共用 350ms：那些是整页进出屏幕，
     * 这些只是同一页里的局部变化，沿用页面时长按下去要等一会儿才看得出选中了。
     * 曲线仍用 tween 默认的 standard 曲线，与页面切换一致。
     */
    val stateChange: FiniteAnimationSpec<Float> = tween(STATE_CHANGE_MS)

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
