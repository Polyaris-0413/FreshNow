package com.freshnow.app.ui.scan

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.AiSettingsRepository
import com.freshnow.app.data.AiVisionClient
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Analyzing : ScanStatus
    data object NotConfigured : ScanStatus
    data class Failed(val detail: String) : ScanStatus
}

data class ScanUiState(
    val result: ScanResult = ScanResult(),
    val expiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput,
    val status: ScanStatus = ScanStatus.Idle
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = AiSettingsRepository(application)
    private val client = AiVisionClient()

    // 同时表示"请求进行中"与"冷却期"，避免连续送帧
    private val busy = AtomicBoolean(false)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /**
     * 由相机分析线程调用；返回 false 时直接丢弃该帧，省掉一次 JPEG 编码
     */
    fun canAcceptFrame(): Boolean = !busy.get()

    fun submitFrame(jpeg: ByteArray) {
        if (!busy.compareAndSet(false, true)) return
        Log.d(TAG, "提交一帧，${jpeg.size} 字节")

        viewModelScope.launch {
            try {
                val settings = settingsRepository.aiSettings.first()
                val configured = settings.baseUrl.isNotBlank() &&
                    settings.modelName.isNotBlank() &&
                    settings.apiKey.isNotBlank()
                if (configured) {
                    // 失败后保持错误文案常驻，避免每轮重试都闪一下「识别中…」
                    _uiState.update { state ->
                        if (state.status is ScanStatus.Failed) state else state.copy(status = ScanStatus.Analyzing)
                    }
                    val result = client.analyze(settings, jpeg)
                    _uiState.update {
                        it.copy(
                            result = result,
                            expiry = ExpiryCalculator.resolve(
                                printedExpiry = result.expiryDate,
                                productionDate = result.productionDate,
                                shelfLife = result.shelfLife
                            ),
                            status = ScanStatus.Idle
                        )
                    }
                } else {
                    // 未配置时不要先切到 Analyzing，否则状态会在两种文案之间反复跳动
                    _uiState.update { it.copy(status = ScanStatus.NotConfigured) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(status = ScanStatus.Failed(e.message ?: e::class.java.simpleName)) }
            } finally {
                delay(FRAME_INTERVAL_MS)
                busy.set(false)
            }
        }
    }

    private companion object {
        const val TAG = "ScanViewModel"
        const val FRAME_INTERVAL_MS = 2_000L
    }
}
