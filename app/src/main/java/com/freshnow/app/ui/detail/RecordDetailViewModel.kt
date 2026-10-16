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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordDetailUiState(
    val record: ScanRecord? = null,
    val expiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput,
    /** 记录里的照片，顺序与 [ScanRecord.imageNames] 一致；文件读不出来的那些不在里面 */
    val images: List<Bitmap> = emptyList(),
    /**
     * 正在看的那一张（下标）。点下面那排缩略图改它，越界时按 0 处理。
     *
     * 放在这里而不是界面里：主图是这一页的主角，旋转、重建之后应当还在那一张上
     */
    val mainIndex: Int = 0,
    /** 是否已完成一次读取，用于区分"加载中"与"记录不存在" */
    val loaded: Boolean = false
)

/** 主图。一张都没有时为 null，界面用占位图顶上 */
val RecordDetailUiState.mainImage: Bitmap?
    get() = images.getOrNull(mainIndex)

/** 详情页那一排缩略图要不要画：只有一张时主图就是它，再画一排等大的缩略图只是把同一张图说两遍 */
val RecordDetailUiState.showsThumbnails: Boolean
    get() = images.size > 1

class RecordDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)
    private val coordinator = SyncCoordinator.getInstance(application)

    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            // 设置读一次就够了：本页没有去设置的路径（设置是主页那一行的下级），
            // 站着这一页的期间它不会变
            // 订阅而不是查一次：编辑页保存后回到本页，值要跟着变
            var decodedNames: List<String> = emptyList()
            var decodedImages: List<Bitmap> = emptyList()
            // 这条记录只试一次补图。对端不在线时补不回来，而每次数据变动又试一遍的话，
            // 网络不通的那段时间里本页会反复发起超时请求，用户完全不知道它在忙什么
            var fetchAttempted = false
            repository.observe(id).collect { record ->
                if (record?.imageNames != decodedNames) {
                    decodedNames = record?.imageNames.orEmpty()
                    decodedImages = withContext(Dispatchers.IO) {
                        // 读不出来的那张不进列表：它与主图是同一块位置的两种画法（照片 / 占位图），
                        // 而占位那一格答不出「点开看哪一张」
                        decodedNames.mapNotNull { decodeScanImage(repository.imageFile(it)) }
                    }
                    // 记录说有几张、本地却少读了：多半是同步过来的那一条。补图之后行里的名字会变，
                    // 订阅会把新的名字再送一趟，这一段就会重新解码
                    val pending = record
                    if (decodedImages.size < decodedNames.size && !fetchAttempted && pending != null) {
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
                    images = decodedImages,
                    mainIndex = _uiState.value.mainIndex.coerceIn(0, (decodedImages.size - 1).coerceAtLeast(0)),
                    loaded = true
                )
            }
        }
    }

    /** 点下面那排缩略图：换主图。只认真实存在的下标 */
    fun showImage(index: Int) {
        _uiState.update { if (index in it.images.indices) it.copy(mainIndex = index) else it }
    }
}
