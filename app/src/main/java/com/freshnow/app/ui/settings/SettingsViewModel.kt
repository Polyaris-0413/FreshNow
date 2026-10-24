package com.freshnow.app.ui.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.AiSettings
import com.freshnow.app.data.AiSettingsRepository
import com.freshnow.app.data.BehaviorSettings
import com.freshnow.app.data.BehaviorSettingsRepository
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.SortOrder
import com.freshnow.app.data.UpdateSource
import com.freshnow.app.data.backup.BackupRepository
import com.freshnow.app.ui.component.AiBasicConfigDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [saved] 为已落盘的配置，设置项行展示它；其余字段是编辑面板的草稿，仅打开面板时从 [saved] 重置
 * [behavior] 同理是行为那一组已落盘的值；两组开关都没有草稿，即时生效
 * [loaded] 表示 [saved] 是否已从 DataStore 读出，为 false 时界面不应渲染设置项
 */
data class SettingsUiState(
    val loaded: Boolean = false,
    val saved: AiSettings = AiSettings(),
    val behavior: BehaviorSettings = BehaviorSettings(),
    val basicConfig: AiBasicConfigDraft = AiBasicConfigDraft(),
    val extraJson: String = "",
    /** 正在导出或导入：两行备份条目在期间置灰，避免同一次读写被连点叠上 */
    val backupRunning: Boolean = false,
)

/**
 * 一次备份操作的结果。不提条数与照片数：用户要知道的只是「成了没有」，
 * 而条数还得先解释墓碑算不算、已删的算不算。
 */
