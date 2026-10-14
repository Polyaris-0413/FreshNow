package com.freshnow.app.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
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

    // ---- 运动方案的弹簧（M3 Expressive）----

    /**
     * M3 把弹簧归成两族：**spatial**（位置、大小、形状）与 **effects**（透明度、颜色）；
     * 每族三档 fast / default / slow。**新增动画先在这六条里挑**，不要按用途另挑参数。
     *
     * 本项目用弹簧的地方都是**接手手势**的：拖动松手后的回落、退场时照片飞回原处、双击缩放。
     * 这类运动不该用带固定时长的 tween：tween 默认曲线起手是缓入的，手指刚离开那几帧几乎不动，
     * 手上读作「顿一下」；弹簧没有固定时长，起手就跟着手指的速度走。0.9 的阻尼比允许一点点过冲，
     * 这也是它不显得拖泥带水的原因。
     *
     * 取值只能抄在这里：本项目 BOM（2026.02.01）解析到的是 material3 **1.4.0**，而那一版里
     * MotionScheme 仍是 internal（Kotlin 侧取不到 MaterialTheme.motionScheme），到 1.5.0-alpha29
     * 才对外公开。两版的取值实测逐个相同（0.9 & 1400/700/300、1 & 3800/1600），所以哪天 BOM 提到
     * 公开它的版本，把这几个函数换成 MaterialTheme.motionScheme.* 即可，不会改观感，也别另挑参数。
     */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)


    // ---- 拍照页的快门 ----

    /**
     * 按下快门那一下取景框收多少：收下去再弹回，回弹就取上面那族弹簧里的 [fastSpatial]。
     *
     * 取 0.97：再小就看得出来是在「做动画」，而不是快门那一下收紧；这个量级在正方形取景框上
     * 边缘只挪几个 dp，配上 stiffness 1400 的弹簧，收得快、回得也快。
     *
     * 这一下是「我拍到了」的回执，不是「请等待」：帧一直流着，从按下到结果只有几十毫秒，
     * 给它配等待话术等于把一段看不见的等待变成看得见的（见 ShutterSqueeze 的说明）。
     */
    const val SHUTTER_SQUEEZE_SCALE = 0.97f


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
     * 选中计数里数字的滚动、列表里条目的出现与增删（刚存进来的淡入、被删的淡出、
     * 其余的滑到新位置）。
     *
     * 时长与取舍见 STATE_CHANGE_MS。与页面切换的比例关系：一次性的整页进出屏幕用
     * 上面的 350ms，这些只是同一页里的局部变化，取 200ms。曲线仍用 tween 默认的
     * standard 曲线，与页面切换一致。
     *
     * 返回类型不写死：淡入淡出要 FiniteAnimationSpec<Float>，位移要 FiniteAnimationSpec<IntOffset>，
     * 由调用处决定，省得同一组时长与曲线在多处各写一遍、日后改一处漏一处。
     */
    fun <T> stateChange(): FiniteAnimationSpec<T> = tween(STATE_CHANGE_MS)


    /**
     * 与 [stateChange] 同一档时长，但晚一拍才开始。
     *
     * 用于「前一件事演完才轮到它」的地方，目前只有一处：列表被删空后空态的出现。它紧跟在条目
     * 淡出之后，而确认删除的对话框也正好在这段时间退场——两者同时开始的话，它的前半段整段被
     * 对话框盖着，用户看到的是「对话框一关，它已经在那儿了」，等于没有淡入。等一拍再淡入，
     * 才看得出它是淡进来的。
     *
     * 延迟取同一档时长而不是另取一个数：要等的那件事（条目淡出）就是用 [stateChange] 演的，
     * 它结束的时刻就是这个数。
     */
    fun <T> stateChangeDelayed(): FiniteAnimationSpec<T> =
        tween(STATE_CHANGE_MS, delayMillis = STATE_CHANGE_MS)

    /** 页内淡入淡出换装：新旧内容各淡各的，不位移 */
    fun fadeSwap(): ContentTransform =
        fadeIn(stateChange()) togetherWith fadeOut(stateChange())

    /**
     * 换装时连宽高一起动。
     *
     * 给「内容换掉之后自己的尺寸也会变」的地方用（例如按钮的文案从「下一步」换成「允许使用相机」，
     * 差三个字）：[fadeSwap] 走的是默认的 SizeTransform（spring），跟项目其余动画不是一套时长，
     * 这里给同一档 tween。
     */
    fun fadeSwapAnimatedSize(): ContentTransform =
        // 不能写 `fadeIn(...) togetherWith fadeOut(...)` 再补尺寸：togetherWith 只有不带
        // SizeTransform 的那个重载，尺寸得在构造 ContentTransform 时给
        ContentTransform(
            targetContentEnter = fadeIn(stateChange()),
            initialContentExit = fadeOut(stateChange()),
            sizeTransform = SizeTransform { _, _ -> stateChange() }
        )

    // ---- 相机预览 ----

    /**
     * 相机预览层从透明到画面的淡入。
     *
     * 时长取 M3 时长表的 standard decelerate（250ms，「进入屏幕」那一列），曲线仍用 tween 默认的
     * FastOutSlowInEasing（M3 的 standard 曲线，与页面切换同一条）。它与页面转场（350ms）、页内状态
     * 切换（200ms）都不是同一件事，所以单列一档，不并进 [stateChange]。
     */
    private const val PREVIEW_FADE_IN_MS = 250

    fun <T> previewFadeIn(): FiniteAnimationSpec<T> = tween(PREVIEW_FADE_IN_MS)

    // ---- 扫描页那枚状态胶囊 ----

    /**
     * 一趟请求飞多久还没回来，才允许说「正在识别…」。
     *
     * 取 1 秒：低于它的话，这句话刚浮出来就被下一帧的结果顶掉了，落在眼里的正好是一次闪烁，
     * 而它要挡的就是这种闪烁。1 秒也是「这一趟明显比平常慢」的分界——平常一趟就是几百毫秒
     * 到一两秒（见 AiVisionClient 的超时）。
     */
    const val RECOGNIZING_THRESHOLD_MS = 1_000L

    /**
     * 状态胶囊上同一句话在画面里至少留多久。
     *
     * 取 600ms：比页内状态切换（[STATE_CHANGE_MS] 的 200ms）长，因为它挡的不是一次过渡好不好看，
     * 而是两句话来回打架——请求每秒回来好几次，跟着信号换字的话一秒里能跳三次。
     * 停留期内报上来的短状态由 ScanStatusDwell 直接丢掉，不排队补演。
     */
    const val STATUS_DWELL_MS = 600L

    // 值到达时**不给**底色回执（曾经有过一套 FIELD_ARRIVAL_*，已删）：同一屏里几块各闪各的，
    // 颜色太乱，而且它与 FreshNowFieldRow 里那一下淡化重复表达同一件事。别再往这个方向加。
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
