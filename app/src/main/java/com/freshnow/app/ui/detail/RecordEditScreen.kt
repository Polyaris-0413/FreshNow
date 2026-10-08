package com.freshnow.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.ScanPhoto
import com.freshnow.app.ui.scan.PhotoCaptureScreen
import com.freshnow.app.ui.showToast
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(
    recordId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordEditViewModel = viewModel()
) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val readFailedToast = stringResource(R.string.record_image_read_failed)
    val saveFailedToast = stringResource(R.string.record_image_save_failed)

    // 换照片的来源选择是本页的状态，不进 ViewModel：它只是「对话框开着吗」，不需要跨进程重建
    var showImageSources by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(
        // 系统的照片选择器：不需要读存储权限，Android 13 以下由系统退回自己的选择器
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    // 选来的图读不出来时报一声。提示是个瞬时动作，读完立刻清掉标志，免得下次进来又弹一遍
    LaunchedEffect(uiState.imageReadFailed) {
        if (uiState.imageReadFailed) {
            showToast(context, readFailedToast)
            viewModel.closeImageReadFailed()
        }
    }

    val cropSource = uiState.cropSource
    if (cropSource != null) {
        // 裁剪与拍照是本页的整屏状态，系统返回键要先关它们；不拦的话会一路退回详情页，
        // 摆好的裁剪框和刚拍的那一张就此丢掉
        BackHandler { viewModel.cancelCrop() }
        // 整屏替换而不是另开一个目的地：裁剪结果是草稿的一部分，回传要经过 Bundle（ByteArray 有
        // 上限）或共享 ViewModel（要改作用域），两样都比留在一页里复杂
        ImageCropScreen(
            image = cropSource,
            onCancel = viewModel::cancelCrop,
            onConfirm = viewModel::applyCrop,
            modifier = modifier
        )
        return
    }

    if (showCamera) {
        BackHandler { showCamera = false }
        PhotoCaptureScreen(
            onCancel = { showCamera = false },
            onCaptured = { jpeg ->
                // 这个回调来自相机的分析线程（见 PhotoCaptureScreen），页面状态要回主线程改
                scope.launch { showCamera = false }
                viewModel.onPhotoCaptured(jpeg)
            },
            modifier = modifier
        )
        return
    }

    FreshNowSubPage(
        title = stringResource(R.string.record_edit_title),
        onBack = onBack,
        actions = {
            // 记录都读不到了就没有可存的东西，不留一个按下去没反应的按钮
            if (uiState.found) {
                TextButton(
                    // 照片写盘失败也照旧退回去：其余字段已经存好了，而提示只说照片这一件事，
                    // 留在本页只会让人以为整个保存都失败了
                    onClick = {
                        viewModel.save { imageSaved ->
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
        modifier = modifier.pointerInput(Unit) {
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

        RecordEditForm(
            uiState = uiState,
            onProductNameChange = viewModel::onProductNameChange,
            onProductionDateChange = viewModel::onProductionDateChange,
            onExpiryDateChange = viewModel::onExpiryDateChange,
            onShelfLifeChange = viewModel::onShelfLifeChange,
            onChangePhoto = { showImageSources = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(FreshNowSpacing.sm)
        )
    }

    if (showImageSources) {
        ChangePhotoDialog(
            hasImage = uiState.image != null,
            onDismissRequest = { showImageSources = false },
            onPickFromGallery = {
                showImageSources = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                showImageSources = false
                showCamera = true
            },
            onRemove = {
                showImageSources = false
                viewModel.onImageRemoved()
            }
        )
    }
}

/**
 * 换照片：从哪来，或者不要了。
 *
 * 用居中的对话框而不是底部面板：这里要的是一句问话加几个并列的做法，而 M3 把「问一句 + 两三个
 * 动作」的形态放在对话框（主页的删除确认、扫描页的离开确认都是这一套）；底部面板在 M3 里是承载
 * 内容与长期交互的地方（设置页那两个编辑面板）。
 *
 * 「从相册选择」与「拍照」拼成一块：两者是同一层级的两个来路，左右各占一半，中间一条
 * outlineVariant 分隔线（即 M3 的 connected buttons 形态）；上下排成两行的话，这一问会变成三行，
 * 与「移除照片」挤在同一列里，谁都不是重点。
 *
 * 「移除照片」放在对话框的确认位、用 error 色，与主页删除确认里那个「删除」同位置同做法：
 * 它是本对话框里唯一不可逆的一步，只有真的有照片时才出现（无图时它按下去也只是把草稿再置空）。
 */
@Composable
private fun ChangePhotoDialog(
    hasImage: Boolean,
    onDismissRequest: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.record_image_change)) },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhotoSourceAction(
                    text = stringResource(R.string.record_image_source_gallery),
                    onClick = onPickFromGallery,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(modifier = Modifier.fillMaxHeight())
                PhotoSourceAction(
                    text = stringResource(R.string.action_take_photo),
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            if (hasImage) {
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.record_image_remove))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * 拼接块里的一格。字阶与颜色取 M3 文字按钮的那一套（labelLarge + primary），
 * 它读起来就是个按钮，只是两块贴在一起而已。
 */
@Composable
private fun PhotoSourceAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = FreshNowSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 编辑表单本体，不含读库与落盘：便于按定值直接断言字段、照片与推算提示（见 RecordEditFormTest）。
 *
 * 照片排在最上面，与详情页的顺序一致；它是这条记录的一个字段，所以与四个文本字段同属一份草稿、
 * 同一次保存。
 *
 * 四个字段一律是文本框，不挂日期选择器：这里改的是「模型读错的地方」，存量值可能是任意写法
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxs)
        ) {
            ScanPhoto(
                image = uiState.image,
                onClick = onChangePhoto,
                onClickLabel = stringResource(R.string.record_image_change)
            )
            // 可编辑这件事要在画面上有交代：照片看起来只是个展示物，不给一句就没理由去点它。
            // 这行与文本框下面的 supporting text 是同一个槽位，同字阶同颜色
            Text(
                text = stringResource(R.string.record_image_change_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        EditField(
            value = uiState.productName,
            onValueChange = onProductNameChange,
            label = stringResource(R.string.scan_product_name)
        )
        EditField(
            value = uiState.productionDate,
            onValueChange = onProductionDateChange,
            label = stringResource(R.string.scan_production_date),
            error = if (uiState.productionDateInvalid) stringResource(R.string.record_edit_error_date) else null
        )
        EditField(
            value = uiState.shelfLife,
            onValueChange = onShelfLifeChange,
            label = stringResource(R.string.scan_shelf_life),
            error = if (uiState.shelfLifeInvalid) stringResource(R.string.record_edit_error_shelf_life) else null
        )
        EditField(
            value = uiState.expiryDate,
            onValueChange = onExpiryDateChange,
            label = stringResource(R.string.scan_expiry_date),
            error = if (uiState.expiryDateInvalid) stringResource(R.string.record_edit_error_date) else null,
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
 * 一个字段。[error] 是「这个写法认不出」，[hint] 是该字段自己的说明，两者共用同一个槽位
 * （规范：until the error is fixed, the error replaces the helper text）。
 *
 * 错误要等字段失焦才报，但已经写错的旧值一打开就报（见 [RecordEditForm]）。
 *
 * 输入法的「完成」也要算一次结束：本字段之后没有可跳的下一项，按「完成」只会收起键盘、
 * 不失焦，于是敲完一个认不出的值盯着屏幕看，什么都不会出现——那看起来就像这个字段不校验。
 */
@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val focusManager = LocalFocusManager.current
    var leftField by remember { mutableStateOf(false) }
    // 初值取「进来时就已写错」：这种值不是用户刚敲的，不说的话保存为什么灰着就没法解释
    var reported by remember { mutableStateOf(error != null) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    leftField = true
                } else if (leftField) {
                    reported = true
                }
            },
        label = { Text(text = label) },
        isError = reported && error != null,
        supportingText = when {
            reported && error != null -> {
                { Text(text = error) }
            }
            hint != null -> {
                { Text(text = hint) }
            }
            else -> null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    )
}

/** 过期日期留空时的说明：算得出就给日期，算不出就如实说推不出（哪个字段错了由那个字段自己报） */
@Composable
private fun derivedExpiryHint(derived: ExpiryOutcome): String = when (derived) {
    is ExpiryOutcome.Resolved -> stringResource(R.string.record_edit_expiry_hint, derived.date)
    ExpiryOutcome.InsufficientInput, ExpiryOutcome.UnparseableShelfLife ->
        stringResource(R.string.record_edit_expiry_hint_pending)
}
