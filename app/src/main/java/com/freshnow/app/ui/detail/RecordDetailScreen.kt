package com.freshnow.app.ui.detail

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip



import androidx.compose.ui.res.stringResource


import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.PhotoViewer
import com.freshnow.app.ui.component.ScanPhoto
import com.freshnow.app.ui.theme.OnOverImageScrim
import com.freshnow.app.ui.theme.OverImageScrim
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
                        // 全屏只看点开的那一张：主图区已经能左右滑了，全屏里再能滑只会让人分不清
                        // 「现在看的是哪一张」（用户反馈）
                        image = images[startIndex],
                        onDismiss = { viewingImageIndex = null },
                        modifier = Modifier.padding(innerPadding),
                        imageModifier = Modifier.sharedElementWithCallerManagedVisibility(
                            sharedContentState = rememberSharedContentState(key = PHOTO_KEY),
                            visible = true,
                            boundsTransform = { _, _ -> FreshNowTransitions.fastSpatial() }
                        )
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
                val images = uiState.images
                val pagerState = rememberPagerState(
                    initialPage = uiState.mainIndex,
                    pageCount = { images.size }
                )
                // 滑到哪一张就报给上层：主图下标归 ViewModel（旋转重建之后还在那一张上）
                LaunchedEffect(pagerState.settledPage) {
                    if (pagerState.settledPage != uiState.mainIndex) {
                        onSelectImage(pagerState.settledPage)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (images.isEmpty()) {
                        // 一张都没有时是占位图，且点不开看图页（那里没图可看）
                        ScanPhoto(image = null, modifier = photoModifier)
                    } else {
                        // 主图自己做横向翻页：照片本来就该能左右滑着看，而滑动是跟手的
                        HorizontalPager(
                            state = pagerState,
                            // 一页一张。页与页之间留一格缝：滑到两张中间时看得出这是两张
                            pageSpacing = FreshNowSpacing.xs,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            ScanPhoto(
                                image = images[page],
                                // 共享元素只挂在当前那一页上：每页都挂的话，进出看图时框架认不出能动的是哪一块
                                modifier = if (page == pagerState.currentPage) photoModifier else Modifier,
                                onClick = onOpenImage,
                                onClickLabel = stringResource(R.string.record_image_open)
                            )
                        }
                    }

                    // 页码压在照片右下角：不占正文的版面，同时把「共有几张、现在第几张」说出来——
                    // 少了它，用户不知道那边还能滑（底下那排缩略图就是为这个加的，但它把正文挤下去了）
                    if (images.size > 1) {
                        Text(
                            text = stringResource(
                                R.string.record_image_page,
                                pagerState.currentPage + 1,
                                images.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnOverImageScrim,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(FreshNowSpacing.xs)
                                .clip(MaterialTheme.shapes.small)
                                .background(OverImageScrim)
                                .padding(
                                    horizontal = FreshNowSpacing.xs,
                                    vertical = FreshNowSpacing.xxs
                                )
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

