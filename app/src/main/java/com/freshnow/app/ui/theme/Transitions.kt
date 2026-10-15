package com.freshnow.app.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * 动画设计源，界面只引用不另定义。
 *
 * 时长与缓动取自 M3 的 motion 令牌（`md.sys.motion.duration.*` 与 `md.sys.motion.easing.*`，
 * 表见 material-3 技能 references/typography-and-shape.md 的「Motion」一节，缓动值即该表的
 * cubic-bezier 定义）。M3 里转场仍走这套 easing + duration 系统，弹簧是组件与手势动画用的。
 *
 * 曲线按用途分四处：页面转场（层级）用 [FreshNowTransitions.Emphasized]（官方推荐给大多数转场），
 * 页内状态变化用 [FreshNowTransitions.Standard]，工具性转场的进入（预览淡入）用
 * [FreshNowTransitions.StandardDecelerate]，无层级关系的页面之间则用 fade through 那一对
 * （[FreshNowTransitions.StandardDecelerate] 进 / [FreshNowTransitions.StandardAccelerate] 出）。
 * 全部显式给，不用 `tween` 的默认值——那个默认是 M3 里的 legacy 曲线（0.4, 0, 0.2, 1），
 * 不是推荐集。
 *
 * 弹簧取自 material3 的 **standard** motion scheme（`tokens/StandardMotionTokens.kt` 的
 * spatial / effects 两族各三档）。standard 是 material3 的默认方案（`MaterialTheme` 里那个
 * ComposeLocal 的初值就是它，见 1.4.0 的 MaterialTheme.kt:233），expressive 方案要显式 opt-in
 * （MaterialExpressiveTheme），本项目没用。
 *
 * 【下面几条不在 M3 的任何令牌表里，是本项目自定】[DURATION_MEDIUM4] 与两条 emphasized 的
 * 进/出曲线目前没有使用者（保留作令牌档位，别当成「必须用满」）；[SHUTTER_SQUEEZE_SCALE]、
 * [RECOGNIZING_THRESHOLD_MS]、[STATUS_DWELL_MS] 与 [stateChangeDelayed] 的延迟属于规范未覆盖
 * 的取值，依据分别写在各自的 KDoc 里。fade through 那两条时长的依据是 M2 的 pattern、不是
 * M3 令牌，见该节说明。
 */
object FreshNowTransitions {

    // ---- M3 时长令牌 ----

    /** `duration.short3`：小转场（页内的颜色、透明度这类小变化） */
    const val DURATION_SHORT3 = 150

    /** `duration.short4`：退出转场（内容进出屏幕） */
    const val DURATION_SHORT4 = 200

    /** `duration.medium1`：工具性转场的进入 */
    const val DURATION_MEDIUM1 = 250

    /** `duration.medium2`：标准转场（页面转场取这一档） */
    const val DURATION_MEDIUM2 = 300

    /** `duration.medium4`：进入转场（当前没有使用者，保留作令牌档位） */
    const val DURATION_MEDIUM4 = 400

    // ---- M3 缓动令牌 ----

    /** `easing.emphasized`：官方推荐给大多数转场 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `easing.emphasized-decelerate`：进入屏幕（当前没有使用者，保留作令牌档位） */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** `easing.emphasized-accelerate`：退出屏幕（当前没有使用者，保留作令牌档位） */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** `easing.standard`：页内状态变化用它（官方这条与 [Emphasized] 同值） */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `easing.standard-decelerate`：工具性转场的进入，例如相机预览淡入 */
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** `easing.standard-accelerate`：退出屏幕的那一档，fade through 的退场用它 */
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // ---- 运动方案的弹簧（standard motion scheme）----

