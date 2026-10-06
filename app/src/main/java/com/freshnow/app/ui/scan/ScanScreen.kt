package com.freshnow.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlinx.coroutines.launch

private const val CAMERA_ASPECT_RATIO = 3f / 4f

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.scan_saved)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    FreshNowSubPage(
        title = stringResource(R.string.scan),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        actions = {
            // 只在已经扫到内容时才给清空/保存入口，避免出现点了没反应的按钮
            if (uiState.record.hasAnyValue) {
                TextButton(onClick = viewModel::clearRecord) {
                    Text(text = stringResource(R.string.scan_clear))
                }
                TextButton(
                    onClick = {
                        if (viewModel.save()) {
                            scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                        }
                    }
                ) {
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FreshNowSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
            ) {
                ScanResultRow(
                    label = stringResource(R.string.scan_production_date),
                    value = uiState.record.productionDate
                )
                ScanResultRow(
                    label = stringResource(R.string.scan_expiry_date),
                    value = when (val expiry = uiState.expiry) {
                        is ExpiryOutcome.Resolved -> expiry.date
                        ExpiryOutcome.UnparseableShelfLife -> stringResource(R.string.scan_expiry_unparseable)
                        ExpiryOutcome.InsufficientInput -> stringResource(R.string.scan_value_unknown)
                    }
                )
                ScanResultRow(
                    label = stringResource(R.string.scan_shelf_life),
                    value = uiState.record.shelfLife
                )
            }

            ScanStatusText(
                status = uiState.status,
                modifier = Modifier.padding(top = FreshNowSpacing.sm)
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
    }
}

@Composable
private fun ScanResultRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { stringResource(R.string.scan_value_unknown) },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ScanStatusText(
    status: ScanStatus,
    modifier: Modifier = Modifier
) {
    val text = when (status) {
        ScanStatus.Idle -> null
        ScanStatus.Analyzing -> stringResource(R.string.scan_status_analyzing)
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
