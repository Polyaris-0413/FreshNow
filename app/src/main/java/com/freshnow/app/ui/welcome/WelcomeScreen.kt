package com.freshnow.app.ui.welcome

import androidx.activity.compose.BackHandler
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
 * 规范要求跳过始终可见可用，因此「跳过」不随步骤消失，返回键与它等价。
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
    val configSaved by viewModel.configSaved.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val cameraPermission = rememberCameraPermissionState()
    val context = LocalContext.current

    LaunchedEffect(finished) {
        if (finished) onFinish()
    }

    // 返回键等同跳过：引导不是必经之路，但同样要先记下「已看过」，否则下次启动又会被请回来
    BackHandler(enabled = !finished) { viewModel.finish() }

    WelcomeSteps(
        basicConfig = basicConfig,
        configSaved = configSaved,
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
 * 欢迎页的版式本身：几步内容 + 进度 + 跳过与主按钮。
 *
 * [configSaved] 是「配置已经落盘」而不是「校验过了」：推进到权限那一步必须等落盘真的完成，
 * 否则推进过去的那一刻配置还没写进 DataStore。
 */
@Composable
internal fun WelcomeSteps(
    basicConfig: AiBasicConfigDraft,
    configSaved: Boolean,
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

    LaunchedEffect(configSaved) {
        if (configSaved) pagerState.animateScrollToPage(WelcomeStep.CAMERA.ordinal)
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
                // 进度用 M3 的进度指示器而不是自绘圆点：规范点名「steppers 与 progress indicators」，
                // 并提醒别拿随步骤变化的插图当进度（那会被当成装饰）
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSkip) {
                        Text(text = stringResource(R.string.welcome_action_skip))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            when (currentStep) {
                                WelcomeStep.INTRO, WelcomeStep.FLOW -> scope.launch {
                                    pagerState.animateScrollToPage(currentStep.ordinal + 1)
                                }

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
