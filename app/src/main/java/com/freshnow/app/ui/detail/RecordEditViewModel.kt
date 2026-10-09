package com.freshnow.app.ui.detail

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ImageChange
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.ScanValueFormat
import com.freshnow.app.data.decodePickedImage
import com.freshnow.app.data.decodeScanImage
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.toSquareJpegBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/**
 * 编辑页的草稿。[loaded] 表示已经读过库（读完才知道记录在不在），[found] 为 false 即记录不存在；
 * [derivedExpiry] 是过期日期留空时按草稿里的生产日期与保质期现算的结果，随每次改动重算。
 *
 * 三个 `xxxInvalid` 是「认不出这个写法」：空白不算错（模型没读到、还没填都正常），但只要写了
 * 认不出的东西就算，用于挡下保存与显示错误文案。
 *
 * 照片分成三份：[image] 是本屏要显示的那张，[cropSource] 是相册选来、等着裁剪的原图
 * （非 null 即裁剪页正在显示），[imageChange] 记的是「这张与库里那张是什么关系」——
 * 选中与裁好的照片都只停在草稿里，点保存才落盘，这样取消编辑不会在磁盘上留下没人引用的图。
 */
data class RecordEditUiState(
    val loaded: Boolean = false,
    val found: Boolean = false,
    /** 这是一条还没落库的新记录（手动录入）：保存时是首次写入而不是覆盖，标题也不一样 */
    val isNew: Boolean = false,
    val productName: String = "",
    val productionDate: String = "",
    val expiryDate: String = "",
    val shelfLife: String = "",
    val productionDateInvalid: Boolean = false,
    val shelfLifeInvalid: Boolean = false,
    val expiryDateInvalid: Boolean = false,
    val derivedExpiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput,
    val image: Bitmap? = null,
    val cropSource: Bitmap? = null,
    val imageChange: ImageChange = ImageChange.Keep,
    /** 选来的图读不出来时置起，由界面提示一次后清掉（见 RecordEditScreen） */
    val imageReadFailed: Boolean = false,
    /** 正在写库。写盘不是瞬时的，这段时间里再点一次保存就会多弹一层返回栈（见 save） */
    val saving: Boolean = false
)

/**
 * 有认不出的写法、或正在写库时不给保存。Material 的 Errors 模式：*"Disable the submission of a
 * form if errors are detected"*——写错的日期会让推算与剩余天数静默失效，而界面上只剩一句「推不出」。
 *
 * 照片不进这个判据：它没有「认不出」这种状态，而读取失败是提示而不是校验（见 imageReadFailed）。
 *
 * 新记录另外要求至少填了一项：与扫描页「一个字段都没读到就不存」同一条规矩，免得手滑存下一堆
 * 什么都没有的记录。已有记录不受这条约束——把字段清空是一次编辑，不该被拦下。
 */
val RecordEditUiState.canSave: Boolean
    get() = !saving && !productionDateInvalid && !shelfLifeInvalid && !expiryDateInvalid &&
        (!isNew || hasAnyValue)

/** 四个字段里有没有哪一个填了东西。扫描侧的同名判据在 ScanResult.hasAnyValue */
val RecordEditUiState.hasAnyValue: Boolean
    get() = productName.isNotBlank() || productionDate.isNotBlank() ||
        expiryDate.isNotBlank() || shelfLife.isNotBlank()

/**
 * 改一条记录的文字字段与照片，或者手填一条还没有的新记录（手动录入，见 [RecordEditViewModel.startNew]）。
 *
 * 四项放在同一份草稿里一起改、一起存：过期日期可能是标签印刷的值，也可能是由生产日期与保质期
 * 推算出来的派生值，这条联动必须在同一屏里看得见——逐个字段就地改会把它藏起来（见 ExpiryCalculator）。
 *
 * 照片也在这份草稿里，理由相同：它是同一条记录的一个字段，与文字一起改、一起存，
 * 用户不必记住「文字在编辑页改、照片在别处改」。
 */
class RecordEditViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    private val _uiState = MutableStateFlow(RecordEditUiState())
    val uiState: StateFlow<RecordEditUiState> = _uiState.asStateFlow()

    /** 读进来的那条记录，保存时在它上面改字段：照片文件名、保存时间都靠它原样带过去。新记录时为 null */
    private var loaded: ScanRecord? = null

    /** 已经读过哪条记录。用它判重而不是 uiState.loaded：后者是给界面看的状态，不是「已经读过库」的证据 */
    private var loadedId: Long? = null

    /** 已经开过手填的草稿。与 [loadedId] 一样只是个判重标记，两者不会同时成立 */
    private var startedNew = false

    /**
     * 读入草稿。只读一次、不订阅：正在编辑的值不该被库里的写入抢走。
     *
     * 已经读过同一个 id 就直接返回。屏幕旋转会重建本页，[LaunchedEffect] 跟着重跑，而这里是一次
     * 整体替换——草稿会被库里的旧值盖回去，裁剪中还没保存的图也会被重置成 null、退回编辑步骤。
     */
    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        startedNew = false
        viewModelScope.launch {
            val record = repository.find(id)
            loaded = record
            if (record == null) {
                _uiState.value = RecordEditUiState(loaded = true, found = false)
                return@launch
            }
            val image = withContext(Dispatchers.IO) { decodeScanImageFile(record) }
            _uiState.value = RecordEditUiState(
                loaded = true,
                found = true,
                productName = record.productName,
                productionDate = record.productionDate,
                expiryDate = record.expiryDate,
                shelfLife = record.shelfLife,
                image = image
            ).revalidate()
        }
    }

    fun onProductNameChange(value: String) {
        _uiState.update { it.copy(productName = value) }
    }

    /**
     * 开一份手填的新记录草稿：不读库，四个字段与照片都是空的。
     *
     * 与 [load] 同样只做一次：本页重建（返回、进程内重建）会再调一次，重开一份空草稿会把用户
     * 已经敲进去的内容擦掉。
     */
    fun startNew() {
        if (startedNew) return
        startedNew = true
        loadedId = null
        loaded = null
        _uiState.value = RecordEditUiState(loaded = true, found = true, isNew = true)
    }

    fun onProductionDateChange(value: String) {
        _uiState.update { it.copy(productionDate = value).revalidate() }
    }

    fun onExpiryDateChange(value: String) {
        _uiState.update { it.copy(expiryDate = value).revalidate() }
    }

    fun onShelfLifeChange(value: String) {
        _uiState.update { it.copy(shelfLife = value).revalidate() }
    }

    /**
     * 相册选来的图：解码后停在草稿里等着裁剪，先不落盘。
     * 读不出来时只置一个提示标志，草稿照片一个字都不动——不能因为一次读图失败就把原来的照片抹掉。
     */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                decodePickedImage(getApplication<Application>().contentResolver, uri)
            }
            if (decoded == null) {
                _uiState.update { it.copy(imageReadFailed = true) }
            } else {
                _uiState.update { it.copy(cropSource = decoded) }
            }
        }
    }

    /** 放弃这次裁剪：原图与草稿照片都保持原样 */
    fun cancelCrop() {
        _uiState.update { it.copy(cropSource = null) }
    }

    /** 裁剪页确认：按用户摆出来的框裁方、编码，进草稿等保存 */
    internal fun applyCrop(window: CropWindow) {
        val source = _uiState.value.cropSource ?: return
        viewModelScope.launch {
            val replaced = withContext(Dispatchers.Default) {
                source.croppedTo(window).toSquareJpegBytes()
            }
            val preview = replaced?.let {
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (replaced == null || preview == null) {
                _uiState.update { it.copy(cropSource = null, imageReadFailed = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    image = preview,
                    cropSource = null,
                    imageChange = ImageChange.Replace(replaced)
                )
            }
        }
    }

    /**
     * 拍到的照片。相机给的字节已经是正方形 JPEG（与扫描页同一套规格，见 ScanImageCodec），
     * 所以不必过裁剪页；这里只是解出一张用来显示的位图。
     */
    fun onPhotoCaptured(jpeg: ByteArray) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            }
            if (preview == null) {
                _uiState.update { it.copy(imageReadFailed = true) }
                return@launch
            }
            _uiState.update {
                it.copy(image = preview, imageChange = ImageChange.Replace(jpeg))
            }
        }
    }

    /**
     * 移除照片。只改草稿：库里那张仍在原处（保存时才连文件一起删），所以取消编辑等于没删过。
     */
    fun onImageRemoved() {
        _uiState.update { it.copy(image = null, imageChange = ImageChange.Remove) }
    }

    fun closeImageReadFailed() {
        _uiState.update { it.copy(imageReadFailed = false) }
    }

    /**
     * 落盘之后再回调，界面在这之后才退出本页：直接退出会把 ViewModel 一起清掉，
     * 而这次写入挂在它的 viewModelScope 上，会被一并取消。回调参数为 false 表示照片没写成功
     * （其余字段已经保存，旧照片保留），界面就这一件事提示用户。
     *
     * 写入期间不再接受第二次保存（[RecordEditUiState.saving]，按钮也会跟着置灰）：一次写入对应一次
     * onSaved，而调用方拿到它才 pop 返回栈；连点两下就是连着 pop 两次，编辑页之后的那一页会一起
     * 被弹掉，屏幕上看到的是「突然回到主页」。
     *
     * 写法沿用模型入库那一套规整（[ScanValueFormat]）：值不管是模型读的还是手输的，落库只有一种写法；
     * 认不出来的原样存下——与详情页对印刷值的处理一致，不替用户猜。
     */
    fun save(onSaved: (imageSaved: Boolean) -> Unit) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        val current = _uiState.value
        val record = loaded
        viewModelScope.launch {
            val imageSaved = try {
                when {
                    // 手填的新记录：库里还没有它，这一次是首次写入，保存时间就在这一刻定下。
                    // 没有记录可编辑时用的也是这一支，于是手动录入与「扫描后存下」在库里长得一样
                    current.isNew -> repository.insert(
                        ScanRecord(
                            productName = current.productName.trim(),
                            productionDate = ScanValueFormat.date(current.productionDate.trim()),
                            expiryDate = ScanValueFormat.date(current.expiryDate.trim()),
                            shelfLife = ScanValueFormat.shelfLife(current.shelfLife.trim()),
                            imageName = "",
                            savedAt = System.currentTimeMillis()
                        ),
                        current.imageChange
                    )

                    // 记录已经读不到了（比如在别处被删掉）就没有可存的东西，当作照片也没问题，直接退出
                    record == null -> true

                    else -> repository.update(
                        record.copy(
                            productName = current.productName.trim(),
                            productionDate = ScanValueFormat.date(current.productionDate.trim()),
                            expiryDate = ScanValueFormat.date(current.expiryDate.trim()),
                            shelfLife = ScanValueFormat.shelfLife(current.shelfLife.trim())
                        ),
                        current.imageChange
                    )
                }
            } catch (e: Exception) {
                // 写库本身就失败了：留在本页并放开保存，让用户再试一次。
                // 抛出去只会把应用崩掉，而这一次失败对用户来说是「没存上」不是「应用坏了」。
                // 按 Exception 而不是 Throwable 捕：本页被销毁时的取消不该被当成一次写入失败
                _uiState.update { it.copy(saving = false) }
                return@launch
            }
            onSaved(imageSaved)
        }
    }

    /** 库里那张照片，读不出来时按「没有照片」处理，与详情页一致 */
    private fun decodeScanImageFile(record: ScanRecord) = decodeScanImage(repository.imageFile(record))

    /** 按裁剪框切下一块。坐标已经由 [cropWindow] 夹进图内 */
    private fun Bitmap.croppedTo(window: CropWindow): Bitmap {
        val side = window.side.coerceAtMost(minOf(width, height))
        val left = window.left.coerceIn(0, width - side)
        val top = window.top.coerceIn(0, height - side)
        if (side == width && side == height && left == 0 && top == 0) return this
        return Bitmap.createBitmap(this, left, top, side, side)
    }

    /** 认得出/认不出交给 [ScanValueFormat]：与模型入库、推算用的是同一套规则，不另写一份 */
    private fun RecordEditUiState.revalidate() = copy(
        productionDateInvalid = isUnrecognizableDate(productionDate),
        shelfLifeInvalid = isUnrecognizableShelfLife(shelfLife),
        expiryDateInvalid = isUnrecognizableDate(expiryDate)
    ).recomputeDerived()

    private fun isUnrecognizableDate(raw: String): Boolean =
        raw.isNotBlank() && ScanValueFormat.parseDate(raw.trim()) == null

    private fun isUnrecognizableShelfLife(raw: String): Boolean =
        raw.isNotBlank() && ScanValueFormat.parseShelfLife(raw.trim()) == null

    private fun RecordEditUiState.recomputeDerived() = copy(
        derivedExpiry = ExpiryCalculator.resolve(
            // 留空才轮得到推算；填了值就是印刷值，以它为准
            printedExpiry = "",
            productionDate = productionDate,
            shelfLife = shelfLife
        )
    )
}
