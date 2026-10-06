package com.freshnow.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordDetailUiState(
    val record: ScanRecord? = null,
    val expiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput,
    /** 是否已完成一次读取，用于区分"加载中"与"记录不存在" */
    val loaded: Boolean = false
)

class RecordDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            val record = repository.find(id)
            _uiState.value = RecordDetailUiState(
                record = record,
                expiry = record?.let {
                    ExpiryCalculator.resolve(
                        printedExpiry = it.expiryDate,
                        productionDate = it.productionDate,
                        shelfLife = it.shelfLife
                    )
                } ?: ExpiryOutcome.InsufficientInput,
                loaded = true
            )
        }
    }
}
