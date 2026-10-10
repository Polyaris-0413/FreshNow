package com.freshnow.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.BehaviorSettings
import com.freshnow.app.data.BehaviorSettingsRepository
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.ScanValueFormat
import com.freshnow.app.data.SortOrder
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.sync.SyncCoordinator
import com.freshnow.app.ui.sync.SyncMessage
import com.freshnow.app.ui.sync.toMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

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

/**
 * 按 [order] 重排列表条目。
 *
 * 创建时间：新存的在前（与查库的 `ORDER BY savedAt DESC` 同一方向，这里再排一次是为了两种排序
 * 共用一条出口）。
 *
 * 过期日期：快到期的在前（升序），**已过期的因此排在最上面**——那正是「该先处理」的语义。
 * 过期日期算不出来的（印刷值认不出、或推不出）一律排到最后：它们不参与比较，谁先谁后由稳定
 * 排序保留（也就是维持创建时间的顺序）。
 */
internal fun List<HomeRecordItem>.sortedFor(order: SortOrder): List<HomeRecordItem> = when (order) {
    SortOrder.CREATED_AT -> sortedByDescending { it.record.savedAt }
    SortOrder.EXPIRY_DATE -> sortedWith(compareBy(nullsLast()) { it.expiryDate() })
}

/**
 * 这一条算得出的过期日期。判据与 [ExpiryCalculator.daysRemaining] 一致：印刷值认不出、
 * 或算不出来的都给 null，不能拿着原文字符串去比大小（`2026年12月` 这种排出来的次序是假的）。
 */
private fun HomeRecordItem.expiryDate(): LocalDate? =
    (expiry as? ExpiryOutcome.Resolved)?.let { ScanValueFormat.parseDate(it.date.trim()) }

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    private val behaviorRepository = BehaviorSettingsRepository(application)

    private val sync = SyncCoordinator.getInstance(application)

    /**
     * 下拉刷新进行中，主页的指示器读它。
     *
     * 刻意不用协调器的 syncing：那个包含进前台自动同步、五分钟兜底轮询、改动推送——它们都没
     * 有手势，而 M3 的下拉指示器是手势的可见反馈（它会滑入到阈值处再转圈，看着就像有人替你下拉
     * 了一下）；对端不在线时那一轮还要等连不上＋发现宽限，八九秒后才收回去，于是变成「一进前台
     * 就有个圈转着不走」。这里只跟本页自己发起的那一轮。
     */
    private val _refreshing = MutableStateFlow(false)

    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 排着几轮下拉（连点会各排一轮），全跑完指示器才收 */
    private var refreshesInFlight = 0

    private val _syncMessage = MutableStateFlow<SyncMessage?>(null)

    /** 上一次手动同步的结果，界面报一句话之后由 [consumeSyncMessage] 清掉 */
    val syncMessage: StateFlow<SyncMessage?> = _syncMessage

    fun consumeSyncMessage() {
        _syncMessage.value = null
    }

    /**
     * 手动同步一轮，下拉刷新走这里。
     *
     * 必须报一句：用户做的就是「我要现在同步」，不给回音的话，他没有办法区分「同步完了没变化」
     * 与「这个手势根本没生效」。
     *
     * 若此刻定时那一轮正在跑，这一下会排在它后面（见 SyncCoordinator.syncNow），所以报出来的是
     * 自己这一轮的结果，而不是途搭上别人那轮。
     */
    fun syncNow() {
        refreshesInFlight++
        _refreshing.value = true
        viewModelScope.launch {
            try {
                _syncMessage.value = sync.syncNow().toMessage()
            } finally {
                if (--refreshesInFlight == 0) _refreshing.value = false
            }
        }
    }

    /**
     * 行为设置。主页只用到其中两项：列表按哪个方式排，「添加」去哪一页。
     *
     * 订阅而不是读一次：用户可能刚在设置页改过再回来，读一次的话这一次点击还是走老路。
     * 初值取一份默认（扫描＋按创建时间），还没读出来时按主线走。
     */
    val behavior: StateFlow<BehaviorSettings> = behaviorRepository.behaviorSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BehaviorSettings())

    /** 改排序方式。先读库里现值再改：界面上那份可能还没收到第一帧，拿它改会把其它项一并写回默认值 */
    fun onSortOrderChange(order: SortOrder) {
        viewModelScope.launch {
            val current = behaviorRepository.behaviorSettings.first()
            behaviorRepository.save(current.copy(sortOrder = order))
        }
    }

    /**
     * 列表内容。null 表示第一次查库还没回来。
     *
     * 初始值不用 emptyList()：「数据还没到」与「确实一条都没有」在界面上是两回事，混成同一个值
     * 的话，冷启动会先显示一句「还没有扫描记录」，等记录到达再把它换成列表——同一屏里前后两个
     * 互相矛盾的结论，而且记录是硬切上来的（见 HomeScreen 对 records == null 的处理）。
     * 这里宁可把「未知」显式表达出来，也不假装已经查完了。
     */
    val records: StateFlow<List<HomeRecordItem>?> = repository.records
        .map { records ->
            val ids = records.map { it.id }.toSet()
            // 与上一次查库相比多出来的就是「刚存进去的」，「原来是有的、现在没了」就是刚被删空。
            // 这两步都在数据到达的那一刻定下来，界面是这一刻之后才读的，所以不论它读得早还是晚、
            // 列表是整棵重新合成还是条目后加，结论都一样——判据原来落在合成时机上，于是「逐条淡入」
            // 和空态的淡出淡入都变成了「取决于这一刻是不是刚好在合成」
            _newRecordIds.value = ids - knownIds
            _justEmptied.value = ids.isEmpty() && knownIds.isNotEmpty()
            knownIds = ids
            records.map(::toItem)
        }
        // 逐条查照片文件是否存在是盘上操作，挪到 IO 线程
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * 列表内容按当前排序方式排好，界面只读这一条。
     *
     * 排序在内存里做，不重查库：过期日期可能是印刷值，也可能是现算出来的（见 ExpiryCalculator），
     * SQL 排不了。初值同样是 null，与 [records] 一样表示「还没查完」。
     */
    val sortedRecords: StateFlow<List<HomeRecordItem>?> =
        combine(records, behavior) { list, settings -> list?.sortedFor(settings.sortOrder) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** 上一次查库见到的 id，用来挑出刚存进来的那几条、以及判断是不是刚被删空 */
    private var knownIds: Set<Long> = emptySet()

    private val _newRecordIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 见 [onRecordsShown]。与 [records] 分开一条流：合成的时机不受它影响，只影响条目要不要演淡入 */
    val newRecordIds: StateFlow<Set<Long>> = _newRecordIds.asStateFlow()

    private val _justEmptied = MutableStateFlow(false)

    /**
     * 上一次查库把列表查空了，且此前是有记录的。见 [onRecordsShown]。
     *
     * 空态只有在这种时候才该淡入：删光时用户正看着列表，那是他眼前发生的变化。从别的页面
     * 回到主页不是变化——空态早就显示过，那次它跟着整页一起出现就够了，再淡一遍是重复。
     */
    val justEmptied: StateFlow<Boolean> = _justEmptied.asStateFlow()

    /**
     * 界面已经把这一批变化演过了，清掉。
     *
     * 从主页离开时调用：下次回来（转场返回、转屏）重新合成的是同一批内容，不必再演一遍。
     * 变化是在离开期间发生的（保存后立即返回扫描页那一侧），清空发生在它之前，因此不受影响。
     */
    fun onRecordsShown() {
        _newRecordIds.value = emptySet()
        _justEmptied.value = false
    }

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
