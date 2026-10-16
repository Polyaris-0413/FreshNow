package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.component.EditField
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.MenuBottomSheet
import com.freshnow.app.ui.component.MenuSheetItem
import com.freshnow.app.ui.component.ScanPhoto
import com.freshnow.app.ui.component.ScrollEdgeFade
import com.freshnow.app.ui.component.hideSheetThen
import com.freshnow.app.ui.component.splicedShape
import com.freshnow.app.ui.scan.PhotoCaptureScreen
import com.freshnow.app.ui.showToast
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTransitions
import com.freshnow.app.ui.theme.backEnter
import com.freshnow.app.ui.theme.backExit
import com.freshnow.app.ui.theme.forwardEnter
import com.freshnow.app.ui.theme.forwardExit
import kotlinx.coroutines.launch

/**
 * 本页的整屏状态。裁剪、拍照都是一整屏，与编辑表单平级替换。
 *
 * 裁剪那张源图挂在状态里（[EditStep.Crop]）而不是用时另读 ViewModel：状态从 [EditStep.Crop]
 * 换成 [EditStep.Edit] 的那一帧，ViewModel 里的原图已经被清掉了，退场动画拿什么画？
 * 带着图走，退场那一帧内容就还在。
 */
internal sealed interface EditStep {
    data object Edit : EditStep

    data class Crop(val source: Bitmap) : EditStep

    data object Capture : EditStep
}

/**
 * 三个整屏状态之间的转场。
 *
 * 用 AnimatedContent 而不是一整块 if/return：裁剪页、拍照页都是“进去过再退回”的整屏，硬切会
 * 丢掉项目里已有一套的页面转场（见 FreshNowTransitions）。方向按“是不是回到表单”定：回到表单
 * 是返回方向，从表单进整屏是前进方向。
 *
 * 退场那帧两页同时在，各自半透明地叠着，所以底下要垫一层主题背景色：不垫就会透出窗口背景，
 * 深色模式下表现为一下黑闪（与 NavHost 同一个理由）。
 *
 * 提为 internal 是为了能在仪器化测试里直接推进动画时钟（mainClock）断言“转场中途两页都在”：
 * 真机上几百毫秒的转场抢不到截图，而硬切与转场的区别恰好就在那几帧。
 */
