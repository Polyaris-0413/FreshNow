package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.RecordField
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.LeaveDialog
import com.freshnow.app.ui.component.PhotoActionsSheet
import com.freshnow.app.ui.component.PhotoSourceSheet
import com.freshnow.app.ui.component.PhotoViewerPage
import com.freshnow.app.ui.component.StepDirection
import com.freshnow.app.ui.component.StepTransition
import com.freshnow.app.ui.component.photoKey
import com.freshnow.app.ui.component.photoThumbnail
import com.freshnow.app.ui.component.PhotoGridItem
import com.freshnow.app.ui.component.RecordEditForm
import com.freshnow.app.ui.component.hideSheetThen
import com.freshnow.app.ui.scan.PhotoCaptureScreen
import com.freshnow.app.ui.showToast
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTransitions
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
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
    val saveFailedToast = stringResource(R.string.record_image_save_failed)

    // 换照片的来源选择是本页的状态，不进 ViewModel：它只是「面板开着吗」，不需要跨进程重建
    var showImageSources by remember { mutableStateOf(false) }
    // 有改动时返回先问一句（见 LeaveDialog），没改动就直接退
    var showLeaveDialog by remember { mutableStateOf(false) }
    // 长按了哪一张：先起一片面板问一句（见 PhotoActionsSheet）
    var actedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    val photoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 点开看大图的那一张。关闭时只把开关放下，记着的是哪一张照片（不是第几格）：设首图会重排，
    // 按下标记的话，同一个共享元素的键会从一张图跳到另一张上（进页时闪一下）
    var viewingPhoto by remember { mutableStateOf(false) }
    var viewedPhotoId by remember { mutableStateOf<Long?>(null) }
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

    // 有改动才问一句（与扫描页「有结果才问」同一条规矩，判据见 [isDirtyAgainst]）。
    // 顶栏返回箭头与系统返回键走同一套判断，否则箭头会绕过这里静默丢掉改动
    val hasUnsavedChanges = viewModel.hasUnsavedChanges()
    fun requestLeave() = if (hasUnsavedChanges) showLeaveDialog = true else onBack()

    // 照片写盘失败也照旧退回去：其余字段已经存好了，而提示只说照片这一件事，
    // 留在本页只会让人以为整个保存都失败了。顶栏那颗保存与弹窗里那颗共用这一下
    fun saveAndLeave() {
        viewModel.save { imageSaved ->
            if (!imageSaved) showToast(context, saveFailedToast)
            onBack()
        }
    }

    // 三个整屏步骤之间的转场（见 [StepTransition]）：回到表单是退回方向，从表单出去是前进方向，
    // 而裁剪与拍照之间留一段淡入淡出——它们不会直接互换，但也不该硬切
    //
    // 整页包在 [SharedTransitionLayout] 里，与详情页同一个结构：格子与看图页必须同时在这一个过渡
    // 范围内，图才能从格子那块矩形飞到全屏
    SharedTransitionLayout(modifier = modifier) {
    StepTransition(
        step = step,
        direction = { from, to ->
            when {
                to is EditStep.Edit -> StepDirection.BACK
                from is EditStep.Edit -> StepDirection.FORWARD
                else -> StepDirection.SIDE
            }
        },
        label = "editStep",
        modifier = Modifier.fillMaxSize()
    ) { current ->
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

            EditStep.Edit -> {
                // 看图页开着时让位：两个 BackHandler 靠先后顺序抢返回键是不可靠的
                // （StepTransition 重组时回调会重新注册，位置会变），所以两边显式互斥
                BackHandler(enabled = hasUnsavedChanges && !viewingPhoto) { showLeaveDialog = true }
                EditForm(
                    uiState = uiState,
                    onBack = ::requestLeave,
                    onSave = ::saveAndLeave,
                    onFieldChange = viewModel::onFieldChange,
                    onOpenPhoto = { index ->
                        val photo = uiState.photos.getOrNull(index)
                        // 没有位图的那一格（读失败）无图可看，保持原位不动
                        if (photo?.bitmap != null) {
                            viewedPhotoId = photo.id
                            viewingPhoto = true
                        }
                    },
                    onPhotoActions = { actedPhotoIndex = it },
                    onAddPhoto = { showImageSources = true },
                    // 每格都挂着共享元素，键跟着照片自己的 id 走——于是设首图交换位置时，键仍然属于
                    // 同一张图，不会出现「同一个键换了个节点」而让框架飞一下。
                    // 让出（visible = false）的只有正在看的那一张
                    photoTileModifier = { index ->
                        val photo = uiState.photos.getOrNull(index)
                        if (photo == null) {
                            Modifier
                        } else {
                            photoThumbnail(
                                key = photoKey(photo.id),
                                visible = !(viewingPhoto && photo.id == viewedPhotoId)
                            )
                        }
                    }
                )
            }
        }
    }

    // 看图页与格子在同一个过渡里。关闭时 viewedPhotoId 仍指着那张图，淡出才有内容可淡
    AnimatedVisibility(
        visible = viewingPhoto,
        enter = fadeIn(FreshNowTransitions.stateChange()),
        exit = fadeOut(FreshNowTransitions.stateChange())
    ) {
        val viewed = uiState.photos.firstOrNull { it.id == viewedPhotoId }
        val image = viewed?.bitmap
        if (viewed != null && image != null) {
            PhotoViewerPage(
                image = image,
                key = photoKey(viewed.id),
                // 关闭时让出共享元素，图才能飞回格子
                visible = viewingPhoto,
                onDismiss = { viewingPhoto = false }
            )
        }
    }
    }

    // 系统返回先关看图页：否则一次返回会把没保存的草稿一起带走，还一句都不问
    BackHandler(enabled = viewingPhoto) { viewingPhoto = false }

    val actedPhoto = actedPhotoIndex
    if (actedPhoto != null) {
        PhotoActionsSheet(
            sheetState = photoSheetState,
            // 已经是第一张就不给「用作首图」：那一项按下去什么都不会变
            canSetFirst = actedPhoto != 0,
            onDismissRequest = { actedPhotoIndex = null },
            // 收起动画由面板自己等完再回调（见 PhotoActionsSheet），这里只负责改哪一张
            onSetFirst = { viewModel.onPhotoMovedToFront(actedPhoto) },
            onRemove = { viewModel.onPhotoRemoved(actedPhoto) }
        )
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

    if (showLeaveDialog) {
        LeaveDialog(
            title = stringResource(R.string.record_leave_dialog_title),
            message = stringResource(R.string.record_leave_dialog_message),
            // 一项都填不出来、或日期认不出时不给一颗按下去也写不进库的保存（表单那颗也是置灰的）
            canSave = uiState.canSave,
            onSave = {
                showLeaveDialog = false
                saveAndLeave()
            },
            onDiscard = {
                showLeaveDialog = false
                onBack()
            },
            onDismiss = { showLeaveDialog = false }
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
    onSave: () -> Unit,
    onFieldChange: (RecordField, String) -> Unit,
    onOpenPhoto: (Int) -> Unit,
    onPhotoActions: (Int) -> Unit,
    onAddPhoto: () -> Unit,
    photoTileModifier: @Composable (Int) -> Modifier
) {
    val focusManager = LocalFocusManager.current

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
                    onClick = onSave,
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

        // 表单比一屏长（九宫格 + 五项），必有一项落在折叠线以下，所以这一页得能滚。
        // 【刻意不放底边渐隐】它压在下面的输入框上时，看着像「输入框被遮了一半」（用户反馈）；
        // 而扫描页那一份表单（同一个组件）本来就没有它，去掉之后两处一致。
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
                onFieldChange = onFieldChange,
                onOpenPhoto = onOpenPhoto,
                onPhotoActions = onPhotoActions,
                onAddPhoto = onAddPhoto,
                // 只有被点开的那一格接共享元素：其余格子没参与这次飞，挂上去只会争同一个键
                photoTileModifier = photoTileModifier,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(formScroll)
                    .padding(FreshNowSpacing.sm)
            )
        }
    }
}

