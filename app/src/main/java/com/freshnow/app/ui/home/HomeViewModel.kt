package com.freshnow.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * 列表条目：查库拿到记录后，过期日期与照片文件都在这里一并算好，界面只管画。
 *
 * 「今天」刻意不放进状态——剩余天数由界面现取当天现算，否则应用跨过零点后列表会一直显示旧值。
 */
data class HomeRecordItem(
    val record: ScanRecord,
    val expiry: ExpiryOutcome,
    /** 记录的照片文件，没有图片或文件已不在时为 null */
    val image: File?
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    val records: StateFlow<List<HomeRecordItem>> = repository.records
        .map { records -> records.map(::toItem) }
        // 逐条查照片文件是否存在是盘上操作，挪到 IO 线程
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private fun toItem(record: ScanRecord) = HomeRecordItem(
        record = record,
        expiry = ExpiryCalculator.resolve(record.expiryDate, record.productionDate, record.shelfLife),
        image = repository.imageFile(record)
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
