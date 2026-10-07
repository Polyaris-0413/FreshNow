package com.freshnow.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    /**
     * 列表内容。null 表示第一次查库还没回来。
     *
     * 初始值不用 emptyList()：「数据还没到」与「确实一条都没有」在界面上是两回事，混成同一个值
     * 的话，冷启动会先显示一句「还没有扫描记录」，等记录到达再把它换成列表——同一屏里前后两个
     * 互相矛盾的结论，而且记录是硬切上来的（见 HomeScreen 对 records == null 的处理）。
     * 这里宁可把「未知」显式表达出来，也不假装已经查完了。
     */
    val records: StateFlow<List<HomeRecordItem>?> = repository.records
        .map { records -> records.map(::toItem) }
        // 逐条查照片文件是否存在是盘上操作，挪到 IO 线程
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun toItem(record: ScanRecord) = HomeRecordItem(
        record = record,
        expiry = ExpiryCalculator.resolve(record.expiryDate, record.productionDate, record.shelfLife),
        image = repository.imageFile(record)
    )

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * 选择模式里被选中的记录。
     *
     * 选择模式不另设开关，就由这个集合是否为空表示：两者分开存会出现「处于选择模式但一个都没选中」
     * 这种界面无法表达的状态（顶栏要显示「已选 0 项」吗？删除按钮可不可点？）。
     */
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** 长按进入选择模式并选中该条；已选中则取消。取消掉最后一条即自动离开选择模式 */
    fun toggleSelection(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * 删除选中的记录。
     *
     * 先退出选择模式再落库：界面应当立刻回到普通列表，而不是等数据库写完、列表已经空了
     * 顶上还挂着「已选 N 项」；记录本身会经 records 这条流回到界面，不需要在这里等。
     */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch { repository.delete(ids) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
