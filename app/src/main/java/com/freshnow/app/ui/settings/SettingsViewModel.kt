package com.freshnow.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.AiSettings
import com.freshnow.app.data.AiSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [saved] 为已落盘的配置，设置项行展示它；其余字段是编辑面板的草稿，仅打开面板时从 [saved] 重置
 */
data class SettingsUiState(
    val saved: AiSettings = AiSettings(),
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val baseUrlError: Boolean = false,
    val modelNameError: Boolean = false,
    val apiKeyError: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AiSettingsRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.aiSettings.first()
            _uiState.update { it.copy(saved = saved) }
        }
    }

    /**
     * 打开编辑面板时用已落盘的值重置草稿，丢弃上次未保存的改动
     */
    fun startEditing() {
        _uiState.update {
            it.copy(
                baseUrl = it.saved.baseUrl,
                modelName = it.saved.modelName,
                apiKey = it.saved.apiKey,
                baseUrlError = false,
                modelNameError = false,
                apiKeyError = false
            )
        }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value, baseUrlError = false) }
    }

    fun onModelNameChange(value: String) {
        _uiState.update { it.copy(modelName = value, modelNameError = false) }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, apiKeyError = false) }
    }

    /**
     * 校验三项必填，未通过时在对应字段上标错并返回 false；通过则落盘并返回 true
     */
    fun save(): Boolean {
        val current = _uiState.value
        val baseUrlMissing = current.baseUrl.isBlank()
        val modelNameMissing = current.modelName.isBlank()
        val apiKeyMissing = current.apiKey.isBlank()

        if (baseUrlMissing || modelNameMissing || apiKeyMissing) {
            _uiState.update {
                it.copy(
                    baseUrlError = baseUrlMissing,
                    modelNameError = modelNameMissing,
                    apiKeyError = apiKeyMissing
                )
            }
            return false
        }

        val settings = AiSettings(
            baseUrl = current.baseUrl.trim(),
            modelName = current.modelName.trim(),
            apiKey = current.apiKey.trim()
        )
        viewModelScope.launch { repository.save(settings) }
        _uiState.update {
            it.copy(
                saved = settings,
                baseUrl = settings.baseUrl,
                modelName = settings.modelName,
                apiKey = settings.apiKey
            )
        }
        return true
    }
}
