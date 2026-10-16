package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.PhotoViewer
import com.freshnow.app.ui.component.ScanPhoto
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTransitions

/** 共享元素的键：详情页与看图页里是同一张照片，动画要认出它 */
private const val PHOTO_KEY = "recordPhoto"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecordDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordDetailViewModel = viewModel()
) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val images = uiState.images
    val mainIndex = uiState.mainIndex
    // 看图是这一页内的一次状态切换，不是新的目的地：照片已经在内存里，走导航得把文件重读一遍。
    // 没有照片时也看不了图——那个位置本来就是占位图。
    // 记的是「从哪一张进去的」而不是一个布尔值：全屏里能左右翻，而共享元素只该挂在进来的那一张上
    var viewingImageIndex by remember { mutableStateOf<Int?>(null) }
    // 交接处最容易出问题（退出动画中途被打断、状态没归零），进出都记一笔
    LaunchedEffect(viewingImageIndex) {
        Log.d("PhotoViewer", "详情页 看图页可见=${viewingImageIndex != null} 有图=${images.isNotEmpty()}")
    }
    // 系统返回只该退回记录页，而不是直接离开这一页，所以在这里拦一下
    BackHandler(enabled = viewingImageIndex != null) { viewingImageIndex = null }

    // 详情页常驻，看图页作为覆盖层进出：**同一个实例**进出，快速连点最多是把这段过渡反向，
    // 不可能出现「退场与进场同时进行」——重叠时框架会把上一段留在屏幕上（实测：滑走后 200~300ms
    // 内再点一下，图就卡在屏幕角落，且那个僵尸页几秒后仍在收手势）。
    // 顺带把「退场要重建整页」的合成成本也省了（之前实测退出那一刻有一帧 80~100ms）。
    SharedTransitionLayout(modifier = modifier) {
        RecordDetailContent(
            uiState = uiState,
            onBack = onBack,
            onNavigateToEdit = onNavigateToEdit,
            onOpenImage = { viewingImageIndex = mainIndex },
            onSelectImage = viewModel::showImage,
            photoModifier = Modifier.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(key = PHOTO_KEY),
                visible = viewingImageIndex == null,
                boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
            )
        )

        AnimatedVisibility(
            visible = viewingImageIndex != null && images.isNotEmpty(),
            enter = fadeIn(FreshNowTransitions.stateChange()),
            exit = fadeOut(FreshNowTransitions.stateChange())
        ) {
            val startIndex = viewingImageIndex
            if (startIndex != null) {
                FreshNowSubPage(
                    title = stringResource(R.string.record_image),
                    onBack = { viewingImageIndex = null }
                ) { innerPadding ->
                    PhotoViewer(
                        images = images,
                        startIndex = startIndex,
                        onDismiss = { viewingImageIndex = null },
                        modifier = Modifier.padding(innerPadding),
                        // 共享元素只挂在进来那一张上：翻到别的图时那一张没有对应的节点，
                        // 还挂着只会让退场动画认错矩形
                        imageModifier = { index ->
                            if (index == startIndex) {
                                Modifier.sharedElementWithCallerManagedVisibility(
                                    sharedContentState = rememberSharedContentState(key = PHOTO_KEY),
                                    visible = true,
                                    boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
                                )
                            } else {
                                Modifier
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 记录本身那一屏。它常驻：看图页是盖在它上面的覆盖层 */
@Composable
private fun RecordDetailContent(
    uiState: RecordDetailUiState,
    onBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onOpenImage: () -> Unit,
    onSelectImage: (Int) -> Unit,
    photoModifier: Modifier = Modifier
) {
    FreshNowSubPage(
        title = stringResource(R.string.record_detail_title),
        onBack = onBack,
        actions = {
            // 记录读不到时不给编辑入口：没有可改的东西
            if (uiState.record != null) {
                TextButton(onClick = onNavigateToEdit) {
                    Text(text = stringResource(R.string.action_edit))
                }
            }
        }
    ) { innerPadding ->
        val record = uiState.record
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.loaded) {
                    Text(
                        text = stringResource(R.string.record_detail_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(FreshNowSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
            ) {
                val image = uiState.mainImage
                ScanPhoto(
                    image = image,
                    modifier = photoModifier,
                    // 占位图不可点：那里没有图可看，能点开一张空图只会让人以为坏了
                    onClick = if (image != null) onOpenImage else null,
                    onClickLabel = if (image != null) stringResource(R.string.record_image_open) else null
                )

                // 多于一张时才给这一排：一张时主图就是它，再画一排等大的缩略图只是把同一张图说两遍
                if (uiState.showsThumbnails) {
                    PhotoThumbnailRow(
                        images = uiState.images,
                        mainIndex = uiState.mainIndex,
                        onSelect = onSelectImage
                    )
                }

                FreshNowResultFields(
                    productName = record.productName,
                    productionDate = record.productionDate,
                    expiry = uiState.expiry,
                    shelfLife = record.shelfLife,
                    // 开关关着时传 null 而不是空串：那一行的含义是「还没填」，而关着的时候
                    // 这一项根本不存在，不该占一行
                    location = if (uiState.supplementEnabled) record.location else null
                )
            }
        }
    }
}

/**
 * 主图下面那一排缩略图：点哪张，上面就换成哪张。
 *
 * 一排而不是九宫格：详情页这里是「换看哪一张」，不是「管理这几张」（增删在编辑页），
 * 横着滑过去就够了，也不必纵向占掉正文的地方。
 *
 * 当前那张用一圈描边标出来：没有它，「点的这张和上面那张是不是同一张」只能靠认图。
 */
@Composable
private fun PhotoThumbnailRow(
    images: List<Bitmap>,
    mainIndex: Int,
    onSelect: (Int) -> Unit
) {
    val shape = MaterialTheme.shapes.extraSmall
    LazyRow(horizontalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)) {
        itemsIndexed(images) { index, bitmap ->
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = stringResource(R.string.record_image_view),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(FreshNowSize.thumbnail)
                    .clip(shape)
                    .then(
                        if (index == mainIndex) {
                            Modifier.border(
                                width = FreshNowSize.selectionOutlineWidth,
                                color = MaterialTheme.colorScheme.primary,
                                shape = shape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) }
            )
        }
    }
}