@Composable
internal fun EditStepTransition(
    step: EditStep,
    modifier: Modifier = Modifier,
    content: @Composable (EditStep) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                when {
                    targetState is EditStep.Edit ->
                        backEnter() togetherWith backExit()

                    initialState is EditStep.Edit ->
                        forwardEnter() togetherWith forwardExit()

                    // 裁剪与拍照不会直接互换，留个过渡免得两个整屏硬切
                    else -> FreshNowTransitions.fadeSwap()
                }
            },
            label = "editStep"
        ) { current ->
            content(current)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(
    /** 要改的那条记录；null 表示手动录入一条新记录，库里还没有它 */
    recordId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordEditViewModel = viewModel()
) {
    LaunchedEffect(recordId) {
        if (recordId == null) viewModel.startNew() else viewModel.load(recordId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val readFailedToast = stringResource(R.string.record_image_read_failed)

    // 换照片的来源选择是本页的状态，不进 ViewModel：它只是「面板开着吗」，不需要跨进程重建
    var showImageSources by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    val imageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(
        // 系统的照片选择器：不需要读存储权限，Android 13 以下由系统退回自己的选择器
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    // 先收起面板再动手：拉起系统选择器或进拍照页与收起动画同时进行会互相打断
    fun dismissSheetThen(action: () -> Unit) =
        scope.hideSheetThen(imageSheetState, onHidden = { showImageSources = false }, action = action)

    // 选来的图读不出来时报一声。提示是个瞬时动作，读完立刻清掉标志，免得下次进来又弹一遍
    LaunchedEffect(uiState.imageReadFailed) {
        if (uiState.imageReadFailed) {
            showToast(context, readFailedToast)
            viewModel.closeImageReadFailed()
        }
    }

    val cropSource = uiState.cropSource
    val step: EditStep = cropSource?.let { EditStep.Crop(it) }
        ?: if (showCamera) EditStep.Capture else EditStep.Edit

    EditStepTransition(step = step, modifier = modifier) { current ->
        when (current) {
            is EditStep.Crop -> {
                // 系统返回键在整屏状态里要先关它；不拦的话会一路退回详情页，
                // 摆好的裁剪框和刚拍的那一张就此丢掉
                BackHandler { viewModel.cancelCrop() }
                ImageCropScreen(
                    image = current.source,
                    onCancel = viewModel::cancelCrop,
                    onConfirm = viewModel::applyCrop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            EditStep.Capture -> {
                BackHandler { showCamera = false }
                PhotoCaptureScreen(
                    onCancel = { showCamera = false },
                    onCaptured = { jpeg ->
                        // 这个回调来自相机的分析线程（见 PhotoCaptureScreen），页面状态要回主线程改
                        scope.launch { showCamera = false }
                        viewModel.onPhotoCaptured(jpeg)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            EditStep.Edit -> EditForm(
                uiState = uiState,
                onBack = onBack,
                onSave = viewModel::save,
                onChangePhoto = { showImageSources = true },
                viewModel = viewModel
            )
        }
    }

    if (showImageSources) {
        ChangePhotoSheet(
            hasImage = uiState.image != null,
            sheetState = imageSheetState,
            onDismissRequest = { showImageSources = false },
            onPickFromGallery = {
                dismissSheetThen {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onTakePhoto = { dismissSheetThen { showCamera = true } },
            onRemove = { dismissSheetThen(viewModel::onImageRemoved) }
        )
    }
}

/**
 * 编辑表单那一屏：顶栏（含保存）加表单本体。
 *
 * 从 [RecordEditScreen] 里拆出来只为让上面那段转场读得清——三个整屏状态各是一块。
 */
@Composable
private fun EditForm(
    uiState: RecordEditUiState,
    onBack: () -> Unit,
    onSave: ((imageSaved: Boolean) -> Unit) -> Unit,
    onChangePhoto: () -> Unit,
    viewModel: RecordEditViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val saveFailedToast = stringResource(R.string.record_image_save_failed)

    FreshNowSubPage(
        // 新记录还没有名字可提，标题换成功能名：「编辑记录」会让人去找那条并不存在的记录
        title = if (uiState.isNew) {
            stringResource(R.string.manual_entry)
        } else {
            stringResource(R.string.record_edit_title)
        },
        onBack = onBack,
        actions = {
            // 记录都读不到了就没有可存的东西，不留一个按下去没反应的按钮
            if (uiState.found) {
                TextButton(
                    // 照片写盘失败也照旧退回去：其余字段已经存好了，而提示只说照片这一件事，
                    // 留在本页只会让人以为整个保存都失败了
                    onClick = {
                        onSave { imageSaved ->
                            if (!imageSaved) showToast(context, saveFailedToast)
                            onBack()
                        }
                    },
                    enabled = uiState.canSave
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        },
        // 点空白处收键盘兼“结束这个字段的输入”：保存被禁用时错误文案才显出来（见 RecordEditForm），
        // 而禁用的按钮不吃点击，那一下点击只有靠这里接住。挂在页面这一层是因为顶栏在表单之外
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        }
    ) { innerPadding ->
        if (!uiState.loaded) return@FreshNowSubPage

        if (!uiState.found) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.record_detail_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@FreshNowSubPage
        }

        // 表单比一屏长（照片就占掉半屏，再加五项），必有一项落在折叠线以下。
        // 底边用渐隐而不是硬边：这一页没有滚动条（锁在 material3 1.4，没有滚动条组件），
        // 而渐隐说的正是缺的那句话——「下面还有」。见 ScrollEdgeFade。
        // 键盘顶上来时 Box 整体变矮（imePadding 在这一层），于是渐隐不会跑到键盘背后
        val formScroll = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            RecordEditForm(
                uiState = uiState,
                onProductNameChange = viewModel::onProductNameChange,
                onLocationChange = viewModel::onLocationChange,
                onProductionDateChange = viewModel::onProductionDateChange,
                onExpiryDateChange = viewModel::onExpiryDateChange,
                onShelfLifeChange = viewModel::onShelfLifeChange,
                onChangePhoto = onChangePhoto,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(formScroll)
                    .padding(FreshNowSpacing.sm)
            )
            ScrollEdgeFade(
                visible = formScroll.canScrollForward,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * 换照片：从哪来，或者不要了。
 *
 * 就是一叠居中卡片（见 MenuBottomSheet），与主页顶栏「更多选项」展开后同一副外观：这里是
 * 挑一个动作，不是一列设置项，M3 的 ListItem 长成「左图右文的一行行」是给后者的。
 *
 * 「从相册选择」与「拍照」拼成一块：两者是同一层级的两个来路，并排各占一半比上下叠成两张卡片
 * 更读得出它们是一组；中间一条 outlineVariant 分隔线把两块分干净（M3 的 connected buttons
 * 就是这么拼的）。「移除照片」自成一张卡——它是另一回事（不要了），且只在真有照片时才出现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePhotoSheet(
    hasImage: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemove: () -> Unit
) {
    MenuBottomSheet(sheetState = sheetState, onDismissRequest = onDismissRequest) {
        // 两个来源上下拼成一组（上：拍照，下：相册），与结果字段那一组同一套拼法：外圆内方、
        // 中间只留一条 2dp 的缝。
        //
        // 原来它们是并排两个动作、中间一条竖分隔线。改掉的理由是规范里那句「用分隔线分组，而不是
        // 分开单个项」：拍照与相册本来就是同一组的平级动作；而竖分隔线的官方用例是大屏上把正文
        // 与媒体并排分开，与这里不是一回事。拼成一块之后，「这两个是一组」靠形状就说清楚了。
        val outerCorner = MaterialTheme.shapes.medium
        val innerCorner = MaterialTheme.shapes.extraSmall
        Column(verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)) {
            MenuSheetItem(
                text = stringResource(R.string.action_take_photo),
                onClick = onTakePhoto,
                shape = splicedShape(top = outerCorner, bottom = innerCorner)
            )
            MenuSheetItem(
                text = stringResource(R.string.record_image_source_gallery),
                onClick = onPickFromGallery,
                shape = splicedShape(top = innerCorner, bottom = outerCorner)
            )
        }
        // 「不要了」不属于上面那一组：它是另一件事，所以自己一块、四角都取卡片自己的形状
        if (hasImage) {
            MenuSheetItem(
                text = stringResource(R.string.record_image_remove),
                onClick = onRemove
            )
        }
    }
}

/**
 * 编辑表单本体，不含读库与落盘：便于按定值直接断言字段、照片与推算提示（见 RecordEditFormTest）。
 *
 * 照片排在最上面，与详情页的顺序一致；它是这条记录的一个字段，所以与四个文本字段同属一份草稿、
 * 同一次保存。
 *
 * 表单里一律是文本框，不挂日期选择器：这里改的是「模型读错的地方」，存量值可能是任意写法
 * （`2026.10.01`、`2026年10月`、空串），而选择器只能产出标准日期，一确认就会把原值覆盖掉。
 *
 * 错误文案的时机按 Material 的 Errors 模式：*"Show error text only after user interaction with a
 * field"*——所以只在字段失焦之后才报，不跟着敲键闪红（日期是一字符一字符敲的，中途总是认不出）。
 * 打开页面时就已经写错的旧值例外：它不是用户刚敲的，而且不说的话保存被禁用会变得没理由。
 *
 * 过期日期下面是它的来源说明：留空表示跟随推算，所以把推算结果显示在同一屏里；填了值就是标签
 * 印刷值，以它为准，不再显示推算——一屏之内就把「谁说了算」交代清楚。两者天然不会同时出现：
 * 推算说明只在留空时给，而错误文案只在非空且认不出时给。
 */
@Composable
internal fun RecordEditForm(
    uiState: RecordEditUiState,
    onProductNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onProductionDateChange: (String) -> Unit,
    onExpiryDateChange: (String) -> Unit,
    onShelfLifeChange: (String) -> Unit,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        // 照片那一栏：一枚缩略图加一句「更换照片」，整行可点。
        //
        // 原来这里是占满宽度的一张大卡片（方形照片约 380dp，半个屏幕还多），再加上五项文字，
        // 这一页就超过了可视区，必有一项被推到折叠线以下。照片在这一页只需回答「这条记录配的是
        // 哪张图」与「怎么换掉」；拍得好不好由详情页那张大图负责（那里点开还能看全屏）。
        // 卡片的轮廓留着，它仍然是一个可编辑项（与几个文本框同一层意思），只是不再占半屏。
        //
        // 圆角与描边跟同屏那几个输入框对齐：这一页主要是输入框，一块圆角、描边颜色都与它们不同的
        // 卡片摆在里面就是一处杂音。
        //
        // 圆角直接引输入框自己的形状来源；颜色取 outline——这是 M3 给 outlined text field 定的那个
        // 颜色角色（OutlinedTextFieldTokens.OutlineColor → ColorSchemeKeyTokens.Outline），
        // 不是输入框里图标与标签用的 onSurfaceVariant；宽度见 FreshNowSize.fieldOutlineWidth。
        OutlinedCard(
            onClick = onChangePhoto,
            modifier = Modifier.fillMaxWidth(),
            shape = OutlinedTextFieldDefaults.shape,
            border = BorderStroke(
                FreshNowSize.fieldOutlineWidth,
                MaterialTheme.colorScheme.outline
            )
        ) {
            Row(
                modifier = Modifier.padding(FreshNowSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScanPhoto(
                    image = uiState.image,
                    modifier = Modifier.size(FreshNowSize.thumbnail),
                    // 框是 48dp 方，图也得填满它（源图已裁成方形，这一下实际上不会裁掉内容）
                    contentScale = ContentScale.Crop,
                    // 圆角跟着外面那块框走：48dp 的方块配 medium（12dp）会把整个角都圆掉；
                    // 占位图标也换小一档：48dp 的图标在这 48dp 的格子里会顶满（见 ScanPhoto）
                    shape = MaterialTheme.shapes.extraSmall,
                    placeholderIconSize = FreshNowSize.smallIcon
                )
                Text(
                    text = stringResource(R.string.record_image_change),
                    modifier = Modifier.padding(start = FreshNowSpacing.sm),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        EditField(
            value = uiState.productName,
            onValueChange = onProductNameChange,
            label = stringResource(R.string.scan_product_name)
        )
        // 排在名称之后：名称说的是「这是什么」，位置说的是「它在哪」，两句是一件事；
        // 而下面三项是一条推算链（生产日期 + 保质期 → 过期日期），位置插在它们中间会把链子打断。
        // 详情页上也是这个次序，两处对齐。
        // 开关关着时不画这一栏（见 RecordEditUiState.supplementEnabled）
        if (uiState.supplementEnabled) {
            EditField(
                value = uiState.location,
                onValueChange = onLocationChange,
                label = stringResource(R.string.record_location)
            )
        }
        EditField(
            value = uiState.productionDate,
            onValueChange = onProductionDateChange,
            label = stringResource(R.string.scan_production_date),
            error = if (uiState.productionDateInvalid) stringResource(R.string.record_edit_error_unrecognized) else null
        )
        EditField(
            value = uiState.shelfLife,
            onValueChange = onShelfLifeChange,
            label = stringResource(R.string.scan_shelf_life),
            error = if (uiState.shelfLifeInvalid) stringResource(R.string.record_edit_error_unrecognized) else null
        )
        EditField(
            value = uiState.expiryDate,
            onValueChange = onExpiryDateChange,
            label = stringResource(R.string.scan_expiry_date),
            error = if (uiState.expiryDateInvalid) stringResource(R.string.record_edit_error_unrecognized) else null,
            hint = if (uiState.expiryDate.isBlank()) {
                derivedExpiryHint(uiState.derivedExpiry)
            } else {
                null
            },
            // 本项之后没有可跳的下一项，输入法的动作键收在「完成」
            imeAction = ImeAction.Done
        )
    }
}

/**
 * 一个字段的输入框。两处表单（编辑记录、补充信息）共用同一个，见
 * [com.freshnow.app.ui.component.EditField]。
 */

/** 过期日期留空时的说明：算得出就给日期，算不出就如实说推不出（哪个字段错了由那个字段自己报） */
@Composable
private fun derivedExpiryHint(derived: ExpiryOutcome): String = when (derived) {
    is ExpiryOutcome.Resolved -> stringResource(R.string.record_edit_expiry_hint, derived.date)
    ExpiryOutcome.InsufficientInput, ExpiryOutcome.UnparseableShelfLife ->
        stringResource(R.string.record_edit_expiry_hint_pending)
}
