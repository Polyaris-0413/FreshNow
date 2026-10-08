package com.freshnow.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing

// 取景框的宽高比。必须与 CameraPreview 的居中正方形裁剪保持一致：那里无条件裁正方形，
// 这里一旦改成非 1f，显示的取景范围与真正送出去的画面就会错开，而且不会有任何报错
private const val CAMERA_ASPECT_RATIO = 1f

// 思维链面板的高度上限，约 12 行正文，超出部分面板内滚动
private val REASONING_MAX_HEIGHT = FreshNowSize.scrollableTextPanelHeight

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

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
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
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
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
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
                        ScanResultColumn(
                            uiState = uiState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                        actionBar()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                ) {
                    // 竖屏下相机跟着结果一起滚动；横屏下它在滚动区之外，滚动时保持可见
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                    ) {
                        // 只定宽；方形由 CameraBox 自己保证
                        cameraBox(Modifier.fillMaxWidth())
                        ScanResultColumn(uiState = uiState)
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
 * 识别结果一列：结果字段、状态文案、可选的思维链面板。
 *
 * 只负责排布，不决定滚动方式——竖屏时它跟着相机一起滚，横屏时占右半屏单独滚，由调用方给。
 * 块间距用 spacedBy 统一给出；状态文案在无需提示时不产生布局节点，因此不会多留一道空档。
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

        ScanStatusText(status = uiState.status)

        if (uiState.showReasoning) {
            ReasoningPanel(reasoning = uiState.reasoning)
        }
    }
}

/**
 * 模型思维链面板，由「设置 → 显示思维链」控制是否出现。
 *
 * 定高 + 内部滚动是有意的：实时扫描每两秒换一帧，思维链长度每帧都在变，
 * 不设上限的话下方内容会跟着上下跳动，识别结果也会被挤出可视区。
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
                .heightIn(max = REASONING_MAX_HEIGHT)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .verticalScroll(rememberScrollState())
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
                FilledIconToggleButton(
                    checked = torchOn,
                    onCheckedChange = onTorchChange,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(FreshNowSpacing.xs)
                ) {
                    // 图标是状态（对准了没），文案是动作（点下去会怎样），两者各说各的话
                    Icon(
                        painter = painterResource(
                            if (torchOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off
                        ),
                        contentDescription = stringResource(
                            if (torchOn) R.string.scan_flashlight_off else R.string.scan_flashlight_on
                        )
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(FreshNowSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.scan_permission_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRequestPermission) {
                    Text(text = stringResource(R.string.scan_grant_permission))
                }
            }
        }
    }
}

@Composable
private fun ScanStatusText(status: ScanStatus) {
    val text = when (status) {
        // 识别中不显示文案：实时扫描下这个状态每隔一两秒就在识别与空闲之间来回切，文字会不停闪现
        ScanStatus.Idle, ScanStatus.Analyzing -> null
        ScanStatus.NotConfigured -> stringResource(R.string.scan_ai_not_configured)
        is ScanStatus.Failed -> status.detail
    }
    if (text == null) return

    val isProblem = status is ScanStatus.Failed || status == ScanStatus.NotConfigured
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