    /**
     * M3 把弹簧归成两族：**spatial**（位置、大小、形状）与 **effects**（透明度、颜色）；
     * 每族三档 fast / default / slow。**新增动画先在这六条里挑**，不要按用途另挑参数。
     *
     * 本项目用弹簧的地方都是**接手势**的：拖动松手后的回落、退场时照片飞回原处、双击缩放。
     * 这类运动不该用带固定时长的 tween：tween 默认曲线起手是缓入的，手指刚离开那几帧几乎不动，
     * 手上读作「顿一下」；弹簧没有固定时长，起手就跟着手指的速度走。0.9 的阻尼比允许一点点过冲，
     * 这也是它不显得拖泥带水的原因。
     *
     * 取值只能抄在这里：本项目 BOM（2026.02.01）解析到的是 material3 **1.4.0**，而那一版里
     * MotionScheme 仍是 internal（Kotlin 侧取不到 MaterialTheme.motionScheme），到 1.5.0-alpha29
     * 才对外公开（公开的是 default/fast/slow × spatial/effects 六个方法）。两版的 standard 取值
     * 逐个相同，所以哪天 BOM 提到公开它的版本，把这六个函数换成 MaterialTheme.motionScheme.* 即可，
     * 不会改观感，也别另挑参数。
     */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)


    // ---- 拍照页的快门 ----

    /**
     * 按下快门那一下取景框收多少：收下去再弹回，回弹就取上面那族弹簧里的 [fastSpatial]。
     *
     * 取 0.97：再小就看得出来是在「做动画」，而不是快门那一下收紧；这个量级在正方形取景框上
     * 边缘只挪几个 dp，配上 stiffness 1400 的弹簧，收得快、回得也快。
     *
     * 这一下是「我拍到了」的回执，不是「请等待」：帧一直流着，从按下到结果只有几十毫秒，
     * 给它配等待话术等于把一段看不见的等待变成看得见的（见 ShutterSqueeze 的说明）。
     *
     * 【规范缺席：M3 没有这一项，主流也没有公认值，本项目自定】M3 的按下反馈走的是
     * **形状与尺寸令牌**，不是缩放比例：
     * 按钮按下时容器从 full 圆角变 CornerSmall（BaselineButtonTokens.PressedContainerShape），
     * 拖动手柄按下有单独的 pressedSize（DragHandleSizes）。取景框不是组件，套不上那套令牌，
     * 这里用整体缩放表达同一件事。
     */
    const val SHUTTER_SQUEEZE_SCALE = 0.97f


    // ---- 页内状态切换 ----

    /**
     * 页内状态切换：时长取 M3 时长令牌 [DURATION_SHORT4]（200ms），缓动取 [Standard]。
     *
     * 曲线必须显式给：`tween` 默认的是 Compose 的 FastOutSlowInEasing，那是 M3 里的 legacy
     * 曲线（0.4, 0, 0.2, 1），起手慢；而这类变化要的是立刻跟上状态，所以取 [Standard]。
     *
     * 【这一档为什么是 200 而不是 150】技能表里 short3（150ms）标的是「小转场」、short4 标的是
     * 「退出转场」。真机上试过把这里改成 150ms，反而不行：选中框淡入、数字滚动都是小幅度变化，
     * 时长压得太短眼睛跟不上，观感更接近硬切；200ms 是短组里最大的一档，慢下来才看得出这是过渡
     * 而不是跳变。所以「看着像小变化但需要看清」的那些留在这一档，纯颜色过渡另走 [smallChange]。
     * 今后若要再调，同样请先实测对比，不要仅仅因为「技能表说 short3」就回退。
     */
    fun <T> stateChange(): FiniteAnimationSpec<T> =
        tween(DURATION_SHORT4, easing = Standard)


    /**
     * 页内的小变化：时长取 M3 时长令牌 [DURATION_SHORT3]（150ms），缓动同样用 [Standard]。
     *
     * 与 [stateChange] 的分工按 M3 给档位的标注来：short3 标的是「小转场」、short4 标的是
     * 「退出转场」。纯颜色过渡（手电筒的底色与前景色、扫描页底部两个按钮的可用/禁用色）不涉及
     * 内容进出，属于前者，取 150ms 更利落；条目的出现与消失、空态、状态胶囊、文案换装这些是
     * 内容在进出，归 [stateChange]。
     *
     * 【「颜色过渡算小转场」是本项目的归类，不是规范写的】M3 只说 short3 用于小转场，没列举
     * 哪些变化算小转场。改这条归类不违反规范，只要时长仍落在短组里。
     */
    fun <T> smallChange(): FiniteAnimationSpec<T> =
        tween(DURATION_SHORT3, easing = Standard)


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
     *
     * 【规范缺席：M3 没有延迟这一档，主流也只有原则没有数值，本项目自定】M2 的 Choreography
     * 里说可以给一组 incoming 元素加「短延迟」，但没给毫秒。所以这个数由「等它演完」推出来。
     */
    fun <T> stateChangeDelayed(): FiniteAnimationSpec<T> =
        tween(DURATION_SHORT4, delayMillis = DURATION_SHORT4, easing = Standard)

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


    // ---- fade through（无层级关系的页面之间）----

    /**
     * fade through 的两段时长与缩放幅度。
     *
     * Material 的 motion system 把这个 pattern 给「**没有空间或导航关系**的两个视图」。92% 的
     * 缩放有官方原文：m2.material.io/design/motion/the-motion-system.html 的 fade through 一节
     * 写的是 incoming "scaling in overall size from 92% to 100%"；两段时长（90 / 210，合 300ms）
     * 是同一个 pattern 的分段，M3 的令牌表只给「总档」、没有分段值。
     *
     * 90 与 210 不落在 M3 的时长刻度上（刻度里最近的是 short2 的 100 与 short4 的 200），但两段
     * 的**交叠比例**就是这个 pattern 的表达，四舍五入会把它改掉，所以保留 pattern 的原文。
     * 也就是说：这两条的依据是 M2 的 pattern，不是 M3 令牌；若哪天要求全部落回 M3 令牌刻度，
     * 只能写成 100 / 200，交叠比例随之从 0.3/0.7 变成 1/3 与 2/3。
     */
    const val FADE_THROUGH_OUT_MS = 90
    const val FADE_THROUGH_IN_MS = 210
    const val FADE_THROUGH_SCALE = 0.92f

    /** fade through 的进入：淡入 + 从 92% 放大 */
    val fadeThroughEnter: EnterTransition =
        fadeIn(tween(FADE_THROUGH_IN_MS, easing = StandardDecelerate)) +
            scaleIn(
                tween(FADE_THROUGH_IN_MS, easing = StandardDecelerate),
                initialScale = FADE_THROUGH_SCALE
            )

    /** fade through 的退出：淡出 + 缩到 92% */
    val fadeThroughExit: ExitTransition =
        fadeOut(tween(FADE_THROUGH_OUT_MS, easing = StandardAccelerate)) +
            scaleOut(
                tween(FADE_THROUGH_OUT_MS, easing = StandardAccelerate),
                targetScale = FADE_THROUGH_SCALE
            )


    // ---- 相机预览 ----

    /**
     * 相机预览层从透明到画面的淡入。
     *
     * 时长取 M3 时长令牌 [DURATION_MEDIUM1]（250ms，技能表里对应「进入屏幕（工具性转场）」那一档）。
     * 它与页面转场、页内状态切换都不是同一件事，所以单列一档，不并进 [stateChange]。
     */
    fun <T> previewFadeIn(): FiniteAnimationSpec<T> =
        tween(DURATION_MEDIUM1, easing = StandardDecelerate)


    // ---- 扫描页那枚状态胶囊 ----

    /**
     * 一趟请求飞多久还没回来，才允许说「正在识别…」。
     *
     * 取 1 秒：低于它的话，这句话刚浮出来就被下一帧的结果顶掉了，落在眼里的正好是一次闪烁，
     * 而它要挡的就是这种闪烁。1 秒也是「这一趟明显比平常慢」的分界——平常一趟就是几百毫秒
     * 到一两秒（见 AiVisionClient 的超时）。
     *
     * 这个 1 秒不是 M3 的令牌（M3 的加载指示器规范没给「多久之后才出现」）；对得上的是主流体验
     * 研究里那条：Nielsen 的响应时间阈值把 1 秒当作「还能留在心流里」的上界，低于 1 秒的等待
     * 不该出现加载指示器——出现了也来不及被看见。
     */
    const val RECOGNIZING_THRESHOLD_MS = 1_000L

    /**
     * 状态胶囊上同一句话在画面里至少留多久。
     *
     * 取 600ms：比页内状态切换（[DURATION_SHORT4] 的 200ms）长，因为它挡的不是一次过渡好不好看，
     * 而是两句话来回打架——请求每秒回来好几次，跟着信号换字的话一秒里能翻三次。
     * 停留期内报上来的短状态由 ScanStatusDwell 直接丢掉，不排队补演。
     *
     * 【规范缺席：M3 只给动画时长、不给文案停留，主流也没有公认值，本项目自定】能对的只有
     * 「防闪烁」这条惯例：一句话至少要留过一帧的若干倍，免得同一位置来回改字。
     */
    const val STATUS_DWELL_MS = 600L

    // 值到达时**不给**底色回执（曾经有过一套 FIELD_ARRIVAL_*，已删）：同一屏里几块各闪各的，
    // 颜色太乱，而且它与 FreshNowFieldRow 里那一下淡化重复表达同一件事。别再往这个方向加。
}

