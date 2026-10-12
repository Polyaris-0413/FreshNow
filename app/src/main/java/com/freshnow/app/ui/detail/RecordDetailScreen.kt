package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.PhotoViewer
import com.freshnow.app.ui.component.ScanPhoto
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
    val image = uiState.image
    // 看图是这一页内的一次状态切换，不是新的目的地：照片已经在内存里，走导航得把文件重读一遍。
    // 没有照片时也看不了图——那个位置本来就是占位图
    var viewingImage by remember { mutableStateOf(false) }
    // 交接处最容易出问题（退出动画中途被打断、状态没归零），进出都记一笔
    LaunchedEffect(viewingImage) {
        Log.d("PhotoViewer", "详情页 看图页可见=$viewingImage 有图=${image != null}")
    }
    // 系统返回只该退回记录页，而不是直接离开这一页，所以在这里拦一下
    BackHandler(enabled = viewingImage) { viewingImage = false }

    // 照片从它在详情页里的样子长到整屏（M3 的 container transform 那套）：两层用同一个键
    // 告诉框架说的是同一张图，位置与大小由它去补间；其余部分同时淡入淡出。
    // 时长取页内状态切换那一档（200ms）：它确实不是一次页面跳转。
    SharedTransitionLayout(modifier = modifier) {
        AnimatedContent(
            targetState = viewingImage && image != null,
            transitionSpec = { FreshNowTransitions.fadeSwap() },
            label = "recordPhoto"
        ) { showingViewer ->
            if (showingViewer && image != null) {
                FreshNowSubPage(
                    title = stringResource(R.string.record_image),
                    onBack = { viewingImage = false }
                ) { innerPadding ->
                    PhotoViewer(
                        image = image,
                        onDismiss = { viewingImage = false },
                        modifier = Modifier.padding(innerPadding),
                        imageModifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = PHOTO_KEY),
                            animatedVisibilityScope = this,
                            boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
                        )
                    )
                }
            } else {
                RecordDetailContent(
                    uiState = uiState,
                    image = image,
                    onBack = onBack,
                    onNavigateToEdit = onNavigateToEdit,
                    onOpenImage = { viewingImage = true },
                    photoModifier = Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = PHOTO_KEY),
                        animatedVisibilityScope = this,
                        boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
                    )
                )
            }
        }
    }
}

/** 记录本身那一屏。抽出来是为了让它与看图屏在 [AnimatedContent] 里各占一支 */
@Composable
private fun RecordDetailContent(
    uiState: RecordDetailUiState,
    image: Bitmap?,
    onBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onOpenImage: () -> Unit,
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
                ScanPhoto(
                    image = image,
                    modifier = photoModifier,
                    // 占位图不可点：那里没有图可看，能点开一张空图只会让人以为坏了
                    onClick = if (image != null) onOpenImage else null,
                    onClickLabel = if (image != null) stringResource(R.string.record_image_open) else null
                )

                FreshNowResultFields(
                    productName = record.productName,
                    productionDate = record.productionDate,
                    expiry = uiState.expiry,
                    shelfLife = record.shelfLife
                )
            }
        }
    }
}
