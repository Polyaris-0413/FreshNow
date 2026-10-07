package com.freshnow.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.component.FreshNowTopAppBar
import com.freshnow.app.ui.component.ScanThumbnail
import com.freshnow.app.ui.component.scanValueText
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTheme
import com.freshnow.app.ui.theme.warningColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun HomeRoute(
    onNavigateToScan: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecord: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()

    HomeScreen(
        records = records,
        onRecordClick = onNavigateToRecord,
        onNavigateToScan = onNavigateToScan,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    records: List<HomeRecordItem>,
    onRecordClick: (Long) -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }

    // 先收起 bottom sheet 再跳转，避免收起动画与导航互相打断
    fun dismissSheetThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                showSheet = false
                action()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FreshNowTopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = { showSheet = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.action_more_options)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToScan) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.action_add)
                )
            }
        }
    ) { innerPadding ->
        RecordsList(
            records = records,
            onRecordClick = onRecordClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FreshNowSpacing.sm)
                    .padding(bottom = FreshNowSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xs)
            ) {
                SheetMenuItem(
                    text = stringResource(R.string.about),
                    onClick = { dismissSheetThen(onNavigateToAbout) }
                )
                SheetMenuItem(
                    text = stringResource(R.string.settings),
                    onClick = { dismissSheetThen(onNavigateToSettings) }
                )
            }
        }
    }
}

/**
 * 单个条目自成圆角框；两个条目结构相同，尺寸因此一致
 */
@Composable
private fun SheetMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = FreshNowSpacing.sm),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecordsList(
    records: List<HomeRecordItem>,
    onRecordClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.home_records_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val today = rememberToday()
    LazyColumn(modifier = modifier) {
        items(records, key = { it.record.id }) { item ->
            RecordRow(
                item = item,
                today = today,
                onClick = { onRecordClick(item.record.id) }
            )
        }
    }
}

/**
 * 今天的日期，跨过零点会自己更新。
 *
 * 剩余天数是按当天算的，「今天」若固化成常量，应用停在列表上过夜后数字就是错的。
 */
@Composable
private fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val nextMidnight = today.plusDays(1).atStartOfDay()
            val untilMidnight = Duration.between(LocalDateTime.now(), nextMidnight).toMillis()
            // 兜底一秒，系统时间被往回拨时不会变成空转
            delay(untilMidnight.coerceAtLeast(1_000L))
            today = LocalDate.now()
        }
    }
    return today
}

/** 「快过期」的阈值：剩余天数不超过它就走警示色 */
private const val EXPIRING_SOON_DAYS = 3L

@Composable
private fun RecordRow(
    item: HomeRecordItem,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val days = ExpiryCalculator.daysRemaining(item.expiry, today)

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { ScanThumbnail(image = item.image) },
        headlineContent = { Text(text = scanValueText(item.record.productName)) },
        supportingContent = {
            Text(
                text = expiryCountdownText(days),
                // 三档：已过期用错误色，快过期用警示色（M3 没有 warning 角色，见 FreshNowWarningColors），
                // 其余保持次要文字色。「算不出天数」不参与分档——不知道就不假装知道
                color = when {
                    days == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    days < 0 -> MaterialTheme.colorScheme.error
                    days <= EXPIRING_SOON_DAYS -> MaterialTheme.warningColors.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** 剩余天数写成一句话：算不出就用「未知」，不假装知道 */
@Composable
private fun expiryCountdownText(days: Long?): String = when {
    days == null -> stringResource(R.string.home_expiry_unknown)
    days > 0 -> stringResource(R.string.home_expiry_days_left, days)
    days == 0L -> stringResource(R.string.home_expiry_today)
    else -> stringResource(R.string.home_expiry_expired, -days)
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    FreshNowTheme {
        HomeScreen(
            records = listOf(
                ScanRecord(
                    id = 1,
                    productName = "纯牛奶",
                    productionDate = "2025-01-01",
                    expiryDate = "",
                    shelfLife = "18个月",
                    imageName = "",
                    savedAt = 0
                ),
                ScanRecord(
                    id = 2,
                    productName = "",
                    productionDate = "2024-01-01",
                    expiryDate = "",
                    shelfLife = "保质期见喷码",
                    imageName = "",
                    savedAt = 0
                )
            ).map { record ->
                HomeRecordItem(
                    record = record,
                    expiry = ExpiryCalculator.resolve(
                        printedExpiry = record.expiryDate,
                        productionDate = record.productionDate,
                        shelfLife = record.shelfLife
                    ),
                    image = null
                )
            },
            onRecordClick = {},
            onNavigateToScan = {},
            onNavigateToAbout = {},
            onNavigateToSettings = {}
        )
    }
}
