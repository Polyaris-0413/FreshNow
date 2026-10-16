package com.freshnow.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ImageChange
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「补充信息」页的草稿：只有用户自己知道的那几个字段（见 [ScanRecord.location]）。
 *
 * 与编辑记录页是两张分开的表单，分开的理由是**目的**不同：扫完一条之后要做的是「把位置补上」，
 * 而编辑记录页要改的是「模型认错了什么」——把位置摆进后者那一屏（照片、名称与三项日期之间），
 * 等于让人在一堆与当下目的无关的控件里找一个字段。这类字段往后还会多（数量、开封状态、备注），
 * 都加在这一页。
 *
 * 位置在两张表单里都能改（编辑页也带它，放在名称下面），所以两页在字段上是有重叠的。
 * 守住的是另一条：两页都先读整条记录、只写自己草稿里那几个字段——编辑页的草稿含位置，
 * 本页的草稿只有位置。这样在一边保存不会把另一边刚填的值静默清掉。
 */
data class RecordSupplementUiState(
    val loaded: Boolean = false,
    val found: Boolean = false,
    val location: String = "",
    val saving: Boolean = false
)

/**
 * 这一页没有「认不出的写法」这种状态，所以能不能存只看是不是正在写。
 *
 * 位置清空也放行：把一项填过的东西擦掉是一次编辑，与编辑记录页那边同一条规矩——
 * 「新记录至少要有一项」是新建时的门槛，不是改动时的。
 */
val RecordSupplementUiState.canSave: Boolean
    get() = !saving

class RecordSupplementViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    private val _uiState = MutableStateFlow(RecordSupplementUiState())
    val uiState: StateFlow<RecordSupplementUiState> = _uiState.asStateFlow()

    /** 读进来的那条记录，保存时在它上面改。只读一次、不订阅：正在编辑的值不该被库里的写入抢走 */
    private var loaded: ScanRecord? = null

    /** 已经读过哪条记录。与编辑页同样用 id 判重：屏幕旋转会重建本页，重读一次会把草稿盖回去 */
    private var loadedId: Long? = null

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch {
            val record = repository.find(id)
            loaded = record
            _uiState.value = RecordSupplementUiState(
                loaded = true,
                found = record != null,
                location = record?.location.orEmpty()
            )
        }
    }

    fun onLocationChange(value: String) {
        _uiState.update { it.copy(location = value) }
    }

    /**
     * 落盘之后再回调，界面在这之后才退出本页：直接退出会把 ViewModel 一起清掉，
     * 而这次写入挂在它的 viewModelScope 上，会被一并取消。
     *
     * **只改 location 这一项**：名称与三项日期归编辑记录页，本页不去动它们（两页都先读整条记录、
     * 只写自己草稿里那几个字段）。位置按自由文本落库（只去首尾空白），
     * 不套任何格式：怎么写由用户说了算。
     *
     * [ImageChange.Keep]：本页不碰照片。写入的是整行，不显式说明「照片照旧」的话，
     * 日后有人在这里改成别的语义就会把照片清掉。
     */
    fun save(onSaved: () -> Unit) {
        if (_uiState.value.saving) return
        val record = loaded ?: return
        _uiState.update { it.copy(saving = true) }
        val location = _uiState.value.location.trim()
        viewModelScope.launch {
            try {
                repository.update(record.copy(location = location), ImageChange.Keep)
            } catch (e: CancellationException) {
                // 本页被销毁时这次写入会随作用域一起取消，取消必须原样抛出：吞掉它等于把
                // 「协程被取消」记成「写入失败」，而 CancellationException 正是 Exception 的子类
                throw e
            } catch (e: Exception) {
                // 写库失败：留在本页并放开保存，让用户再试一次
                _uiState.update { it.copy(saving = false) }
                return@launch
            }
            onSaved()
        }
    }
}
