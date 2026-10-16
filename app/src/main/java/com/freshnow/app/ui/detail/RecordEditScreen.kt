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
import androidx.compose.animation.AnimatedVisibility
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
import com.freshnow.app.ui.component.PhotoSourceSheet
import com.freshnow.app.ui.component.MenuSheetItem
import com.freshnow.app.ui.component.PhotoGrid
import com.freshnow.app.ui.component.PhotoGridItem
import com.freshnow.app.ui.component.RecordEditForm
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
                onRemovePhoto = viewModel::onPhotoRemoved,
                onMovePhoto = viewModel::onPhotoMoved,
                onAddPhoto = { showImageSources = true },
                viewModel = viewModel
            )
        }
    }

    if (showImageSources) {
        PhotoSourceSheet(
            sheetState = imageSheetState,
            onDismissRequest = { showImageSources = false },
            onPickFromGallery = {
                dismissSheetThen {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onTakePhoto = { dismissSheetThen { showCamera = true } }
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
    onRemovePhoto: (Int) -> Unit,
    onMovePhoto: (from: Int, to: Int) -> Unit,
    onAddPhoto: () -> Unit,
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
                fields = uiState.toFormFields(),
                photos = uiState.photos.map { PhotoGridItem(key = it.id.toString(), image = it.bitmap) },
                derivedExpiry = uiState.derivedExpiry,
                onFieldChange = viewModel::onFieldChange,
                onRemovePhoto = onRemovePhoto,
                onMovePhoto = onMovePhoto,
                onAddPhoto = onAddPhoto,
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

