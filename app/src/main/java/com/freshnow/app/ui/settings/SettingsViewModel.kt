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

data class SettingsUiState(
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
            _uiState.update {
                it.copy(baseUrl = saved.baseUrl, modelName = saved.modelName, apiKey = saved.apiKey)
            }
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

        viewModelScope.launch {
            repository.save(
                AiSettings(
                    baseUrl = current.baseUrl.trim(),
                    modelName = current.modelName.trim(),
                    apiKey = current.apiKey.trim()
                )
            )
        }
        return true
    }
}
