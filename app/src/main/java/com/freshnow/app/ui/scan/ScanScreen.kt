package com.freshnow.app.ui.scan

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.openAppSettings
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTransitions
import com.freshnow.app.ui.showToast

// 取景框的宽高比。必须与 CameraPreview 的居中正方形裁剪保持一致：那里无条件裁正方形，
// 这里一旦改成非 1f，显示的取景范围与真正送出去的画面就会错开，而且不会有任何报错
private const val CAMERA_ASPECT_RATIO = 1f

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // 请求已发出、结果还没到。首次进入本页会自动弹一次，这段时间里系统弹窗正盖在界面上，
    // 不能把「未授予且系统不再弹窗」判成永久拒绝
    var awaitingPermissionAnswer by remember { mutableStateOf(!hasCameraPermission) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        awaitingPermissionAnswer = false
    }

    // 权限也能在系统设置里改，回来时以真实权限为准（从设置页开完权限回来要立刻恢复取景）
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    // 系统不再弹窗（rationale 为 false）说明用户已拒两次、或已被策略禁止，此时再调请求不会有任何反应，
    // 只能引导用户去设置页（见 Android 关于 shouldShowRequestPermissionRationale 的说明）
    val permissionBlocked = !hasCameraPermission &&
        !awaitingPermissionAnswer &&
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false

    var showSaveDialog by remember { mutableStateOf(false) }
    // 手电筒是相机的状态而不是页面数据，不进 ViewModel；但换版式或旋转会重建 Activity，
    // 灯要跟着用户的意图重新亮起，所以用 rememberSaveable
    var torchOn by rememberSaveable { mutableStateOf(false) }
    val hasResult = uiState.record.hasAnyValue

    // 保存成功即退出扫描页：结果已经进了列表，留在本页没有意义，也避免误以为还没保存
    fun saveAndLeave() {
        if (viewModel.save()) onBack()
    }

    // 有结果时先问一句再走，避免扫到的结果被静默丢掉；没有结果就直接退。
    // 顶栏返回箭头走同一套判断，否则箭头会绕过这里静默丢结果。
    BackHandler(enabled = hasResult) { showSaveDialog = true }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            awaitingPermissionAnswer = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestCameraPermission() {
        awaitingPermissionAnswer = true
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    FreshNowSubPage(
        title = stringResource(R.string.scan),
        onBack = { if (hasResult) showSaveDialog = true else onBack() },
        modifier = modifier
    ) { innerPadding ->
        // 两种排布下相机的回调完全相同，只有尺寸约束不同，因此只把尺寸交给调用方决定
        val cameraBox: @Composable (Modifier) -> Unit = { sizeConstraint ->
            CameraBox(
                hasCameraPermission = hasCameraPermission,
                permissionBlocked = permissionBlocked,
                // 被永久拒绝时再调请求不会有任何反应，改跳系统设置页
                onRequestPermission = {
                    if (permissionBlocked) openAppSettings(context) else requestCameraPermission()
                },
                torchOn = torchOn,
                onTorchChange = { torchOn = it },
                canAcceptFrame = viewModel::canAcceptFrame,
                onFrame = viewModel::submitFrame,
                modifier = sizeConstraint
            )
        }

        // 操作区跟着内容走而不是放进 Scaffold 的 bottomBar：横屏时它只占结果栏底部，
        // 通栏的话会把按高度定尺寸的取景框一并压矮。若交出一半给 bottomBar，
        // 就会出现两个「当前是不是横屏」的判据（版式看可用空间、bottomBar 看窗口尺寸），可能互相矛盾。
        val actionBar: @Composable () -> Unit = {
            ScanActionBar(
                enabled = hasResult,
                onClear = viewModel::clearRecord,
                onSave = { saveAndLeave() }
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(FreshNowSpacing.sm)
        ) {
            // 【有意偏离 M3 规范，请勿「按规范」改回去】
            // M3 要求 600dp 起切换多窗格、并给大屏内容加 840dp 宽度上限。本项目只面向手机形态
            // （竖屏与横屏），不做平板/折叠屏多窗格，也不做宽度约束——全应用没有窗口尺寸类分支。
            // 因此这里唯一的尺寸判断只针对「高度比宽度更紧张」这一种情况。
            //
            // 宽不小于高（横屏，或接近方形的分屏）时高度才是稀缺资源：上下排布会把正方形取景框
            // 撑成远高于可视区的长条，取景框只露出顶部一截、识别结果被顶到屏幕外，
            // 且露出的画面对不上「看到什么就裁什么」的裁剪假设。此时改为左右并排，
            // 取景框按可用高度取正方形，结果在右侧单独滚动，相机在滚动时保持可见。
            if (maxWidth >= maxHeight) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                ) {
                    // 只定高：边长即取可用高度，宽度不够时取景框内部的 aspectRatio 会按宽度回落。
                    // 操作区在右侧栏内，因此这里的可用高度不受它影响
                    cameraBox(Modifier.fillMaxHeight())
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                    ) {
                        val resultsScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            ScanResultColumn(
                                uiState = uiState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(resultsScroll)
                            )
                            ScrollEdgeFade(
                                visible = resultsScroll.canScrollForward,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                        actionBar()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                ) {
                    val pageScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 竖屏下相机跟着结果一起滚；横屏下它在滚动区之外，滚动时保持可见
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(pageScroll),
                            verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                        ) {
                            // 只定宽；方形由 CameraBox 自己保证
                            cameraBox(Modifier.fillMaxWidth())
                            ScanResultColumn(uiState = uiState)
                        }
                        ScrollEdgeFade(
                            visible = pageScroll.canScrollForward,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    actionBar()
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(text = stringResource(R.string.scan_save_dialog_title)) },
            // 正文说清这一问的代价。只有标题的话，标题下面空出一块，对话框显得又扁又空；
            // 主页那个删除对话框同样带正文，两者就此一致
            text = { Text(text = stringResource(R.string.scan_save_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        saveAndLeave()
                    }
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        onBack()
                    }
                ) {
                    Text(text = stringResource(R.string.scan_save_dialog_discard))
                }
            }
        )
    }

    // 保存确认优先：有结果且服务正好连不上时两个对话框都该弹，叠在一起只会看到最上面那个
    uiState.serviceProblem?.let { problem ->
        if (showSaveDialog) return@let
        ServiceUnavailableDialog(
            problem = problem,
            onOpenSettings = {
                viewModel.closeServiceDialog()
                // 压栈而非切换目的地：改完配置回来仍落在扫描页，相机与累加记录都还在
                onNavigateToSettings()
            },
            onAcknowledge = {
                viewModel.closeServiceDialog()
                // 服务都用不了，留在本页只是等下一次弹窗；但已扫到的内容不能因为服务挂了就静默丢掉，
                // 因此先走保存那一问（有结果时）
                if (hasResult) showSaveDialog = true else onBack()
            }
        )
    }
}

