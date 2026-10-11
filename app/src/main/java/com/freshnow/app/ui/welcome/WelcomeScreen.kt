package com.freshnow.app.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.AiBasicConfigDraft
import com.freshnow.app.ui.component.AiBasicConfigForm
import com.freshnow.app.ui.openAppSettings
import com.freshnow.app.ui.scan.rememberCameraPermissionState
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTransitions
import kotlinx.coroutines.launch

/**
 * 引导的第几步。枚举顺序就是显示顺序，页数也由它决定（进度条与「还有几步」都以它为唯一来源）。
 *
 * [CAMERA] 是相机权限的 permission priming：先说明用途、再由用户点按钮触发系统弹窗。
 * 取值来自 Android 官方的 Auth & Onboarding 指南（「Explain why your app needs specific
 * permissions when they are needed」）——本应用唯一会用到相机的地方是扫描，把它放在配置之后、
 * 进入主页之前，用户此时刚看完「扫描要拍标签」，说明与动作之间没有别的东西插队。
 */
internal enum class WelcomeStep {
    INTRO,
    FLOW,
    AI_CONFIG,
    CAMERA,
    DONE;

    /**
     * 这一步有没有「可以不做的事」，也就是摆不摆「跳过」。
     *
     * 信息页没有：那里「跳过」与「下一步」做的是同一件事，两颗同义的按钮摆在一起只会让人犹豫。
     * 配置与权限那两步才有——不填配置、不授权，都是确实可以省掉的动作。
     */
    val hasSkippableAction: Boolean
        get() = this == AI_CONFIG || this == CAMERA
}

/**
 * 首次启动的欢迎页，只在「这次安装还没走过引导」时由 [com.freshnow.app.ui.FreshNowApp] 渲染
 * （见 OnboardingRepository）。
 *
 * 引导的整体形状取 Material 的「Top User Benefits」模型：先给价值（第 1 步），再说清用它的代价
 * （第 2 步：识别要自备模型服务），然后是动作（第 3 步配置、第 4 步相机权限），最后收个尾
 * （第 5 步）。收尾那一步是有意留的：设置刚做完就直接落进主页，用户不知道自己走完了没有。
 *
 * 「跳过」跳过的是当前这一步要干的事，不是整个引导：一律往前走一项，而且只摆在「确实有可以不做
 * 的事」那两步（配置与权限）。信息页上它与「下一步」完全同义，两颗同义的按钮只会让人犹豫；
 * 收尾那一步也不摆（它和「开始使用」是同一件事）。结束引导的出口就是那颗「开始使用」，
 * 以及停在第一步时的返回键。「上一步」与系统返回键都能往回走。
 *
 * 状态与界面分开：[WelcomeSteps] 是不带状态的版式，测试直接摆它，不会碰到设备上真实的 DataStore。
 */
@Composable
fun WelcomeScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = viewModel()
) {
    val basicConfig by viewModel.basicConfig.collectAsStateWithLifecycle()
    val configSavedCount by viewModel.configSavedCount.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val cameraPermission = rememberCameraPermissionState()
    val context = LocalContext.current
    // 授权成功才往下走：结果回来之前留在权限那一页。没这一步的话，用户在系统弹窗里点拒绝时，
    // 人已经站在收尾页上了
    var cameraGrantedCount by remember { mutableIntStateOf(0) }

    // 用户去系统设置里开完权限再回来时，不会有 requestWithResult 的回调，所以 granted 变真也算一次
    LaunchedEffect(cameraPermission.granted) {
        if (cameraPermission.granted) cameraGrantedCount += 1
    }

    LaunchedEffect(finished) {
        if (finished) onFinish()
    }

    WelcomeSteps(
        basicConfig = basicConfig,
        configSavedCount = configSavedCount,
        cameraGrantedCount = cameraGrantedCount,
        cameraPermissionBlocked = cameraPermission.blocked,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onModelNameChange = viewModel::onModelNameChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSaveConfig = viewModel::saveBasicConfig,
        onRequestCamera = {
            cameraPermission.requestWithResult { granted ->
                if (granted) cameraGrantedCount += 1
            }
        },
        // 权限被系统锁死时再点也不会有弹窗（见 CameraPermission），只能去设置页
        onOpenCameraSettings = { openAppSettings(context) },
        onSkip = viewModel::finish,
        modifier = modifier
    )
}

