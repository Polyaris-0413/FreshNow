package com.freshnow.app.ui.scan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.AiSettingsRepository
import com.freshnow.app.data.AiVisionClient
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.RecordField
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.ScanResult
import com.freshnow.app.data.decodePickedImage
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.data.mergeObservation
import com.freshnow.app.data.toSquareJpegBytes
import com.freshnow.app.ui.component.EditPhoto
import com.freshnow.app.ui.detail.CropWindow
import com.freshnow.app.ui.detail.croppedTo
import com.freshnow.app.ui.theme.FreshNowTransitions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 服务用不了时的说明对话框长什么样，两种处境能做的事不一样
 */
sealed interface ServiceProblem {
    /** 还没配过：没有服务端日志可复制，能给的只有去配置的入口 */
    data object NotConfigured : ServiceProblem

    /** 请求失败：[log] 即服务端返回的原话，可以复制走交给别的 AI */
    data class Failed(val log: String) : ServiceProblem
}

data class ScanUiState(
    val record: ScanResult = ScanResult(),
    val expiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput,
    val reasoning: String = "",
    val showReasoning: Boolean = false,
    /** 这一页现在在哪一态（见 [ScanStage]）*/
    val stage: ScanStage = ScanStage.SCANNING,
    /** 位置：只服务用户自己知道的那一项（见 [com.freshnow.app.data.local.ScanRecord.location]）*/
    val location: String = "",
    /** 编辑态的照片草稿；第一张是按下「下一步」那一刻的画面（见设计文档 D5）*/
    val photos: List<EditPhoto> = emptyList(),
    /**
     * 用户自己写过的那几项（见 [mergeObservation]）。那一项从此只归他，后续的帧不再覆盖——
     * 自己写的字被机器悄悄冲掉是最让人不信任的事。
     */
    val lockedFields: Set<RecordField> = emptySet(),
    /** 相册选来、等着裁剪的原图；非 null 即裁剪页正在显示 */
    val cropSource: Bitmap? = null,
    /** 取景框里那枚状态胶囊该说什么，见 [ScanStatus] */
    val status: ScanStatus = ScanStatus.Hidden,
    /** 服务用不了时该弹出的说明对话框；null 表示不弹 */
    val serviceProblem: ServiceProblem? = null,
    /** 这一次已经提交落库、正等着离开这一页（见 [ScanViewModel.save]）*/
    val saving: Boolean = false
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = AiSettingsRepository(application)
    private val recordRepository = ScanRecordRepository(application)
    private val client = AiVisionClient()

    // 表示"请求进行中"。请求一回来就放开，让下一帧立刻接上，不再有额外冷却
    private val busy = AtomicBoolean(false)

    // 保存时取此刻最新的一帧作为记录的照片：分析线程写入、主线程读取，故用 @Volatile
    @Volatile
    private var latestFrame: ByteArray? = null

    // 服务这一帧的处理流程里读写，而那段代码跑在 viewModelScope（主线程）上，故不需要同步

    /** 连续处于问题状态（未配置或请求失败）的帧数 */
    private var problemFrames = 0

    /** 状态胶囊的最短停留，见 [ScanStatusDwell] */
    private val statusDwell = ScanStatusDwell(FreshNowTransitions.STATUS_DWELL_MS)

    /** 那句「正在识别…」的计时器；请求回来就把这榟事作废 */
    private var statusJob: Job? = null

    /** 编辑态草稿照片的 id 发到哪儿了。只在草稿内部流转 */
    private var nextPhotoId = 0L

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        // 订阅而非每帧读一次：在设置里开关思维链后，回到本页立即生效
        viewModelScope.launch {
            settingsRepository.aiSettings.collect { settings ->
                _uiState.update { it.copy(showReasoning = settings.showReasoning) }
            }
        }
    }

    /**
     * 由相机分析线程调用；返回 false 时直接丢弃该帧，省掉一次 JPEG 编码
     */
    fun canAcceptFrame(): Boolean = !busy.get()

    fun submitFrame(jpeg: ByteArray) {
        // 编辑态不往模型送帧：用户正在改的那份草稿不该被新一帧盖掉，网络也就不必白跑
        if (_uiState.value.stage == ScanStage.EDITING) return
        if (!busy.compareAndSet(false, true)) return
        latestFrame = jpeg
        Log.d(TAG, "提交一帧，${jpeg.size} 字节")

        viewModelScope.launch {
            startStatusWatch()
            // 这一帧模型看到的（不是累加后的记录）：胶囊报的就是它，见 [finishStatus]
            var observation: ScanResult? = null
            try {
                val settings = settingsRepository.aiSettings.first()
                val configured = settings.baseUrl.isNotBlank() &&
                    settings.modelName.isNotBlank() &&
                    settings.apiKey.isNotBlank()
                if (configured) {
                    val analysis = client.analyze(settings, jpeg)
                    observation = analysis.result
                    _uiState.update { state ->
                        // 累加记录：本帧没看到的字段保留已有值，而用户手写过的项不参与合并（见 lockedFields）
                        val record = mergeObservation(state.record, analysis.result, state.lockedFields)
                        Log.d(TAG, "本帧读数=${analysis.result} 累加记录=$record 思维链${analysis.reasoning.length}字")
                        state.withRecord(record).copy(reasoning = analysis.reasoning)
                    }
                    onServiceUsable()
                } else {
                    onServiceProblem(ServiceProblem.NotConfigured)
                }
            } catch (e: Exception) {
                onServiceProblem(ServiceProblem.Failed(e.message ?: e::class.java.simpleName))
                // 失败时退避一下：平常不留冷却，但请求是失败的话相机每秒几十帧会不停重试，把请求打爆
                delay(RETRY_DELAY_MS)
            } finally {
                busy.set(false)
                finishStatus(observation)
            }
        }
    }

    /**
     * 请求发出：先不开口，等够 [FreshNowTransitions.RECOGNIZING_THRESHOLD_MS] 还没回来才说「正在识别」。
     * 平常一趟就几百毫秒，低于这个门槛就结果已经回来了，摆了也是一闪而过。
     */
    private fun startStatusWatch() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            delay(FreshNowTransitions.RECOGNIZING_THRESHOLD_MS)
            publishStatus(ScanStatus.Recognizing)
        }
    }

    /**
     * 请求回来（成败都是）：那句「正在识别」作废，改报**这一帧**看到的项数。
     *
     * 报单帧而不是累加值：累加值很快停在上限（四项都读到）就永不再动，而用户要知道的是「眼下这一下
     * 有没有收获」——单帧的项数会跟着他对得准不准升降，一直在说话。代价是它有时小于屏幕上已填好的
     * 字段数（那是累加的结果），这是两者口径不同，不是丢了数据。
     *
     * [observation] 为 null（这一帧失败、或服务还没配置）时保持上一句不说话：那一帧本就没有观察可言，
     * 而这时屏幕上会弹服务说明对话框，轮不到胶囊发言。
     */
    private fun finishStatus(observation: ScanResult?) {
        statusJob?.cancel()
        if (observation == null) return
        publishStatus(ScanStatus.of(observation))
    }

    /** 报给停留器，由它定下画面要不要改 */
    private fun publishStatus(wanted: ScanStatus) {
        statusDwell.offer(SystemClock.elapsedRealtime(), wanted)?.let { shown ->
            _uiState.update { it.copy(status = shown) }
        }
    }

    /**
     * 落库之后回调新记录的 id，界面凭它决定接着去哪（两条入口都把这一页带走）。
     *
     * 用回调而不是返回 id：那是一次挂在本 ViewModel 作用域上的异步写入，方法当场返回时还没有 id。
     * 一个字段都没读到就不写、也不回调——与「清空 / 保存」置灰用的是同一条判据。
     *
     * 这里不清场：清场里带着「翻回扫描态」，而整屏步骤跟着那一项走，于是保存完先把扫描页演一遍
     * （相机重新展开、整屏转场走完）才回主页。草稿留到离场为止，防连点交给 [ScanUiState.saving]。
     */
    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        if (state.saving || !state.record.hasAnyValue) return

        val frames = state.photos.mapNotNull { it.bytes }
        _uiState.update { it.startSaving() }
        viewModelScope.launch {
            val id = try {
                recordRepository.save(state.storedResult(), state.location, frames)
            } catch (e: CancellationException) {
                // 本页被销毁时这次写入会随作用域一起取消；取消是 Exception 的子类，不原样抛出
                // 就会被下面那一支记成「写入失败」（与编辑页同一条规矩）
                throw e
            } catch (e: Exception) {
                // 写库本身就失败了：留在本页并放开保存，让用户再试一次
                _uiState.update { it.copy(saving = false) }
                return@launch
            }
            onSaved(id)
        }
    }

    /**
     * 翻面：把此刻的画面接在草稿末尾（见设计文档 D5）。
     *
     * 每一次翻面都接一张，而不是只在第一次：折回去对准要补的那一面、再翻面，就是「补拍」——
     * 不必为这一个动作再引一个拍照页。同一张图重复按只会在草稿里出现两格，落库时按内容名去重。
     */
    fun nextStep() {
        val frame = latestFrame
        _uiState.update { it.startEditing() }
        if (frame == null) return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(frame, 0, frame.size)
            }
            _uiState.update { state ->
                state.copy(photos = state.photos + EditPhoto(id = nextPhotoId++, bytes = frame, bitmap = bitmap))
            }
        }
    }

    /** 折回扫描态：相机重新展开，草稿里的字与照片都留着（见设计文档 D3）*/
    fun backToScanning() {
        _uiState.update { it.backToScanning() }
    }

    /** 编辑态里改一项。表单报回来的是「哪一项 + 新值」*/
    fun onFieldChange(field: RecordField, value: String) {
        _uiState.update { it.onFieldChange(field, value) }
    }

    /** 移除草稿里的某一张。只动草稿：落盘时它自然不在名字列表里 */
    fun onPhotoRemoved(index: Int) {
        _uiState.update { state ->
            if (index !in state.photos.indices) {
                state
            } else {
                state.copy(photos = state.photos.filterIndexed { i, _ -> i != index })
            }
        }
    }

    /**
     * 相册选来的图：解码后停在草稿里等着裁剪（与编辑页同一套，见 [ImageCropScreen]）。
     * 读不出来时什么都不动——一次读图失败不该把已经加好的照片抹掉。
     */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                decodePickedImage(getApplication<Application>().contentResolver, uri)
            }
            _uiState.update { if (decoded == null) it else it.copy(cropSource = decoded) }
        }
    }

    fun cancelCrop() {
        _uiState.update { it.copy(cropSource = null) }
    }

    /** 裁剪页确认：按摆好的框裁方、编码，接在草稿末尾等保存 */
    internal fun applyCrop(window: CropWindow) {
        val source = _uiState.value.cropSource ?: return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) { source.croppedTo(window).toSquareJpegBytes() }
            val preview = bytes?.let { data ->
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(data, 0, data.size) }
            }
            _uiState.update { state ->
                if (bytes == null || preview == null) {
                    state.copy(cropSource = null)
                } else {
                    state.copy(
                        cropSource = null,
                        photos = state.photos + EditPhoto(id = nextPhotoId++, bytes = bytes, bitmap = preview)
                    )
                }
            }
        }
    }

    /**
     * 拍到的照片。相机给的字节已经是正方形 JPEG（与扫描抓帧同一套规格，见 ScanImageCodec），
     * 所以不必过裁剪页。
     */
    fun onPhotoCaptured(jpeg: ByteArray) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            }
            _uiState.update { state ->
                state.copy(photos = state.photos + EditPhoto(id = nextPhotoId++, bytes = jpeg, bitmap = preview))
            }
        }
    }

    /**
     * 服务这一帧是通的：问题计数归零，并收起对话框——已经通得上了，再挂着报错就成了错误信息
     */
    private fun onServiceUsable() {
        problemFrames = 0
        _uiState.update { it.copy(serviceProblem = null) }
    }

    /**
     * 服务这一帧用不了：攒够帧数就弹说明对话框，单帧失败往往只是一次抖动，不值得打断扫描。
     * 失败时的日志用服务端返回的原话：转述过的「常见原因」往往对不上真正错在哪里，而这份日志要
     * 交给别的 AI 去读，改写过就更查不出东西了。
     *
     * 弹窗期间不求值：每帧都写一次 StateFlow 会白白触发重组。已经弹了就等界面来关，
     * 关掉后从零重数，所以服务持续不可用时大约每隔几秒会再提醒一次
     */
    private fun onServiceProblem(problem: ServiceProblem) {
        if (_uiState.value.serviceProblem != null) return
        problemFrames++
        if (problemFrames < PROBLEM_FRAMES_BEFORE_PROMPT) return
        problemFrames = 0
        _uiState.update { it.copy(serviceProblem = problem) }
    }

    /**
     * 关掉说明对话框。两个按钮都要先关掉它，至于接下来是复制日志、去设置还是回主页，由界面决定：
     * 服务用不了的话，留在本页只能是被同一条报错反复打断
     */
    fun closeServiceDialog() {
        _uiState.update { it.copy(serviceProblem = null) }
    }

    /**
     * 清空累加记录，开始扫描下一件商品。整体复位到刚进页的样子
     */
    fun clearRecord() {
        latestFrame = null
        _uiState.update {
            it.withRecord(ScanResult()).copy(
                reasoning = "",
                status = ScanStatus.Hidden,
                stage = ScanStage.SCANNING,
                location = "",
                photos = emptyList(),
                lockedFields = emptySet(),
                cropSource = null,
                saving = false
            )
        }
        // 不走停留：四个字段已经当场变成「未知」，胶囊若还被上一句拦着就跟眼前对不上了
        statusDwell.reset(ScanStatus.Hidden)
    }

    private companion object {
        const val TAG = "ScanViewModel"

        // 仅用于请求失败后的退避，正常路径上不额外等待
        const val RETRY_DELAY_MS = 2_000L

        // 连续失败多少帧才提示。一帧失败退避 2 秒，两帧即几秒钟：真的连不上很快能等到提示，
        // 而偶发的一次抖动（下一帧就好了）不会弹窗。用户关掉后计数归零，因此服务一直不可用时
        // 大约每隔这么久会再提醒一次
        const val PROBLEM_FRAMES_BEFORE_PROMPT = 2
    }
}