/**
 * AI 服务用不了时的说明。
 *
 * 正文不显示服务端返回了什么：日志是给别的 AI 读的，一键复制比在对话框里摊开一段原始报错省事，
 * 正文也就不必再为长文本留滑块区。日志本身仍要经手一遍——「复制」写进剪贴板的就是它。
 *
 * 确认位放什么取决于处境：能复制时就复制（本页的 AI 已经用不了了，把日志交给别的 AI 是此时唯一
 * 还有意义的动作）；没配置过时没有日志可交出去，改给去设置的入口——那时用户要的只是去哪填。
 * 「知道了」两种情况都是回主页。规范限制对话框最多两个动作，而两条路正好装满。
 *
 * 提为 internal 是为了能在仪器化测试里直接断言两个入口：设备上要复现「服务连不上」，
 * 得真的把配置写坏或断网。
 */
@Composable
internal fun ServiceUnavailableDialog(
    problem: ServiceProblem,
    onOpenSettings: () -> Unit,
    onAcknowledge: () -> Unit
) {
    val context = LocalContext.current
    val copiedToast = stringResource(R.string.scan_service_dialog_copied)

    AlertDialog(
        onDismissRequest = onAcknowledge,
        title = { Text(text = stringResource(R.string.scan_service_dialog_title)) },
        text = {
            Text(
                text = when (problem) {
                    ServiceProblem.NotConfigured -> stringResource(R.string.scan_ai_not_configured)
                    is ServiceProblem.Failed -> stringResource(R.string.scan_service_dialog_message)
                }
            )
        },
        confirmButton = {
            when (problem) {
                ServiceProblem.NotConfigured -> TextButton(onClick = onOpenSettings) {
                    Text(text = stringResource(R.string.action_open_settings))
                }

                is ServiceProblem.Failed -> TextButton(
                    onClick = {
                        // 写进剪贴板这一步在界面上看不出来，所以必须回一句提示
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText(null, problem.log))
                        showToast(context, copiedToast)
                    }
                ) {
                    Text(text = stringResource(R.string.scan_service_dialog_copy))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onAcknowledge) {
                Text(text = stringResource(R.string.scan_service_dialog_dismiss))
            }
        }
    )
}

