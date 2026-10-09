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
import com.freshnow.app.data.sync.SyncCoordinator
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
    private val coordinator = SyncCoordinator.getInstance(application)

    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            // 订阅而不是查一次：编辑页保存后回到本页，值要跟着变
            var decodedName: String? = null
            var decodedImage: Bitmap? = null
            // 这条记录只试一次补图。对端不在线时补不回来，而每次数据变动又试一遍的话，
            // 网络不通的那段时间里本页会反复发起超时请求，用户完全不知道它在忙什么
            var fetchAttempted = false
            repository.observe(id).collect { record ->
                if (record?.imageName != decodedName) {
                    decodedName = record?.imageName
                    decodedImage = record?.let {
                        withContext(Dispatchers.IO) { decodeScanImage(repository.imageFile(it)) }
                    }
                    // 记录说有图、本地却没有：多半是同步过来的那一条。拉回来之后行里的图片名会变，
                    // 订阅会把新的名字再送一趟，这一段就会重新解码
                    val pending = record
                    if (decodedImage == null && !fetchAttempted && pending != null &&
                        needsPhoto(pending)
                    ) {
                        fetchAttempted = true
                        // 另起一个协程：取图要等网络，压在 collect 里会让后续的每一条数据变动一起排队
                        viewModelScope.launch { coordinator.fetchPhoto(pending) }
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

    /** 记录声称自己有照片，而本地文件不在 */
    private fun needsPhoto(record: ScanRecord): Boolean =
        record.imageName.isNotEmpty() && repository.imageFile(record) == null
}