/**
 * 欢迎页的版式本身：几步内容 + 进度 + 跳过、上一步与主按钮。
 *
 * [configSavedCount] 与 [cameraGrantedCount] 都是「已经发生的事实」，而不是「点过了」：
 * 前者等配置真的落盘，后者等相机权限真的授予——先走再等的话，推进过去的那一刻配置还没写进
 * DataStore，或者系统弹窗还盖在屏幕上。
 */
@Composable
internal fun WelcomeSteps(
    basicConfig: AiBasicConfigDraft,
    configSavedCount: Int,
    cameraGrantedCount: Int,
    cameraPermissionBlocked: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveConfig: () -> Unit,
    onRequestCamera: () -> Unit,
    onOpenCameraSettings: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = WelcomeStep.entries
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val currentStep = steps[pagerState.currentPage]
    val scope = rememberCoroutineScope()

    fun goTo(step: WelcomeStep) {
        scope.launch { pagerState.animateScrollToPage(step.ordinal) }
    }

    /**
     * 往前走一项。「跳过」与主按钮的「下一步」都走这里——两者都是「这一步到此为止，去下一步」，
     * 区别只在那一步有没有留下东西。
     *
     * 用 getOrNull 而不是按当前页下标加一直接取：连点时当前页可能已经走到收尾页，越界就崩了。
     */
    fun goForward() {
        val next = steps.getOrNull(pagerState.currentPage + 1)
        if (next == null) onSkip() else goTo(next)
    }

    /**
     * 往回走一项；已经在第 1 步就什么也不做。
     *
     * 这一步不是多余的谨慎：翻页过半时当前页已经是 0，而「上一步」这时正在淡出、仍接得到点击，
     * 照当前页下标减一直接取就是 steps[-1]——连点两次上一步曾因此闪退
     * （IndexOutOfBoundsException: index: -1）。
     */
    fun goBack() {
        steps.getOrNull(pagerState.currentPage - 1)?.let(::goTo)
    }

    LaunchedEffect(configSavedCount) {
        if (configSavedCount > 0) goTo(WelcomeStep.CAMERA)
    }

    // 权限授予之后才推进，而且用户得已经走到相机那一步：进引导时权限本来就在（例如重装后系统仍
    // 记着），这个信号不该把人从第 1 步直接丢到收尾页
    LaunchedEffect(cameraGrantedCount) {
        if (cameraGrantedCount > 0 && pagerState.currentPage >= WelcomeStep.CAMERA.ordinal) {
            goTo(WelcomeStep.DONE)
        }
    }

    // 系统返回键与「上一步」同一件事；已经在第 1 步时退回结束引导
    BackHandler {
        if (pagerState.currentPage == 0) onSkip() else goBack()
    }

    // 主按钮这一屏该说什么。抽出来是为了让按钮自己盯着它做过渡（见下面 Button 里的 AnimatedContent）：
    // 各步的文案不一样长，换的时候得连文字带宽度一起动
    val primaryLabel = when (currentStep) {
        WelcomeStep.INTRO, WelcomeStep.FLOW,
        WelcomeStep.AI_CONFIG -> R.string.welcome_action_next

        WelcomeStep.CAMERA ->
            if (cameraPermissionBlocked) R.string.action_open_settings else R.string.welcome_action_grant_camera

        WelcomeStep.DONE -> R.string.welcome_action_start
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 配置那一步要填三项，键盘弹出时底部按钮得跟着抬起来
                .imePadding()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                WelcomeStepContent(
                    step = steps[page],
                    basicConfig = basicConfig,
                    onBaseUrlChange = onBaseUrlChange,
                    onModelNameChange = onModelNameChange,
                    onApiKeyChange = onApiKeyChange,
                    cameraPermissionBlocked = cameraPermissionBlocked,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FreshNowSpacing.sm)
                    .padding(bottom = FreshNowSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
            ) {
                // 第 1 步没有进度可言（还什么都没走），所以整条进度从第 2 步才淡入。
                // 外面这个固定高度的盒子撑住位置：否则进度条一出现，底部按钮就跟着往下跳一格
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FreshNowSize.progressTrack)
                ) {
                    WelcomeProgress(
                        visible = pagerState.currentPage > 0,
                        // 进度跟随滚动而不是等翻页落定：按页跳变的话，一页一格地闪，看不出「在往前走」
                        progress = pagerProgress(pagerState, steps.size)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 「跳过」摆在行首、「上一步」紧挨主按钮——原来的摆法。代价是主按钮的文案变长时
                    // （「下一步」→「允许使用相机」）会把「上一步」往左顶：上面那颗按钮的过渡动画只能
                    // 让这件事演得平滑，位置本身还是会变。要治根得让两颗都靠左（上一步在行首），
                    // 那就换了一种观感，取舍留在这里
                    AnimatedVisibility(
                        visible = currentStep.hasSkippableAction,
                        enter = fadeIn(FreshNowTransitions.stateChange()),
                        exit = fadeOut(FreshNowTransitions.stateChange())
                    ) {
                        TextButton(onClick = { goForward() }) {
                            Text(text = stringResource(R.string.welcome_action_skip))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    // 「上一步」从第 2 步起才在
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = fadeIn(FreshNowTransitions.stateChange()),
                        exit = fadeOut(FreshNowTransitions.stateChange())
                    ) {
                        TextButton(onClick = { goBack() }) {
                            Text(text = stringResource(R.string.welcome_action_previous))
                        }
                    }

                    Button(
                        onClick = {
                            when (currentStep) {
                                WelcomeStep.INTRO, WelcomeStep.FLOW -> goForward()

                                WelcomeStep.AI_CONFIG -> onSaveConfig()

                                // 这里不直接翻页：翻页由 cameraGrantedCount 在权限真的授予之后带过来。
                                // 权限被系统锁死时那个弹窗不会再出现，只能去设置页
                                WelcomeStep.CAMERA -> if (cameraPermissionBlocked) {
                                    onOpenCameraSettings()
                                } else {
                                    onRequestCamera()
                                }

                                WelcomeStep.DONE -> onSkip()
                            }
                        },
                    ) {
                        AnimatedContent(
                            targetState = primaryLabel,
                            // 文字交叉淡化，宽度跟着一起动：「下一步」与「允许使用相机」差三个字，
                            // 不演的话按钮会「啪」地跳宽，中间那段空隙也跟着瞬移
                            transitionSpec = { FreshNowTransitions.fadeSwapAnimatedSize() },
                            label = "primaryAction"
                        ) { label ->
                            // 交叉淡化期间新旧两段文字同时在，不锁成一行的话会被挤成两行
                            Text(text = stringResource(label), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 已走完的进度条。淡入淡出单独放在函数里：直接写在底部的 Column 里会被解析成
 * ColumnScope 版的 AnimatedVisibility，而它在 Box 里调不到（那是另一个接收者）。
 */
@Composable
private fun WelcomeProgress(visible: Boolean, progress: Float) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(FreshNowTransitions.stateChange()),
        exit = fadeOut(FreshNowTransitions.stateChange())
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 已经走了多远：按「剩下几段」算，最后一页正好填满。除以页数的话，走到最后一步进度条还差一截，
 * 看着像没走完。
 */
private fun pagerProgress(pagerState: PagerState, pageCount: Int): Float {
    if (pageCount <= 1) return 1f
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    return (position / (pageCount - 1)).coerceIn(0f, 1f)
}

/**
 * 单步的内容。整个可区居中摆：内容不足一屏时居中，超出一屏（第 3 步在小屏加键盘）时
 * 从顶部开始滚。用 Box 居中而不是让可滚动的 Column 自己居中——Column 在 verticalScroll 下
 * 拿到的最大高度是无界的，`Arrangement.Center` 在它身上不会生效。
 */
@Composable
private fun WelcomeStepContent(
    step: WelcomeStep,
    basicConfig: AiBasicConfigDraft,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    cameraPermissionBlocked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FreshNowSpacing.sm)
                .padding(vertical = FreshNowSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                WelcomeStep.INTRO -> {
                    WelcomeHero(R.drawable.ic_cookie)
                    StepSpacer()
                    WelcomeTitle(stringResource(R.string.app_name))
                    StepSpacer()
                    WelcomeBody(stringResource(R.string.welcome_intro_body))
                    Spacer(modifier = Modifier.height(FreshNowSpacing.md))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xs)
                    ) {
                        Benefit(stringResource(R.string.welcome_intro_benefit_scan))
                        Benefit(stringResource(R.string.welcome_intro_benefit_expiry))
                        Benefit(stringResource(R.string.welcome_intro_benefit_privacy))
                    }
                }

                WelcomeStep.FLOW -> {
                    WelcomeHero(R.drawable.ic_reasoning)
                    StepSpacer()
                    WelcomeTitle(stringResource(R.string.welcome_flow_title))
                    StepSpacer()
                    WelcomeBody(stringResource(R.string.welcome_flow_body))
                    Spacer(modifier = Modifier.height(FreshNowSpacing.md))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_flow_step_scan),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.welcome_flow_step_read),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.welcome_flow_step_expiry),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                WelcomeStep.AI_CONFIG -> {
                    WelcomeHero(R.drawable.ic_base_config)
                    StepSpacer()
                    WelcomeTitle(stringResource(R.string.welcome_ai_title))
                    StepSpacer()
                    WelcomeBody(stringResource(R.string.welcome_ai_body))
                    Spacer(modifier = Modifier.height(FreshNowSpacing.md))
                    AiBasicConfigForm(
                        draft = basicConfig,
                        onBaseUrlChange = onBaseUrlChange,
                        onModelNameChange = onModelNameChange,
                        onApiKeyChange = onApiKeyChange
                    )
                }

                WelcomeStep.CAMERA -> {
                    WelcomeHero(R.drawable.ic_camera)
                    StepSpacer()
                    WelcomeTitle(stringResource(R.string.welcome_camera_title))
                    StepSpacer()
                    WelcomeBody(stringResource(R.string.welcome_camera_body))
                    if (cameraPermissionBlocked) {
                        Spacer(modifier = Modifier.height(FreshNowSpacing.sm))
                        Text(
                            text = stringResource(R.string.scan_permission_denied),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                WelcomeStep.DONE -> {
                    WelcomeHero(R.drawable.ic_done_all)
                    StepSpacer()
                    WelcomeTitle(stringResource(R.string.welcome_done_title))
                    StepSpacer()
                    WelcomeBody(stringResource(R.string.welcome_done_body))
                }
            }
        }
    }
}

/** 每一步的主视觉。装饰性的，语义由标题承担，因此不给 contentDescription */
@Composable
private fun WelcomeHero(iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(FreshNowSize.welcomeHero),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun StepSpacer() {
    Spacer(modifier = Modifier.height(FreshNowSpacing.sm))
}

@Composable
private fun WelcomeTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WelcomeBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** 第 1 步的三条要点。M3 规范建议这一屏最多讲三件事 */
@Composable
private fun Benefit(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}