/**
 * 底部操作区：清空与保存。
 *
 * 放在底部而非顶栏：这两个是屏幕级上下文操作，M3 给这类操作的位置就是底部，顶栏留给返回这类
 * 全局导航；底部伸手可及，也比顶栏好按。
 *
 * 就是一个 Row、不带自己的底色：套 BottomAppBar 会多出一层 surfaceContainer，
 * 在页面底部压出一条与顶栏不对称的色带——顶栏被刻意设成 background 正是为了避免这种色差。
 * 两个按钮按内容宽度分靠两端而不各占半屏：等宽按钮在手机上会变成两块巨大的平板，
 * 描边那块的形状与文字比例尤其失衡。
 *
 * 无内容时置灰而不隐藏：隐藏会让它在第一条识别结果到达的瞬间凭空出现、把上方内容顶一下。
 * 保存是主操作（填充按钮）、清空是次操作（文字按钮），与设置页的取消/保存同一套主次关系。
 *
 * 提为 internal 是为了能在仪器化测试里直接断言置灰与可点两种状态：设备上要出现「已扫到内容」
 * 得靠相机真的拍到标签，测试里没法复现。
 */
@Composable
internal fun ScanActionBar(
    enabled: Boolean,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onClear, enabled = enabled) {
            Text(text = stringResource(R.string.scan_clear))
        }
        Button(onClick = onSave, enabled = enabled) {
            Text(text = stringResource(R.string.action_save))
        }
    }
}

/**
 * 识别结果一列：结果字段，外加可选的思维链面板。
 *
 * 只负责排布，不决定滚动方式——竖屏时它跟着相机一起滚，横屏时占右半屏单独滚，由调用方给。
 * 块间距用 spacedBy 统一给出。
 */
@Composable
private fun ScanResultColumn(
    uiState: ScanUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        FreshNowResultFields(
            productName = uiState.record.productName,
            productionDate = uiState.record.productionDate,
            expiry = uiState.expiry,
            shelfLife = uiState.record.shelfLife
        )

        // 识别失败、未配置这类问题不再在本列重复一遍：报错原因由说明对话框给出

        if (uiState.showReasoning) {
            ReasoningPanel(reasoning = uiState.reasoning)
        }
    }
}

/**
 * 模型思维链面板，由「设置 → 显示思维链」控制是否出现。
 *
 * 不设高度上限、也不在内部滚动：整页只留一个滚动容器。先前给它定高 + 内部滚动是为了不让下方
 * 内容跟着思维链长度跳，但它是本列最后一项，下面无物可顶，那条理由不成立；留着反而多出一层
 * 同轴滚动——手指落在框里时滚动被框吃掉，页面纹丝不动，而框自己又没有「还能滚」的提示。
 */