/**
 * 页面转场：前进与返回各一对，NavHost 与页内整屏切换（编辑页的表单 ↔ 裁剪）共用，走 M3 的
 * **shared axis X**（有前后关系的两个整屏视图沿同一根轴平移）。
 *
 * 【两页同速，是这套转场的要点】位移交给 [AnimatedContentTransitionScope] 自带的容器转场，
 * 它按当前内容与目标内容的实际尺寸算偏移，默认就是整屏；四条的时长与曲线取同一条 [pageSpec]。
 * shared axis 表达的是「同一个轴上的前后两个位置」，全靠两页同步移动；给两页不同的时长
 * （例如进场 400、退场 200）会让旧页先跑掉、新页还在后面磨，空间关系当场散掉。
 *
 * 【没有淡入淡出】也是同一个原因：位移不足整屏时两页会叠在一起，只能靠透明度区分，所以
 * 「缩位移」与「加淡」是一对——shared axis 要做的是把位移给满、不淡。
 *
 * 时长取 [FreshNowTransitions.DURATION_MEDIUM2]（300ms，令牌表里标注「标准转场」），曲线取
 * [FreshNowTransitions.Emphasized]（官方推荐给大多数转场的那条）。
 *
 * 方向：前进时新页自右侧移入、旧页向左移出；返回时两者相反。
 *
 * 只给**有层级关系**的跳转用（进子页、子页返回）；两个没有前后关系的顶层页之间用
 * [FreshNowTransitions.fadeThroughEnter] / [FreshNowTransitions.fadeThroughExit]——横向位移
 * 本身就在说「这两个页面是一前一后」，对平行去处是句假话。
 *
 * 【时长与位移是本项目的选择，不是规范要求】M3 没有给 shared axis 规定时长，也没规定位移量：
 * 300ms 是在令牌刻度里挑的一档（medium2 标「标准转场」，250/300/350/400 都自圆其说），整屏位移
 * 是 Compose 容器转场的默认值。改这两项都不违反规范，前提是两页仍同速、且位移与「要不要加淡」
 * 保持配套（位移不满整屏就会叠住，那时必须靠淡来区分）。
 */
fun AnimatedContentTransitionScope<*>.forwardEnter(): EnterTransition =
    slideIntoContainer(SlideDirection.Left, pageSpec())

fun AnimatedContentTransitionScope<*>.forwardExit(): ExitTransition =
    slideOutOfContainer(SlideDirection.Left, pageSpec())

fun AnimatedContentTransitionScope<*>.backEnter(): EnterTransition =
    slideIntoContainer(SlideDirection.Right, pageSpec())

fun AnimatedContentTransitionScope<*>.backExit(): ExitTransition =
    slideOutOfContainer(SlideDirection.Right, pageSpec())

/** 两页共用的一条：同一条曲线、同一档时长，进场与退场才咬得上 */
private fun <T> pageSpec(): FiniteAnimationSpec<T> =
    tween(
        FreshNowTransitions.DURATION_MEDIUM2,
        easing = FreshNowTransitions.Emphasized
    )

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
