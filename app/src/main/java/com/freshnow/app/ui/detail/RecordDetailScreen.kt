package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    ScanPhoto(
                        image = image,
                        modifier = photoModifier,
                        // 占位图不可点：那里没有图可看，能点开一张空图只会让人以为坏了
                        onClick = if (image != null) onOpenImage else null,
                        onClickLabel = if (image != null) stringResource(R.string.record_image_open) else null
                    )

                    // 翻页的指引压在照片的左右边缘：原来这里是下面一排缩略图，它把正文往下挤了
                    // 一大截（用户反馈「挡住下面的卡片」）。箭头不占版面，同时把「左右也能翻」说出来。
                    // 手也挂在这一层：指哪都能滑
                    if (uiState.hasSeveralImages && image != null) {
                        PhotoPagingAffordance(
                            hasPrevious = uiState.mainIndex > 0,
                            hasNext = uiState.mainIndex < uiState.images.lastIndex,
                            onPrevious = { onSelectImage(uiState.mainIndex - 1) },
                            onNext = { onSelectImage(uiState.mainIndex + 1) },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                FreshNowResultFields(
                    productName = record.productName,
                    productionDate = record.productionDate,
                    expiry = uiState.expiry,
                    shelfLife = record.shelfLife,
                    // 开关关着时传 null 而不是空串：那一行的含义是「还没填」，而关着的时候
                    // 这一项根本不存在，不该占一行
                    location = record.location
                )
            }
        }
    }
}

/**
 * 主图上的翻页指引：左右各一枚半透明箭头（只在那一侧还有图时出现），整块都能横向滑。
 *
 * 箭头压在图上而不是排在下面：详情页的纵向位置得留给正文（用户反馈「挡住下面的卡片」）。
 * 箭头本身也可点：滑是给知道能滑的人的，箭头是给不知道的人的。
 */
@Composable
private fun PhotoPagingAffordance(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.pointerInput(hasPrevious, hasNext) {
            var travelled = 0f
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    travelled += amount
                },
                onDragEnd = {
                    val threshold = SWIPE_THRESHOLD.toPx()
                    when {
                        travelled <= -threshold && hasNext -> onNext()
                        travelled >= threshold && hasPrevious -> onPrevious()
                    }
                }
            )
        }
    ) {
        if (hasPrevious) {
            PagingArrow(
                forward = false,
                onClick = onPrevious,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        if (hasNext) {
            PagingArrow(
                forward = true,
                onClick = onNext,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * 一枚翻页箭头。
 *
 * 底取 scrim 半透明：M3 没有给「压在图片上的浮层」这一档，而 scrim 本来就是给内容之上的
 * 遮罩用的——纯色底会把照片盖掉一块，半透明才既看得见箭头又看得出底下有图。
 */
@Composable
private fun PagingArrow(forward: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(
        if (forward) R.string.record_image_next else R.string.record_image_previous
    )
    Box(
        modifier = modifier
            .padding(FreshNowSpacing.xs)
            .size(FreshNowSize.icon)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = ARROW_SCRIM_ALPHA))
            .clickable(onClick = onClick, onClickLabel = label)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .size(FreshNowSize.smallIcon)
                // 只有一枚返回箭头图标，往右那一侧把它翻过来
                .graphicsLayer { if (forward) scaleX = -1f }
        )
    }
}

/**
 * 横滑多少 dp 才算「翻一张」。
 *
 * M3 没给横向翻页的手势阈值（它规定的是拖拽与滑动退出这类），这里取 48dp：与 M3 的最小触摸
 * 目标同一档，手指走过这一段必然是有意的。
 */
private val SWIPE_THRESHOLD = 48.dp

/** 箭头底的不透明度。M3 没有这一档，取到「看得出底下有图、箭头又清楚」为止 */
private const val ARROW_SCRIM_ALPHA = 0.32f
