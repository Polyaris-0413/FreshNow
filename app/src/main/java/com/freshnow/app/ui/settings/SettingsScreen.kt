package com.freshnow.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showEditor by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.settings_saved)

    FreshNowSubPage(
        title = stringResource(R.string.settings),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        SettingsList(
            summary = uiState.saved.modelName.ifBlank { stringResource(R.string.ai_settings_not_configured) },
            onBasicConfigClick = {
                viewModel.startEditing()
                showEditor = true
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        )
    }

    if (showEditor) {
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState
        ) {
            AiBasicConfigEditor(
                uiState = uiState,
                onBaseUrlChange = viewModel::onBaseUrlChange,
                onModelNameChange = viewModel::onModelNameChange,
                onApiKeyChange = viewModel::onApiKeyChange,
                onSave = {
                    if (viewModel.save()) {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showEditor = false
                            scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsList(
    summary: String,
    onBasicConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 分区标题：M3 无专门组件，采用 Settings 惯例的 Title Small + primary
        Text(
            text = stringResource(R.string.ai_settings_section_title),
            modifier = Modifier.padding(
                start = FreshNowSpacing.sm,
                top = FreshNowSpacing.sm,
                end = FreshNowSpacing.sm,
                bottom = FreshNowSpacing.xxs
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // 两行列表项，字号由 ListItem 默认值给出（headline Body Large / supporting Body Medium）
        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.ai_settings_basic_group_title))
            },
            supportingContent = { Text(text = summary) },
            modifier = Modifier.clickable(onClick = onBasicConfigClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

/**
 * 「基础配置」的编辑面板，承载在 ModalBottomSheet 内
 */
@Composable
private fun AiBasicConfigEditor(
    uiState: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = FreshNowSpacing.sm)
            .padding(bottom = FreshNowSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.ai_settings_basic_group_title),
            style = MaterialTheme.typography.titleMedium
        )

        RequiredTextField(
            value = uiState.baseUrl,
            onValueChange = onBaseUrlChange,
            label = stringResource(R.string.ai_settings_base_url_label),
            isError = uiState.baseUrlError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = uiState.modelName,
            onValueChange = onModelNameChange,
            label = stringResource(R.string.ai_settings_model_name_label),
            isError = uiState.modelNameError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = uiState.apiKey,
            onValueChange = onApiKeyChange,
            label = stringResource(R.string.ai_settings_api_key_label),
            isError = uiState.apiKeyError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.action_save))
        }
    }
}

/**
 * 必填文本项，仅在未填时给出错误文案
 */
@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        supportingText = if (isError) {
            { Text(text = stringResource(R.string.error_required_field)) }
        } else {
            null
        },
        isError = isError,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions
    )
}

@Preview(showBackground = true)
@Composable
private fun AiBasicConfigEditorPreview() {
    FreshNowTheme {
        AiBasicConfigEditor(
            uiState = SettingsUiState(),
            onBaseUrlChange = {},
            onModelNameChange = {},
            onApiKeyChange = {},
            onSave = {},
            modifier = Modifier.padding(FreshNowSpacing.sm)
        )
    }
}
