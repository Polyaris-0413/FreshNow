package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.freshnow.app.R
import com.freshnow.app.data.AiSettings
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 设置页的编辑面板与欢迎页的配置步骤共用这一份：字段、必填规则、落盘结果三处必须一致，
 * 各写一遍的话，日后改一处漏一处。
 */
data class AiBasicConfigDraft(
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val baseUrlError: Boolean = false,
    val modelNameError: Boolean = false,
    val apiKeyError: Boolean = false
) {

    companion object {
        fun of(settings: AiSettings) = AiBasicConfigDraft(
            baseUrl = settings.baseUrl,
            modelName = settings.modelName,
            apiKey = settings.apiKey
        )
    }

    fun validated(): AiBasicConfigDraft = copy(
        baseUrlError = baseUrl.isBlank(),
        modelNameError = modelName.isBlank(),
        apiKeyError = apiKey.isBlank()
    )

    /** 校验结果，读 [validated] 之后的值 */
    val isValid: Boolean get() = !baseUrlError && !modelNameError && !apiKeyError

    /**
     * 思考链与思考参数不属于这份表单，从 [saved] 原样带过来
     */
    fun toSettings(saved: AiSettings) = saved.copy(
        baseUrl = baseUrl.trim(),
        modelName = modelName.trim(),
        apiKey = apiKey.trim()
    )
}

/**
 * 设置页把它放进编辑面板，欢迎页把它摆在配置步骤里；标题与按钮由调用方给。
 */
@Composable
fun AiBasicConfigForm(
    draft: AiBasicConfigDraft,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        RequiredTextField(
            value = draft.baseUrl,
            onValueChange = onBaseUrlChange,
            label = stringResource(R.string.ai_settings_base_url_label),
            isError = draft.baseUrlError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = draft.modelName,
            onValueChange = onModelNameChange,
            label = stringResource(R.string.ai_settings_model_name_label),
            isError = draft.modelNameError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = draft.apiKey,
            onValueChange = onApiKeyChange,
            label = stringResource(R.string.ai_settings_api_key_label),
            isError = draft.apiKeyError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )
    }
}

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
