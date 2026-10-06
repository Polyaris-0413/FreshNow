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
import com.freshnow.app.data.mergeObservation
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
    val record: ScanResult = ScanResult(),
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
                    val observation = client.analyze(settings, jpeg)
                    _uiState.update { state ->
                        // 累加记录：本帧没看到的字段保留已有值，推算也基于累加后的记录
                        val record = state.record.mergeObservation(observation)
                        Log.d(TAG, "本帧读数=$observation 累加记录=$record")
                        state.withRecord(record).copy(status = ScanStatus.Idle)
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

    /**
     * 清空累加记录，开始扫描下一件商品
     */
    fun clearRecord() {
        _uiState.update { it.withRecord(ScanResult()) }
    }

    /** 记录与推算结果必须一起更新，避免两处状态不同步 */
    private fun ScanUiState.withRecord(record: ScanResult) = copy(
        record = record,
        expiry = ExpiryCalculator.resolve(
            printedExpiry = record.expiryDate,
            productionDate = record.productionDate,
            shelfLife = record.shelfLife
        )
    )

    private companion object {
        const val TAG = "ScanViewModel"
        const val FRAME_INTERVAL_MS = 2_000L
    }
}
