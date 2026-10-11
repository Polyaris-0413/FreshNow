package com.freshnow.app.ui.welcome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.AiSettingsRepository
import com.freshnow.app.data.OnboardingRepository
import com.freshnow.app.ui.component.AiBasicConfigDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 欢迎页的状态：配置三项的草稿、落盘结果、引导是否结束。
 *
 * 配置走的是与设置页同一份 [AiBasicConfigDraft]，校验规则不会在两个入口分叉；
 * 落盘的仓库也是同一个（[AiSettingsRepository]），因此欢迎页填完与设置页填完等价。
 */
class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = AiSettingsRepository(application)
    private val onboardingRepository = OnboardingRepository(application)

    private val _basicConfig = MutableStateFlow(AiBasicConfigDraft())
    val basicConfig: StateFlow<AiBasicConfigDraft> = _basicConfig.asStateFlow()

    /**
     * 配置成功落盘的次数。界面等它大于 0 才推进到下一步，否则推进过去的那一时刻配置还没写进 DataStore。
     *
     * 用计数而不是布尔：用户可能从权限那一步退回配置改一次再保存，布尔只在第一次从 false 变 true 时
     * 触发推进，第二次保存就不动了。
     */
    private val _configSavedCount = MutableStateFlow(0)
    val configSavedCount: StateFlow<Int> = _configSavedCount.asStateFlow()

    /** 引导结束（「已看过」也已落盘），界面据此切到主页 */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    fun onBaseUrlChange(value: String) {
        _basicConfig.update { it.copy(baseUrl = value, baseUrlError = false) }
    }

    fun onModelNameChange(value: String) {
        _basicConfig.update { it.copy(modelName = value, modelNameError = false) }
    }

    fun onApiKeyChange(value: String) {
        _basicConfig.update { it.copy(apiKey = value, apiKeyError = false) }
    }

    /**
     * 校验三项必填；没通过就只在界面上标错（用户随时可以跳过整个引导），
     * 通过则落盘并让 [configSavedCount] 加一。
     *
     * 读一次已落盘的值再合并：思考链与思考参数不在这份表单里，不能因为这里保存就丢掉
     * （用户可能先前在设置页配过——例如上一轮引导被跳过之后又回来填）。
     */
    fun saveBasicConfig() {
        val validated = _basicConfig.value.validated()
        _basicConfig.value = validated
        if (!validated.isValid) return

        viewModelScope.launch {
            val saved = aiRepository.aiSettings.first()
            aiRepository.save(validated.toSettings(saved))
            _configSavedCount.update { it + 1 }
        }
    }

    /**
     * 结束引导：先把「已看过」落盘，再让界面切走。
     *
     * 顺序不能反：界面切走后本 ViewModel 就没了，viewModelScope 随之取消，落盘会被半路掐掉，
     * 下次启动又走一遍引导。
     */
    fun finish() {
        viewModelScope.launch {
            onboardingRepository.markWelcomeSeen()
            _finished.value = true
        }
    }
}
