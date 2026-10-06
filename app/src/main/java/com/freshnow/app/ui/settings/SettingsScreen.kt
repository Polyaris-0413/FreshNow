package com.freshnow.app.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.theme.FreshNowTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.settings_saved)

    FreshNowSubPage(
        title = stringResource(R.string.settings),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        SettingsForm(
            uiState = uiState,
            onBaseUrlChange = viewModel::onBaseUrlChange,
            onModelNameChange = viewModel::onModelNameChange,
            onApiKeyChange = viewModel::onApiKeyChange,
            onSave = {
                if (viewModel.save()) {
                    scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SettingsForm(
    uiState: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.ai_settings_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.ai_settings_required_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        RequiredTextField(
            value = uiState.baseUrl,
            onValueChange = onBaseUrlChange,
            label = stringResource(R.string.ai_settings_base_url_label),
            hint = stringResource(R.string.ai_settings_base_url_hint),
            isError = uiState.baseUrlError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = uiState.modelName,
            onValueChange = onModelNameChange,
            label = stringResource(R.string.ai_settings_model_name_label),
            hint = stringResource(R.string.ai_settings_model_name_hint),
            isError = uiState.modelNameError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = uiState.apiKey,
            onValueChange = onApiKeyChange,
            label = stringResource(R.string.ai_settings_api_key_label),
            hint = stringResource(R.string.ai_settings_api_key_hint),
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
 * 必填文本项：未填时以 isError 标红并把提示文案替换为必填错误
 */
@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
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
        supportingText = {
            Text(text = if (isError) stringResource(R.string.error_required_field) else hint)
        },
        isError = isError,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsFormPreview() {
    FreshNowTheme {
        SettingsForm(
            uiState = SettingsUiState(),
            onBaseUrlChange = {},
            onModelNameChange = {},
            onApiKeyChange = {},
            onSave = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
