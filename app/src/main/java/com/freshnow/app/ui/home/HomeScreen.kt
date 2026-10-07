package com.freshnow.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import com.freshnow.app.ui.theme.FreshNowSize
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
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    HomeScreen(
        records = records,
        selectedIds = selectedIds,
        onRecordClick = onNavigateToRecord,
        onRecordToggle = viewModel::toggleSelection,
        onExitSelection = viewModel::clearSelection,
        onDeleteSelected = viewModel::deleteSelected,
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
    selectedIds: Set<Long>,
    onRecordClick: (Long) -> Unit,
    onRecordToggle: (Long) -> Unit,
    onExitSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 选择模式由「有没有选中项」决定，不另存一个开关，见 HomeViewModel.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    // 选择模式是个临时状态，返回键先离开它，而不是直接退出应用
    BackHandler(enabled = inSelectionMode) { onExitSelection() }

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
                title = if (inSelectionMode) {
                    stringResource(R.string.home_selected_count, selectedIds.size)
                } else {
                    stringResource(R.string.app_name)
                },
                navigationIcon = {
                    if (inSelectionMode) {
                        IconButton(onClick = onExitSelection) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_exit_selection)
                            )
                        }
                    }
                },
                actions = {
                    if (inSelectionMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    } else {
                        IconButton(onClick = { showSheet = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.action_more_options)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // 选择模式下不给「添加」：它和当前这件事无关，浮在选中项上只会挡住列表和删除按钮
            if (!inSelectionMode) {
                FloatingActionButton(onClick = onNavigateToScan) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.action_add)
                    )
                }
            }
        }
    ) { innerPadding ->
        RecordsList(
            records = records,
            selectedIds = selectedIds,
            inSelectionMode = inSelectionMode,
            onRecordClick = onRecordClick,
            onRecordToggle = onRecordToggle,
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

    // 删除不可恢复，问一次再动手。跟着选择模式一起收场：选中项若已被清空，这个对话框就没有意义了
    if (showDeleteDialog && inSelectionMode) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(text = stringResource(R.string.home_delete_dialog_title, selectedIds.size))
            },
            text = { Text(text = stringResource(R.string.home_delete_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteSelected()
                    },
                    // 这里点下去东西就没了，用错误色把它和普通的「确定」区分开
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
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
    selectedIds: Set<Long>,
    inSelectionMode: Boolean,
    onRecordClick: (Long) -> Unit,
    onRecordToggle: (Long) -> Unit,
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
                selected = item.record.id in selectedIds,
                // 选择模式里点按是切换选中：此时点一下是为了多选一条，
                // 若照旧进详情，想加选就会被迫跳走
                onClick = {
                    if (inSelectionMode) onRecordToggle(item.record.id) else onRecordClick(item.record.id)
                },
                onLongClick = { onRecordToggle(item.record.id) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(
    item: HomeRecordItem,
    today: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val days = ExpiryCalculator.daysRemaining(item.expiry, today)

    // 选中态用主色描边表示。ListItem 本身没有选中外观，而这里「选中」的含义是「待删除」，
    // 既然删除是不可逆的，就不借用「快过期」那套状态色——那是提醒临期，与此无关。
    // 描边由 Modifier.border 画在行的边界之内，不占布局，选中前后条目不会跳动。
    val selectionOutline = if (selected) {
        Modifier.border(
            width = FreshNowSize.selectionOutlineWidth,
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.extraSmall
        )
    } else {
        Modifier
    }

    ListItem(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(selectionOutline),
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

@Preview(showBackground = true, name = "列表")
@Composable
private fun HomeScreenPreview() {
    FreshNowTheme {
        HomeScreen(
            records = previewRecords(),
            selectedIds = emptySet(),
            onRecordClick = {},
            onRecordToggle = {},
            onExitSelection = {},
            onDeleteSelected = {},
            onNavigateToScan = {},
            onNavigateToAbout = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true, name = "选择模式")
@Composable
private fun HomeScreenSelectionPreview() {
    FreshNowTheme {
        HomeScreen(
            records = previewRecords(),
            selectedIds = setOf(1L),
            onRecordClick = {},
            onRecordToggle = {},
            onExitSelection = {},
            onDeleteSelected = {},
            onNavigateToScan = {},
            onNavigateToAbout = {},
            onNavigateToSettings = {}
        )
    }
}

private fun previewRecords(): List<HomeRecordItem> = listOf(
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
    ),
    ScanRecord(
        id = 3,
        productName = "酸奶",
        productionDate = "",
        expiryDate = "2025-06-01",
        shelfLife = "",
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
}
