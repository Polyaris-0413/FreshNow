package com.freshnow.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.ScanValueFormat
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 编辑页的草稿。[loaded] 表示已经读过库（读完才知道记录在不在），[found] 为 false 即记录不存在；
 * [derivedExpiry] 是过期日期留空时按草稿里的生产日期与保质期现算的结果，随每次改动重算。
 */
data class RecordEditUiState(
    val loaded: Boolean = false,
    val found: Boolean = false,
    val productName: String = "",
    val productionDate: String = "",
    val expiryDate: String = "",
    val shelfLife: String = "",
    val derivedExpiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput
)

/**
 * 改一条已保存记录的文字字段。
 *
 * 四项放在同一份草稿里一起改、一起存：过期日期可能是标签印刷的值，也可能是由生产日期与保质期
 * 推算出来的派生值，这条联动必须在同一屏里看得见——逐个字段就地改会把它藏起来（见 ExpiryCalculator）。
 */
class RecordEditViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    private val _uiState = MutableStateFlow(RecordEditUiState())
    val uiState: StateFlow<RecordEditUiState> = _uiState.asStateFlow()

    /** 读进来的那条记录，保存时在它上面改字段：照片文件名、保存时间都靠它原样带过去 */
    private var loaded: ScanRecord? = null

    /** 读入草稿。只读一次、不订阅：正在编辑的值不该被库里的写入抢走 */
    fun load(id: Long) {
        viewModelScope.launch {
            val record = repository.find(id)
            loaded = record
            _uiState.value = if (record == null) {
                RecordEditUiState(loaded = true, found = false)
            } else {
                RecordEditUiState(
                    loaded = true,
                    found = true,
                    productName = record.productName,
                    productionDate = record.productionDate,
                    expiryDate = record.expiryDate,
                    shelfLife = record.shelfLife
                ).recomputeDerived()
            }
        }
    }

    fun onProductNameChange(value: String) {
        _uiState.update { it.copy(productName = value) }
    }

    fun onProductionDateChange(value: String) {
        _uiState.update { it.copy(productionDate = value).recomputeDerived() }
    }

    fun onExpiryDateChange(value: String) {
        _uiState.update { it.copy(expiryDate = value) }
    }

    fun onShelfLifeChange(value: String) {
        _uiState.update { it.copy(shelfLife = value).recomputeDerived() }
    }

    /**
     * 落盘之后再回调，界面在这之后才退出本页：直接退出会把 ViewModel 一起清掉，
     * 而这次写入挂在它的 viewModelScope 上，会被一并取消。
     *
     * 写法沿用模型入库那一套规整（[ScanValueFormat]）：值不管是模型读的还是手输的，落库只有一种写法；
     * 认不出来的原样存下——与详情页对印刷值的处理一致，不替用户猜。
     */
    fun save(onSaved: () -> Unit) {
        val current = _uiState.value
        val record = loaded
        viewModelScope.launch {
            try {
                if (record != null) {
                    repository.update(
                        record.copy(
                            productName = current.productName.trim(),
                            productionDate = ScanValueFormat.date(current.productionDate.trim()),
                            expiryDate = ScanValueFormat.date(current.expiryDate.trim()),
                            shelfLife = ScanValueFormat.shelfLife(current.shelfLife.trim())
                        )
                    )
                }
            } finally {
                onSaved()
            }
        }
    }

    private fun RecordEditUiState.recomputeDerived() = copy(
        derivedExpiry = ExpiryCalculator.resolve(
            // 留空才轮得到推算；填了值就是印刷值，以它为准
            printedExpiry = "",
            productionDate = productionDate,
            shelfLife = shelfLife
        )
    )
}
