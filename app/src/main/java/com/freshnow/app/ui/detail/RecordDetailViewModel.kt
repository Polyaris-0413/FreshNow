package com.freshnow.app.ui.detail

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.decodeScanImage
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordDetailUiState(
    val record: ScanRecord? = null,
    val expiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput,
    /** 记录里的照片，没有图片或文件读不出来时为 null，界面用占位图代替 */
    val image: Bitmap? = null,
    /** 是否已完成一次读取，用于区分"加载中"与"记录不存在" */
    val loaded: Boolean = false
)

class RecordDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            // 订阅而不是查一次：编辑页保存后回到本页，值要跟着变
            var decodedName: String? = null
            var decodedImage: Bitmap? = null
            repository.observe(id).collect { record ->
                if (record?.imageName != decodedName) {
                    decodedName = record?.imageName
                    decodedImage = record?.let {
                        withContext(Dispatchers.IO) { decodeScanImage(repository.imageFile(it)) }
                    }
                }
                _uiState.value = RecordDetailUiState(
                    record = record,
                    expiry = record?.let {
                        ExpiryCalculator.resolve(
                            printedExpiry = it.expiryDate,
                            productionDate = it.productionDate,
                            shelfLife = it.shelfLife
                        )
                    } ?: ExpiryOutcome.InsufficientInput,
                    image = decodedImage,
                    loaded = true
                )
            }
        }
    }
}
