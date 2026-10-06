package com.freshnow.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.theme.FreshNowSpacing

// 正方形取景框。取景框只决定预览怎么裁切显示，送给 AI 分析的始终是整帧，所以改比例不影响识别
private const val CAMERA_ASPECT_RATIO = 1f

// 取景框描边宽度，取 M3 描边容器（outlined card）的 1dp
private val CAMERA_FRAME_WIDTH = 1.dp

// 思维链面板的高度上限，取设计源最大间距的 4 倍（约 12 行正文），超出部分面板内滚动
private val REASONING_MAX_HEIGHT = FreshNowSpacing.xl * 4

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
        modifier = modifier,
        actions = {
            // 只在已经扫到内容时才给清空/保存入口，避免出现点了没反应的按钮
            if (hasResult) {
                TextButton(onClick = viewModel::clearRecord) {
                    Text(text = stringResource(R.string.scan_clear))
                }
                TextButton(onClick = { saveAndLeave() }) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(FreshNowSpacing.sm)
        ) {
            CameraBox(
                hasCameraPermission = hasCameraPermission,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                canAcceptFrame = viewModel::canAcceptFrame,
                onFrame = viewModel::submitFrame
            )

            FreshNowResultFields(
                productName = uiState.record.productName,
                productionDate = uiState.record.productionDate,
                expiry = uiState.expiry,
                shelfLife = uiState.record.shelfLife,
                modifier = Modifier.padding(top = FreshNowSpacing.sm)
            )

            ScanStatusText(
                status = uiState.status,
                modifier = Modifier.padding(top = FreshNowSpacing.sm)
            )

            if (uiState.showReasoning) {
                ReasoningPanel(
                    reasoning = uiState.reasoning,
                    modifier = Modifier.padding(top = FreshNowSpacing.sm)
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(text = stringResource(R.string.scan_save_dialog_title)) },
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
 * 模型思维链面板，由「设置 → 显示思维链」控制是否出现。
 *
 * 定高 + 内部滚动是有意的：实时扫描每两秒换一帧，思维链长度每帧都在变，
 * 不设上限的话下方内容会跟着上下跳动，识别结果也会被挤出可视区。
 */
@Composable
private fun ReasoningPanel(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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

@Composable
private fun CameraBox(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    canAcceptFrame: () -> Boolean,
    onFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CAMERA_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                canAcceptFrame = canAcceptFrame,
                onFrame = onFrame,
                modifier = Modifier.fillMaxSize()
            )
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

        // 描边要盖在预览之上：Box 自身的 border 会先于子级绘制，被预览层整个遮住
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = CAMERA_FRAME_WIDTH,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.medium
                )
        )
    }
}

@Composable
private fun ScanStatusText(
    status: ScanStatus,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
