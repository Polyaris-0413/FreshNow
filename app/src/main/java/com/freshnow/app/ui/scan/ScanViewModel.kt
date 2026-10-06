package com.freshnow.app.ui.scan

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.AiSettingsRepository
import com.freshnow.app.data.AiVisionClient
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.ScanResult
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.data.mergeObservation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    val status: ScanStatus = ScanStatus.Idle,
    val reasoning: String = "",
    val showReasoning: Boolean = false
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = AiSettingsRepository(application)
    private val recordRepository = ScanRecordRepository(application)
    private val client = AiVisionClient()

    // 表示"请求进行中"。请求一回来就放开，让下一帧立刻接上，不再有额外冷却
    private val busy = AtomicBoolean(false)

    // 保存时取此刻最新的一帧作为记录的照片：分析线程写入、主线程读取，故用 @Volatile
    @Volatile
    private var latestFrame: ByteArray? = null

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        // 订阅而非每帧读一次：在设置里开关思维链后，回到本页立即生效
        viewModelScope.launch {
            settingsRepository.aiSettings.collect { settings ->
                _uiState.update { it.copy(showReasoning = settings.showReasoning) }
            }
        }
    }

    /**
     * 由相机分析线程调用；返回 false 时直接丢弃该帧，省掉一次 JPEG 编码
     */
    fun canAcceptFrame(): Boolean = !busy.get()

    fun submitFrame(jpeg: ByteArray) {
        if (!busy.compareAndSet(false, true)) return
        latestFrame = jpeg
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
                    val analysis = client.analyze(settings, jpeg)
                    _uiState.update { state ->
                        // 累加记录：本帧没看到的字段保留已有值，推算也基于累加后的记录
                        val record = state.record.mergeObservation(analysis.result)
                        Log.d(TAG, "本帧读数=${analysis.result} 累加记录=$record 思维链${analysis.reasoning.length}字")
                        state.withRecord(record)
                            .copy(status = ScanStatus.Idle, reasoning = analysis.reasoning)
                    }
                } else {
                    // 未配置时不要先切到 Analyzing，否则状态会在两种文案之间反复跳动
                    _uiState.update { it.copy(status = ScanStatus.NotConfigured) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(status = ScanStatus.Failed(e.message ?: e::class.java.simpleName)) }
                // 失败时退避一下：平常不留冷却，但请求是失败的话相机每秒几十帧会不停重试，把请求打爆
                delay(RETRY_DELAY_MS)
            } finally {
                busy.set(false)
            }
        }
    }

    /**
     * 保存当前累加记录，成功后清空以便接着扫描下一件商品。无可保存内容时返回 false
     */
    fun save(): Boolean {
        val record = _uiState.value.record
        if (!record.hasAnyValue) return false

        val frame = latestFrame
        viewModelScope.launch { recordRepository.save(record, frame) }
        clearRecord()
        return true
    }

    /**
     * 清空累加记录，开始扫描下一件商品
     */
    fun clearRecord() {
        latestFrame = null
        _uiState.update { it.withRecord(ScanResult()).copy(reasoning = "") }
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

        // 仅用于请求失败后的退避，正常路径上不额外等待
        const val RETRY_DELAY_MS = 2_000L
    }
}
