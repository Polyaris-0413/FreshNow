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
import com.freshnow.app.data.anyFieldFilled
import com.freshnow.app.data.decodePickedImage
import com.freshnow.app.data.decodeScanImage
import com.freshnow.app.data.isUnrecognizableDate
import com.freshnow.app.data.isUnrecognizableShelfLife
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.toSquareJpegBytes
import com.freshnow.app.ui.component.EditPhoto
import com.freshnow.app.data.RecordField
import com.freshnow.app.ui.component.RecordFormFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * 照片是一份列表 [photos]（见 [EditPhoto]），另有 [cropSource] 是相册选来、等着裁剪的原图
 * （非 null 即裁剪页正在显示）。新加的图只停在草稿里，点保存才落盘，这样取消编辑不会在磁盘上
 * 留下没人引用的图。
 */
data class RecordEditUiState(
    val loaded: Boolean = false,
    val found: Boolean = false,
    /** 这是一条还没落库的新记录（手动录入）：保存时是首次写入而不是覆盖，标题也不一样 */
    val isNew: Boolean = false,
    val productName: String = "",
    /**
     * 位置。本页唯一一项不由模型读入、只能由用户写的字段（见 [ScanRecord.location]），
     * 它没有「认不出的写法」这一说，所以不参与校验
     */
    val location: String = "",
    val productionDate: String = "",
    val expiryDate: String = "",
    val shelfLife: String = "",
    val productionDateInvalid: Boolean = false,
    val shelfLifeInvalid: Boolean = false,
    val expiryDateInvalid: Boolean = false,
    val derivedExpiry: ExpiryOutcome = ExpiryOutcome.InsufficientInput,
    /** 草稿里的照片，顺序即展示顺序、第一位是代表图 */
    val photos: List<EditPhoto> = emptyList(),
    /** 相册选来、等着裁剪的原图；非 null 即裁剪页正在显示 */
    val cropSource: Bitmap? = null,
    /** 选来的图读不出来时置起，由界面提示一次后清掉（见 RecordEditScreen） */
    val imageReadFailed: Boolean = false,
    /** 正在写库。写盘不是瞬时的，这段时间里再点一次保存就会多弹一层返回栈（见 save） */
    val saving: Boolean = false
)

/** 进页时那条记录的字段（已按落库写法规整）与照片名，判「有没有改动」的基准（见 [isDirtyAgainst]） */
internal data class RecordBaseline(
    val fields: StoredFields,
    val imageNames: List<String>
)

