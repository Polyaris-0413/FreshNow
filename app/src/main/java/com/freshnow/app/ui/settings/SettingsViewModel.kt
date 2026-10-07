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
 * [loaded] 表示 [saved] 是否已从 DataStore 读出，为 false 时界面不应渲染设置项
 */
data class SettingsUiState(
    val loaded: Boolean = false,
    val saved: AiSettings = AiSettings(),
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val extraJson: String = "",
    val baseUrlError: Boolean = false,
    val modelNameError: Boolean = false,
    val apiKeyError: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AiSettingsRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.aiSettings.first()
            _uiState.update { it.copy(loaded = true, saved = saved) }
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
     * 开关是即时生效项，不经过编辑面板：先更新界面再落盘
     */
    fun onShowReasoningChange(value: Boolean) {
        val updated = _uiState.value.saved.copy(showReasoning = value)
        _uiState.update { it.copy(saved = updated) }
        viewModelScope.launch { repository.save(updated) }
    }

    /**
     * 打开思考参数面板时用已落盘的值重置草稿
     */
    fun startEditingExtra() {
        _uiState.update { it.copy(extraJson = it.saved.extraRequestJson) }
    }

    fun onExtraJsonChange(value: String) {
        _uiState.update { it.copy(extraJson = value) }
    }

    /**
     * 原样落盘，不在这里校验写法。
     *
     * 写法不规范等同于没填——这句判断只在发请求那一处做（见 AiVisionClient.applyExtraRequestParams），
     * 它拿到的若不是合法 JSON 就不并入请求体，于是跟随服务商默认。界面上的表述也是这么说的，
     * 两处判据合在一处，才不会出现「界面说跟随默认、保存却被拦下」这种自相矛盾。
     */
    fun saveExtra() {
        val updated = _uiState.value.saved.copy(extraRequestJson = _uiState.value.extraJson.trim())
        _uiState.update { it.copy(saved = updated) }
        viewModelScope.launch { repository.save(updated) }
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
            apiKey = current.apiKey.trim(),
            showReasoning = current.saved.showReasoning,
            extraRequestJson = current.saved.extraRequestJson
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
