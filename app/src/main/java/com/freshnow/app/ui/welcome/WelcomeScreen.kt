package com.freshnow.app.ui.welcome

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
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
internal enum class WelcomeStep { INTRO, FLOW, AI_CONFIG, CAMERA }

/**
 * 首次启动的欢迎页，只在「这次安装还没走过引导」时由 [com.freshnow.app.ui.FreshNowApp] 渲染
 * （见 OnboardingRepository）。
 *
 * 引导的整体形状取 Material 的「Top User Benefits」模型：先给价值（第 1 步），再说清用它的代价
 * （第 2 步：识别要自备模型服务），然后是动作（第 3 步配置、第 4 步相机权限）。
 * 「跳过」跳过的是当前这一步要干的事，不是整个引导：信息页上是往前走一项，配置页是不填，
 * 权限页是不授权；最后一步无处可去，点它才是结束引导。
 * 「上一步」与系统返回键都能往回走；屏上没有「结束引导」这颗按钮，那个出口由返回键承担
 * （停在第一步时按返回即结束）。
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

    LaunchedEffect(finished) {
        if (finished) onFinish()
    }

    WelcomeSteps(
        basicConfig = basicConfig,
        configSavedCount = configSavedCount,
        cameraPermissionBlocked = cameraPermission.blocked,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onModelNameChange = viewModel::onModelNameChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSaveConfig = viewModel::saveBasicConfig,
        // 权限被系统锁死时再点也不会有弹窗（见 CameraPermission），只能去设置页；其余情况请求一次
        // 就收尾，用户拒了也能在扫描页重新走那套提示
        onCameraAction = {
            if (cameraPermission.blocked) {
                openAppSettings(context)
            } else {
                if (!cameraPermission.granted) cameraPermission.request()
                viewModel.finish()
            }
        },
        onSkip = viewModel::finish,
        modifier = modifier
    )
}

/**
 * 欢迎页的版式本身：几步内容 + 进度 + 跳过、上一步与主按钮。
 *
 * [configSavedCount] 是「配置成功落盘的次数」而不是「校验过了」：推进到权限那一步必须等落盘真的
 * 完成，否则推进过去的那一刻配置还没写进 DataStore。
 */
@Composable
internal fun WelcomeSteps(
    basicConfig: AiBasicConfigDraft,
    configSavedCount: Int,
    cameraPermissionBlocked: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveConfig: () -> Unit,
    onCameraAction: () -> Unit,
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

    LaunchedEffect(configSavedCount) {
        if (configSavedCount > 0) goTo(WelcomeStep.CAMERA)
    }

    // 系统返回键与「上一步」同一件事；已经在第 1 步时退回结束引导
    BackHandler {
        if (pagerState.currentPage > 0) goTo(steps[pagerState.currentPage - 1]) else onSkip()
    }

    /**
     * 「跳过」跳过的是这一步要做的事，不是整个引导：一律往前走一项。最后一步走不动了，
     * 那才是结束引导。
     */
    fun skipCurrentStep() {
        if (currentStep == WelcomeStep.CAMERA) onSkip() else goTo(steps[currentStep.ordinal + 1])
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
                    TextButton(onClick = { skipCurrentStep() }) {
                        Text(text = stringResource(R.string.welcome_action_skip))
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    if (pagerState.currentPage > 0) {
                        TextButton(onClick = { goTo(steps[pagerState.currentPage - 1]) }) {
                            Text(text = stringResource(R.string.welcome_action_previous))
                        }
                    }

                    Button(
                        onClick = {
                            when (currentStep) {
                                WelcomeStep.INTRO, WelcomeStep.FLOW ->
                                    goTo(steps[currentStep.ordinal + 1])

                                WelcomeStep.AI_CONFIG -> onSaveConfig()

                                WelcomeStep.CAMERA -> onCameraAction()
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(
                                when (currentStep) {
                                    WelcomeStep.INTRO, WelcomeStep.FLOW -> R.string.welcome_action_next
                                    WelcomeStep.AI_CONFIG -> R.string.welcome_action_save_continue
                                    WelcomeStep.CAMERA ->
                                        if (cameraPermissionBlocked) {
                                            R.string.action_open_settings
                                        } else {
                                            R.string.welcome_action_grant_camera
                                        }
                                }
                            )
                        )
                        if (currentStep == WelcomeStep.INTRO || currentStep == WelcomeStep.FLOW) {
                            Spacer(modifier = Modifier.width(FreshNowSpacing.xs))
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_forward),
                                // 箭头就是「下一步」这三个字的形状，读屏再念一遍是重复
                                contentDescription = null,
                                modifier = Modifier.size(FreshNowSize.buttonIcon)
                            )
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