@Composable
private fun ReasoningPanel(reasoning: String) {
    Column {
        Text(
            text = stringResource(R.string.scan_reasoning_title),
            modifier = Modifier.padding(bottom = FreshNowSpacing.xxs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(FreshNowSpacing.sm)
        ) {
            Text(
                text = reasoning.ifBlank { stringResource(R.string.scan_reasoning_empty) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 滚动区底边的渐隐：内容被折叠线裁掉时，用渐隐代替一条硬边，提示「下面还有」。
 *
 * 只在 [visible]（即调用方读的 `ScrollState.canScrollForward`）为真时出现：还能往下滚才提示。
 * 用渐隐而不是滚动条：M3 的滚动条组件要 material3 1.5（本项目锁在 1.4.0 稳定线），而渐隐是
 * Google 自己一贯的手法（Android 15 起平台对内容滚到系统栏下方也做同样的边缘渐隐），且它表达的
 * 正是我们缺的那个语义——「内容还在往下延伸」，而不是「滚到了哪儿」。
 *
 * 自身不占布局：叠在滚动区之上，所以调用方要把它放进与滚动区同一个 Box 里、对齐到底部。
 * 不做淡入淡出：它随滚动位置出现/消失，本来就是个瞬时信号，加动画反而多一层状态。
 *
 * 提为 internal 是为了能在仪器化测试里直接断言显隐两态。
 */
@Composable
internal fun ScrollEdgeFade(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FreshNowSize.scrollEdgeFade)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            )
    )
}

/**
 * 取景框。
 *
 * 分工：调用方给「多大」（竖屏 fillMaxWidth、横屏 fillMaxHeight），本组件保证「是方的」。
 * 方形不是审美偏好而是功能约束——CameraPreview 会把送给 AI 的整帧裁成居中正方形，
 * 所以取景框一旦不是方的，「看到什么就裁什么」就会静默失效：用户看到整幅画面，模型只收到中间一块。
 *
 * 手电筒叠在取景框内角：它是相机的配件，贴在画面上才读得出属于相机。放在这里也是两种版式
 * 唯一共用的节点——横屏时取景框在左栏，按钮跟着相机走，不会跑到右侧的结果栏去。
 */
@Composable
private fun CameraBox(
    hasCameraPermission: Boolean,
    permissionBlocked: Boolean,
    onRequestPermission: () -> Unit,
    torchOn: Boolean,
    onTorchChange: (Boolean) -> Unit,
    canAcceptFrame: () -> Boolean,
    onFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // 无闪光灯的设备没有可开的灯。这是设备属性，不是编译期常量，所以问一次相机再决定出不出现
    var torchAvailable by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(CAMERA_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                torchOn = torchOn,
                onTorchAvailabilityChange = { torchAvailable = it },
                canAcceptFrame = canAcceptFrame,
                onFrame = onFrame,
                modifier = Modifier.fillMaxSize()
            )
            // 权限未授予时相机根本不存在，也就谈不上开灯，按钮同样不给
            if (torchAvailable) {
                // FilledIconToggleButton 自带的选中态配色是瞬时切换的，而这里要的是颜色过渡：
                // 选中与未选中两组色都喂同一个动画值，开关语义仍由 checked 提供，颜色由我们演。
                // 两端取值就是该组件的 token（未选中 = secondaryContainer 底 + primary 图标，
                // 选中 = primary 底 + onPrimary 图标），见 FilledIconButtonTokens
                val containerColor by animateColorAsState(
                    targetValue = if (torchOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    animationSpec = FreshNowTransitions.stateChange(),
                    label = "flashlightContainerColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (torchOn) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    animationSpec = FreshNowTransitions.stateChange(),
                    label = "flashlightContentColor"
                )
                FilledIconToggleButton(
                    checked = torchOn,
                    onCheckedChange = onTorchChange,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                        checkedContainerColor = containerColor,
                        checkedContentColor = contentColor,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(FreshNowSpacing.xs)
                ) {
                    // 开与关共用同一个图标，状态只由上面的颜色表达
                    Icon(
                        painter = painterResource(R.drawable.ic_flashlight_on),
                        contentDescription = stringResource(
                            if (torchOn) R.string.scan_flashlight_off else R.string.scan_flashlight_on
                        )
                    )
                }
            }
        } else {
            CameraPermissionHint(
                permissionBlocked = permissionBlocked,
                onGrantPermission = onRequestPermission,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * 没有相机权限时的空态。
 *
 * 做成「图 + 文 + 动作」三件套，而不是只摆一句提示加一个按钮：取景框里本来该有画面，只是一时
 * 显示不出来，这正是 Material 定义的空态（内容显示不了的区域，用非交互图像 + 一句 tagline 交代，
 * 需要用户动手时再补一个动作）。取值全走设计源：图标边长用 [FreshNowSize.icon]（那一档就是
 * 空态/占位图标边长）、间距用 [FreshNowSpacing]，与主页的「还没有扫描记录」同一套。
 *
 * 图是装饰，语义由文案承担，因此不写 contentDescription，读屏软件不会把同一件事读两遍。
 *
 * 提为 internal 是为了能在仪器化测试里直接断言两种形态：设备上要复现「权限被永久拒绝」，
 * 得真的把系统弹窗连拒两次。
 */
@Composable
internal fun CameraPermissionHint(
    permissionBlocked: Boolean,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(FreshNowSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_camera),
            contentDescription = null,
            modifier = Modifier.size(FreshNowSize.icon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                // 被永久拒绝时要交待清为何点下去不再弹窗，只说「需要权限」会让人以为按钮坏了
                if (permissionBlocked) R.string.scan_permission_denied else R.string.scan_permission_required
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onGrantPermission) {
            Text(
                text = stringResource(
                    if (permissionBlocked) R.string.action_open_settings else R.string.scan_grant_permission
                )
            )
        }
    }
}
