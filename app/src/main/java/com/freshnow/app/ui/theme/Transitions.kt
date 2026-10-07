package com.freshnow.app.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

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

    // ---- 页面切换 ----

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

    // ---- 页内状态切换 ----

    /**
     * 页内状态切换的时长，取 M3 时长令牌 short4（200ms）。
     *
     * 【按实测观感选的值，请勿只照着技能表改回 short3】
     * 技能 typography-and-shape.md 的 Duration Scale 表里，short3（150ms）标的是「小转场」、
     * short4 标的是「退出转场」，照表面意思这类页内变化该取 150ms。真机上试过 150ms，
     * 反而不行：选中框淡入、数字滚动都是小幅度变化，时长压得太短眼睛跟不上，
     * 观感更接近硬切；200ms 是短组里最大的一档，慢下来才看得出这是过渡而不是跳变。
     * 今后若要再调，同样请先实测对比，不要仅仅因为「技能表说 short3」就回退。
     */
    private const val STATE_CHANGE_MS = 200

    /**
     * 页内状态切换：顶栏在「列表」与「选择模式」之间换装、列表条目选中框的出现与消失、
     * 选中计数里数字的滚动。
     *
     * 时长与取舍见 STATE_CHANGE_MS。与页面切换的比例关系：一次性的整页进出屏幕用
     * 上面的 350ms，这些只是同一页里的局部变化，取 200ms。曲线仍用 tween 默认的
     * standard 曲线，与页面切换一致。
     *
     * 返回类型不写死：淡入淡出要 FiniteAnimationSpec<Float>，位移要 FiniteAnimationSpec<IntOffset>，
     * 由调用处决定，省得同一组时长与曲线在多处各写一遍、日后改一处漏一处。
     */
    fun <T> stateChange(): FiniteAnimationSpec<T> = tween(STATE_CHANGE_MS)

    /** 页内淡入淡出换装：新旧内容各淡各的，不位移 */
    fun fadeSwap(): ContentTransform =
        fadeIn(stateChange()) togetherWith fadeOut(stateChange())
}

/**
 * 选中计数里数字的滚动：变多往上滚、变少往下滚。方向由数量本身决定，与界面无关，
 * 因此判据在这里而不是调用方。
 *
 * 位移取整个行高，而不是留一部分：调用方只把这一个数字放进 AnimatedContent，它的框
 * 正好一行高，整行偏移配合 AnimatedContent 的裁剪就是一个滚动窗口——旧数字从一侧滑出、
 * 新数字从另一侧滑入。偏移不足两个数字反而会同时留在框里，看着是重影。
 *
 * 不做成 [FreshNowTransitions] 的成员：它要在 transitionSpec 里直接调用，而那个 lambda 的
 * 接收者是 AnimatedContentTransitionScope，成员形式的扩展函数在那里调不到。
 */
fun AnimatedContentTransitionScope<Int>.countRoll(): ContentTransform {
    val roll = if (targetState > initialState) 1 else -1
    return (
        slideInVertically(FreshNowTransitions.stateChange()) { height -> roll * height } +
            fadeIn(FreshNowTransitions.stateChange())
        ).togetherWith(
        slideOutVertically(FreshNowTransitions.stateChange()) { height -> -roll * height } +
            fadeOut(FreshNowTransitions.stateChange())
    )
}
