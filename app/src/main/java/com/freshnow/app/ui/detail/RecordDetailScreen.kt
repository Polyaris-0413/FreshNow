package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.ScanPhoto
import com.freshnow.app.ui.component.ScrollEdgeFade
import com.freshnow.app.ui.theme.FreshNowSpacing

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
        },
        modifier = modifier
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
            val resultFields: @Composable () -> Unit = {
                FreshNowResultFields(
                    productName = record.productName,
                    productionDate = record.productionDate,
                    expiry = uiState.expiry,
                    shelfLife = record.shelfLife
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(FreshNowSpacing.sm)
            ) {
                // 与扫描页同一条判据（见 ScanScreen）：宽不小于高时高度才是稀缺资源。
                // 上下排会把正方形照片撑成远超可视区的高条，信息被顶到屏幕外；
                // 改为左右并排，照片按可用高度取正方形，信息在右栏单独滚动。
                if (maxWidth >= maxHeight) {
                    // 先取出边长：进了 Row 之后，这一作用域的成员会被 RowScope 挡住
                    val photoSide = maxHeight
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                    ) {
                        // 与竖屏同一条规则，只是取宽度的方向换了：宽度由可用高度决定。
                        // 照片本身就落成正方形（见 ScanImageCodec），有图时自然撑成与高度等大的正方形；
                        // 没图时占位块保持自己那点高度，不会被拉成一个大方块
                        ScanPhoto(
                            image = uiState.image,
                            modifier = Modifier.width(photoSide)
                        )
                        // 右栏单独滚动，与扫描页同款：内容滑出折叠线时底边渐隐，提示「下面还有」
                        val fieldsScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(fieldsScroll)
                            ) {
                                resultFields()
                            }
                            ScrollEdgeFade(
                                visible = fieldsScroll.canScrollForward,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
                    ) {
                        ScanPhoto(image = uiState.image)
                        resultFields()
                    }
                }
            }
        }
    }
}