sealed interface BackupMessage {
    data object Exported : BackupMessage
    data object ExportFailed : BackupMessage
    data object Imported : BackupMessage
    data object ImportFailed : BackupMessage
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AiSettingsRepository(application)
    private val behaviorRepository = BehaviorSettingsRepository(application)
    private val backupRepository = BackupRepository(ScanRecordRepository(application))

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = _backupMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.aiSettings.first()
            val behavior = behaviorRepository.behaviorSettings.first()
            _uiState.update { it.copy(loaded = true, saved = saved, behavior = behavior) }
        }
    }

    /**
     * 失败一律当「没导成」提示：写不进去的原因（空间不足、位置只读、用户刚把 U 盘拔了）
     * 用户看得到、也只能自己换个位置，给一串系统错误码不如直接请他重试。
     */
    fun exportBackup(uri: Uri) {
        if (_uiState.value.backupRunning) return
        _uiState.update { it.copy(backupRunning = true) }
        viewModelScope.launch {
            val result = runCatching {
                // "wt" 会截断已有内容，避免覆盖一个更长的旧文件时留下尾巴；
                // 少数文档提供方不认这个模式，退回普通写入
                val resolver = getApplication<Application>().contentResolver
                val output = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                    ?: resolver.openOutputStream(uri, "w")
                    ?: error("打不开写入位置")
                output.use { backupRepository.exportTo(it) }
            }
            _uiState.update { it.copy(backupRunning = false) }
            val error = result.exceptionOrNull()
            if (error != null) {
                // 失败一律吞成一句「导出失败」，但那句话对查问题没有任何用：打不开位置、
                // 写盘中断、文档提供方报错看起来一模一样。留一行堆栈给日志
                Log.w(BACKUP_LOG_TAG, "导出备份失败", error)
            }
            _backupMessage.value = if (result.isSuccess) {
                BackupMessage.Exported
            } else {
                BackupMessage.ExportFailed
            }
        }
    }

    /**
     * 把备份文件并进本机数据。读不懂的文件与合并成功是两件事，前者要用户换文件，
     * 后者不论并进几条都算成了。
     */
    fun importBackup(uri: Uri) {
        if (_uiState.value.backupRunning) return
        _uiState.update { it.copy(backupRunning = true) }
        viewModelScope.launch {
            val result = runCatching {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: error("打不开备份文件")
                input.use { backupRepository.restoreFrom(it) }
            }
            _uiState.update { it.copy(backupRunning = false) }
            val error = result.exceptionOrNull()
            if (error != null) {
                Log.w(BACKUP_LOG_TAG, "导入备份失败", error)
            }
            _backupMessage.value = if (result.isSuccess) {
                BackupMessage.Imported
            } else {
                BackupMessage.ImportFailed
            }
        }
    }

    /** 报完就销掉：留在状态里的话，切走再回来会再弹一次，而那次什么都没发生 */
    fun consumeBackupMessage() {
        _backupMessage.value = null
    }

    private companion object {
        const val BACKUP_LOG_TAG = "FreshNowBackup"
    }

    fun startEditing() {
        _uiState.update {
            it.copy(basicConfig = AiBasicConfigDraft.of(it.saved))
        }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(basicConfig = it.basicConfig.copy(baseUrl = value, baseUrlError = false)) }
    }

    fun onModelNameChange(value: String) {
        _uiState.update { it.copy(basicConfig = it.basicConfig.copy(modelName = value, modelNameError = false)) }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(basicConfig = it.basicConfig.copy(apiKey = value, apiKeyError = false)) }
    }

    /**
     * 开关是即时生效项，不经过编辑面板：先更新界面再落盘
     */
    fun onShowReasoningChange(value: Boolean) {
        val updated = _uiState.value.saved.copy(showReasoning = value)
        _uiState.update { it.copy(saved = updated) }
        viewModelScope.launch { repository.save(updated) }
    }

    /**
     * 「手动输入」与「显示思维链」同一条规矩：开关即时生效，先更新界面再落盘
     */
    fun onManualEntryChange(value: Boolean) {
        val updated = _uiState.value.behavior.copy(manualEntry = value)
        _uiState.update { it.copy(behavior = updated) }
        viewModelScope.launch { behaviorRepository.save(updated) }
    }


    /** 排序方式同样即时生效，且与主页顶栏那个菜单写的是同一份设置 */
    fun onSortOrderChange(order: SortOrder) {
        val updated = _uiState.value.behavior.copy(sortOrder = order)
        _uiState.update { it.copy(behavior = updated) }
        viewModelScope.launch { behaviorRepository.save(updated) }
    }

    fun onUpdateSourceChange(source: UpdateSource) {
        val updated = _uiState.value.behavior.copy(updateSource = source)
        _uiState.update { it.copy(behavior = updated) }
        viewModelScope.launch { behaviorRepository.save(updated) }
    }

    fun startEditingExtra() {
        _uiState.update { it.copy(extraJson = it.saved.extraRequestJson) }
    }

    fun onExtraJsonChange(value: String) {
        _uiState.update { it.copy(extraJson = value) }
    }

    /**
     * 原样落盘，不在这里校验写法。
     *
     * 写法不规范等同于没填——这句判断只在发请求那一处做（见 AiVisionClient.applyExtraRequestParams），
     * 它拿到的若不是合法 JSON 就不并入请求体，于是跟随服务商默认。界面上的表述也是这么说的，
     * 两处判据合在一处，才不会出现「界面说跟随默认、保存却被拦下」这种自相矛盾。
     */
    fun saveExtra() {
        val updated = _uiState.value.saved.copy(extraRequestJson = _uiState.value.extraJson.trim())
        _uiState.update { it.copy(saved = updated) }
        viewModelScope.launch { repository.save(updated) }
    }

    /**
     * 校验三项必填（规则与欢迎页同一份，见 [AiBasicConfigDraft]），未通过时在对应字段上标错
     * 并返回 false；通过则落盘并返回 true
     */
    fun save(): Boolean {
        val validated = _uiState.value.basicConfig.validated()
        _uiState.update { it.copy(basicConfig = validated) }
        if (!validated.isValid) return false

        val settings = validated.toSettings(_uiState.value.saved)
        viewModelScope.launch { repository.save(settings) }
        _uiState.update {
            it.copy(
                saved = settings,
                // 草稿对齐到落盘值：三项都取过 trim，留着原样的话下次开面板会看到未 trim 的旧值
                basicConfig = AiBasicConfigDraft.of(settings)
            )
        }
        return true
    }
}
