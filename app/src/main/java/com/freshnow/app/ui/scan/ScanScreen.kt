package com.freshnow.app.ui.scan

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.ScrollEdgeFade
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.showToast

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberCameraPermissionState()

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

    // 进页即请求：用户按「扫描」就是明确的用相机意图，不必再点一次
    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }

    FreshNowSubPage(
        title = stringResource(R.string.scan),
        onBack = { if (hasResult) showSaveDialog = true else onBack() },
        modifier = modifier
    ) { innerPadding ->
        // 两种排布下相机的回调完全相同，只有尺寸约束不同，因此只把尺寸交给调用方决定
        val cameraBox: @Composable (Modifier) -> Unit = { sizeConstraint ->
            CameraBox(
                permission = permission,
                torchOn = torchOn,
                onTorchChange = { torchOn = it },
                canAcceptFrame = viewModel::canAcceptFrame,
                onFrame = viewModel::submitFrame,
                modifier = sizeConstraint
            )
        }

        // 操作区放内容区底部而不是 Scaffold 的 bottomBar：它与上方内容共用外层 Column 的那一层
        // 留白（FreshNowSpacing.sm），交给 Scaffold 就得在 bottomBar 里再写一遍同一档留白，
        // 两处日后会各漂各的。
        val actionBar: @Composable () -> Unit = {
            ScanActionBar(
                enabled = hasResult,
                onClear = viewModel::clearRecord,
                onSave = { saveAndLeave() }
            )
        }

        // 【有意偏离 M3 规范，请勿「按规范」改回去】
        // M3 要求 600dp 起切换多窗格、并给大屏内容加 840dp 宽度上限。本项目只面向手机形态，
        // 不做平板/折叠屏多窗格，也不做宽度约束——Activity 锁竖屏（见 AndroidManifest），
        // 所以这里不按宽高比分支，只有一种版式。
        //
        // 取景框撑满宽度、结果是主角，两者都要在，只能让位给滚动：相机跟着结果一起滚，
        // 滚上去之后取景框会移出可视区。这是竖屏下早就有的取舍。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(FreshNowSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
        ) {
            val pageScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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
 * 只负责排布，不决定滚动方式——目前它与取景框同处一个滚动列，滚动交给调用方。
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
 * 扫描页的取景框：按宽度定出正方形的外壳，内容交给 [CameraViewport]。
 *
 * 方形不是审美偏好而是功能约束——CameraPreview 会把送给 AI 的整帧裁成居中正方形，
 * 所以取景框一旦不是方的，「看到什么就裁什么」就会静默失效：用户看到整幅画面，模型只收到中间一块。
 */
@Composable
private fun CameraBox(
    permission: CameraPermissionState,
    torchOn: Boolean,
    onTorchChange: (Boolean) -> Unit,
    canAcceptFrame: () -> Boolean,
    onFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(CAMERA_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        CameraViewport(
            permission = permission,
            torchOn = torchOn,
            onTorchChange = onTorchChange,
            canAcceptFrame = canAcceptFrame,
            onFrame = onFrame,
            modifier = Modifier.fillMaxSize()
        )
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
    modifier: Modifier = Modifier,
    /** 未授予时的说明。各页的用途不同（扫描 / 拍照），措辞也跟着不同 */
    requiredMessage: String = stringResource(R.string.scan_permission_required)
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
            text = if (permissionBlocked) {
                // 被永久拒绝时要交待清为何点下去不再弹窗，只说「需要权限」会让人以为按钮坏了
                stringResource(R.string.scan_permission_denied)
            } else {
                requiredMessage
            },
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