/** 交给共用表单的那几项（见 [com.freshnow.app.ui.component.RecordEditForm]）*/
internal fun RecordEditUiState.toFormFields() = RecordFormFields(
    productName = productName,
    location = location,
    productionDate = productionDate,
    expiryDate = expiryDate,
    shelfLife = shelfLife,
    productionDateInvalid = productionDateInvalid,
    shelfLifeInvalid = shelfLifeInvalid,
    expiryDateInvalid = expiryDateInvalid
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

/**
 * 四个「模型读到的」字段里有没有哪一个填了东西（位置不在其列——它是用户自己写的，
 * 不参与「这条记录有没有内容」这个判据）。判据只有一处实现（见 data/ScanResult.kt 的 anyFieldFilled），
 * 与扫描侧「一个字段都没读到就不存」是同一条规矩。
 */
val RecordEditUiState.hasAnyValue: Boolean
    get() = anyFieldFilled(productName, productionDate, expiryDate, shelfLife)

/**
 * 这一页有没有东西会因为返回而丢掉：有才先问一句（与扫描页「有结果才问」同一条规矩），
 * 没有就直接退，不拿一句「尚未保存」去拦一个什么都没改的人。
 *
 * 新记录比的是「输没输入过」：字段一项没填就是空页，照片也是这次输入的一部分。
 * 已有记录比的是「与进页时那条记录差多少」，所以改了一种写法又改回去（落库只有一种写法，
 * 见 [ScanValueFormat]）、或者删了照片又原样加回来，都不算改动。
 *
 * 位置也算：它在这份草稿里，返回时一样会丢。
 *
 * [baseline] 为 null 表示手上是一份手填的新记录，没有可比的旧版。
 */
internal fun RecordEditUiState.isDirtyAgainst(baseline: RecordBaseline?): Boolean {
    // 还没读到库时「有没有改动」无从谈起，此刻拦住返回只会卡住一个空白页
    if (!loaded || !found) return false
    val base = baseline ?: return hasAnyValue || photos.isNotEmpty()
    val images = photoChange
    return storedFields() != base.fields ||
        images.kept != base.imageNames ||
        images.added.isNotEmpty()
}

/**
 * 草稿里的字段按落库写法规整后的样子（见 [RecordEditViewModel.save]）。
 *
 * 提着它是因为插入与覆盖两条路要的是同一套规整（[ScanValueFormat]）：各写一遍的话，
 * 「值不管谁写的、落库只有一种写法」这条规矩会从一个分支漏掉，而那就是同一个日期在库里
 * 存成两种写法的来处。
 */
internal data class StoredFields(
    val productName: String,
    val location: String,
    val productionDate: String,
    val expiryDate: String,
    val shelfLife: String
)

private fun RecordEditUiState.storedFields() = StoredFields(
    productName = productName.trim(),
    // 自由文本，不参与任何规整：位置怎么写由用户说了算，这里只去掉首尾空白
    location = location.trim(),
    productionDate = ScanValueFormat.date(productionDate.trim()),
    expiryDate = ScanValueFormat.date(expiryDate.trim()),
    shelfLife = ScanValueFormat.shelfLife(shelfLife.trim())
)

/**
 * 草稿里的照片与库里那串名字之间的关系，见 [ImageChange.Update]。
 *
 * 当场从草稿推出来，而不是每次增删都维护一份：草稿是唯一真相，关系是它的函数——
 * 两处各记一份的话，「删了又加回来」「同一张图选两次」这类操作迟早会让它们分家。
 *
 * 返回 [ImageChange.Update] 而不是基类：这里描的总是「目标状态」，没有「原样保留」那一支。
 */
private val RecordEditUiState.photoChange: ImageChange.Update
    get() = ImageChange.Update(
        kept = photos.mapNotNull { photo -> photo.name.takeIf { it.isNotEmpty() } },
        added = photos.mapNotNull { it.bytes }
    )

/**
 * 改一条记录的文字字段、位置与照片，或者手填一条还没有的新记录（手动录入，见 [RecordEditViewModel.startNew]）。
 *
 * 这些字段放在同一份草稿里一起改、一起存：过期日期可能是标签印刷的值，也可能是由生产日期与保质期
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

    /** 进页时那条记录的原样（手填的新记录为 null），返回时判「有没有东西会丢」全靠它 */
    private var baseline: RecordBaseline? = null

    /** 已经读过哪条记录。用它判重而不是 uiState.loaded：后者是给界面看的状态，不是「已经读过库」的证据 */
    private var loadedId: Long? = null

    /** 已经开过手填的草稿。与 [loadedId] 一样只是个判重标记，两者不会同时成立 */
    private var startedNew = false

    /**
     * 这一页有没有东西会因为返回而丢掉，询问方是界面（见 [RecordEditScreen]）。
     *
     * 不在 [RecordEditUiState] 里存一个 Boolean：那份状态是「草稿长什么样」，而这是草稿与基准的差，
     * 两者各存一份就得在每处改动后面补一次重算。
     */
    fun hasUnsavedChanges(): Boolean = _uiState.value.isDirtyAgainst(baseline)

/** 草稿照片的 id 发到哪儿了。只在草稿内部流转，不与库里的任何东西对应 */
    private var nextPhotoId = 0L

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
            // 先读设置再读库：两样都拿到了才让界面渲染，否则位置那一栏会晚一帧冒出来、
            // 把下面几个字段当场往下顶一下
            val record = repository.find(id)
            loaded = record
            if (record == null) {
                baseline = null
                _uiState.value = RecordEditUiState(loaded = true, found = false)
                return@launch
            }
            val photos = loadPhotos(record)
            val draft = RecordEditUiState(
                loaded = true,
                found = true,
                productName = record.productName,
                location = record.location,
                productionDate = record.productionDate,
                expiryDate = record.expiryDate,
                shelfLife = record.shelfLife,
                photos = photos
            ).revalidate()
            // 基准取的是刚读进来的这条记录，而不是库里那一行：两个字段的写法要经过同一套规整，
            // 否则「只是换了个写法」会被当成改动（见 isDirtyAgainst）
            baseline = RecordBaseline(fields = draft.storedFields(), imageNames = record.imageNames)
            _uiState.value = draft
        }
    }

    fun onProductNameChange(value: String) {
        _uiState.update { it.copy(productName = value) }
    }

    /** 表单报回来的是「哪一项 + 新值」，在这里分派到各自的写入方法 */
    fun onFieldChange(field: RecordField, value: String) {
        when (field) {
            RecordField.PRODUCT_NAME -> onProductNameChange(value)
            RecordField.LOCATION -> onLocationChange(value)
            RecordField.PRODUCTION_DATE -> onProductionDateChange(value)
            RecordField.EXPIRY_DATE -> onExpiryDateChange(value)
            RecordField.SHELF_LIFE -> onShelfLifeChange(value)
        }
    }

    fun onLocationChange(value: String) {
        _uiState.update { it.copy(location = value) }
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
        baseline = null
        viewModelScope.launch {
            _uiState.value = RecordEditUiState(
                loaded = true,
                found = true,
                isNew = true,
            )
        }
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
            _uiState.update { state ->
                state.copy(
                    photos = state.photos + EditPhoto(id = nextPhotoId++, bytes = replaced, bitmap = preview),
                    cropSource = null
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
            _uiState.update { state ->
                state.copy(photos = state.photos + EditPhoto(id = nextPhotoId++, bytes = jpeg, bitmap = preview))
            }
        }
    }

    /**
     * 移除草稿里的某一张。只改草稿：库里那张仍在原处（保存时才不再引用它，文件由
     * [ScanRecordRepository.reconcileImages] 收走），所以取消编辑等于没删过。
     */
    fun onPhotoRemoved(index: Int) {
        _uiState.update { state ->
            if (index !in state.photos.indices) {
                state
            } else {
                state.copy(photos = state.photos.filterIndexed { i, _ -> i != index })
            }
        }
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
            val fields = current.storedFields()
            val imageSaved = try {
                when {
                    // 手填的新记录：库里还没有它，这一次是首次写入，保存时间就在这一刻定下。
                    // 没有记录可编辑时用的也是这一支，于是手动录入与「扫描后存下」在库里长得一样
                    current.isNew -> repository.insert(
                        ScanRecord(
                            productName = fields.productName,
                            location = fields.location,
                            productionDate = fields.productionDate,
                            expiryDate = fields.expiryDate,
                            shelfLife = fields.shelfLife,
                            savedAt = System.currentTimeMillis()
                        ),
                        current.photoChange
                    )

                    // 记录已经读不到了（比如在别处被删掉）就没有可存的东西，当作照片也没问题，直接退出
                    record == null -> true

                    // 只改自己这一类字段：位置归「补充信息」页，这里 copy 时不提它，它就不会被清掉
                    // 本页单稿里也带着位置（进来时从库里读到的），所以保存会把它原样写回去：
                    // 在补充信息页刚填的位置不会被这里清掉。两页都是先读整条记录、只写自己单稿里那几个字段
                    else -> repository.update(
                        record.copy(
                            productName = fields.productName,
                            location = fields.location,
                            productionDate = fields.productionDate,
                            expiryDate = fields.expiryDate,
                            shelfLife = fields.shelfLife
                        ),
                        current.photoChange
                    )
                }
            } catch (e: CancellationException) {
                // 本页被销毁（用户返回、进程内重建）时这次写入会随作用域一起取消，取消必须原样抛出：
                // 吞掉它等于把「协程被取消」记成「写入失败」，而 CancellationException 正是 Exception
                // 的子类，下面的 catch (Exception) 捕得到它
                throw e
            } catch (e: Exception) {
                // 写库本身就失败了：留在本页并放开保存，让用户再试一次。
                // 抛出去只会把应用崩掉，而这一次失败对用户来说是「没存上」不是「应用坏了」。
                _uiState.update { it.copy(saving = false) }
                return@launch
            }
            onSaved(imageSaved)
        }
    }

    /**
     * 把库里这条记录的照片读成草稿。
     *
     * 解不出来的那张也照样进草稿（[EditPhoto.bitmap] 为 null，格子用占位图顶上）：它还在记录名下，
     * 从这里悄悄抹掉就等于用户一进编辑页、什么都没做、一保存就把那张图删了。
     */
    private suspend fun loadPhotos(record: ScanRecord): List<EditPhoto> = record.imageNames.map { name ->
        EditPhoto(
            id = nextPhotoId++,
            name = name,
            bitmap = withContext(Dispatchers.IO) { decodeScanImage(repository.imageFile(name)) }
        )
    }

    /** 认得出/认不出交给 [ScanValueFormat]：与模型入库、推算用的是同一套规则，不另写一份 */
    private fun RecordEditUiState.revalidate() = copy(
        productionDateInvalid = isUnrecognizableDate(productionDate),
        shelfLifeInvalid = isUnrecognizableShelfLife(shelfLife),
        expiryDateInvalid = isUnrecognizableDate(expiryDate)
    ).recomputeDerived()

    private fun RecordEditUiState.recomputeDerived() = copy(
        derivedExpiry = ExpiryCalculator.resolve(
            // 留空才轮得到推算；填了值就是印刷值，以它为准
            printedExpiry = "",
            productionDate = productionDate,
            shelfLife = shelfLife
        )
    )
}
