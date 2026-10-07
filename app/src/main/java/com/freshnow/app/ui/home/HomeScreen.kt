package com.freshnow.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import com.freshnow.app.ui.theme.FreshNowTransitions
import com.freshnow.app.ui.theme.countRoll
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
    val newRecordIds by viewModel.newRecordIds.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    // 离开本页就清掉「刚存进来的」这份标记：再回来时重新合成的还是同一批记录，不该再演一遍。
    // 清在这里而不是合成里，是因为此刻那些条目已经不存在了，清早了也漏不掉谁
    DisposableEffect(Unit) {
        onDispose { viewModel.onRecordsShown() }
    }

    HomeScreen(
        // 第一次查库还没回来时是 null：列表位置先空着，等数据到了才合成并淡入。
        // 不把 null 与「确实一条都没有」都折成空列表，否则冷启动会先给一句
        // 「还没有扫描记录」再把记录硬切上来，中途换一次结论
        records = records,
        newRecordIds = newRecordIds,
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
    records: List<HomeRecordItem>?,
    /** 刚存进来、还没演过淡入的那几条，见 HomeViewModel.newRecordIds */
    newRecordIds: Set<Long>,
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

    // 顶栏的两副面孔用一个可空计数表示：null 是普通列表，非 null 是选择模式及其计数。
    //
    // 计数放进状态里，而不是在选择模式那一支里现读 selectedIds.size：AnimatedContent 按状态
    // 缓存内容，退出选择模式时被淡出的那条顶栏拿到的仍是它自己的计数，会停在「已选 1 项」上淡出。
    // 若现读，它会跟着最新的选中数量先滚一遍「已选 0 项」再淡出，同一件事演两遍。
    val selectionCount: Int? = selectedIds.size.takeIf { inSelectionMode }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 顶栏是「列表」与「选择模式」两副面孔，直接换会很生硬，整条一起淡入淡出。
            // 整条换而不是分开淡标题与图标：选择模式下标题左边多出退出按钮、标题本身要右移，
            // 分开淡会让它在两个位置之间跳。
            AnimatedContent(
                targetState = selectionCount,
                // 内容按「是不是选择模式」复用：计数在自己的小框里滚（见 SelectionCountTitle），
                // 整条顶栏只在进出选择模式时切换
                contentKey = { it != null },
                transitionSpec = { FreshNowTransitions.fadeSwap() },
                label = "topBar"
            ) { count ->
                if (count != null) {
                    FreshNowTopAppBar(
                        title = { SelectionCountTitle(count = count) },
                        navigationIcon = {
                            IconButton(onClick = onExitSelection) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.action_exit_selection)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.action_delete)
                                )
                            }
                        }
                    )
                } else {
                    FreshNowTopAppBar(
                        title = { Text(text = stringResource(R.string.app_name)) },
                        actions = {
                            IconButton(onClick = { showSheet = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.action_more_options)
                                )
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            // 选择模式下不给「添加」：它和当前这件事无关，浮在选中项上只会挡住列表和删除按钮。
            // 与顶栏同一步调淡出，否则顶栏在淡、它在"啪"地消失
            AnimatedVisibility(
                visible = !inSelectionMode,
                enter = fadeIn(FreshNowTransitions.stateChange()),
                exit = fadeOut(FreshNowTransitions.stateChange())
            ) {
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
            newRecordIds = newRecordIds,
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
        // M3 把拖动手柄整个槽（一块 32×48 的触摸区）包了一层不带形状的 clickable，涟漪于是按那块
        // 矩形铺开，与里面 4dp 的胶囊完全不是一回事。手柄这里本来也不需要按压反馈——「点它收起
        // 面板」没有歧义——所以直接关掉：给 `LocalRippleConfiguration` 传 null 就是 M3 为这件事留的
        // 开关（见 Ripple.kt 的 KDoc）。手柄的点击、长按提示与无障碍语义都不受影响。
        val defaultRippleConfiguration = LocalRippleConfiguration.current

        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState
            ) {
                // 面板内部把默认配置恢复回去，否则两张菜单卡片的按压反馈也会一起被关掉
                CompositionLocalProvider(
                    LocalRippleConfiguration provides defaultRippleConfiguration
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
 * 选中计数。前后两段文字不动，只有中间的数字滚——整句一起滚会把「已选」「项」也带着动，
 * 看着像标题在抖，而这一屏里真正在变的只有数字。
 *
 * 三段文字合并成一个语义节点，否则读屏软件会读成「已选」「2」「项」三截。
 */
@Composable
private fun SelectionCountTitle(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(R.string.home_selected_count_prefix))
        AnimatedContent(
            targetState = count,
            transitionSpec = { countRoll() },
            label = "selectedCount"
        ) { current ->
            Text(text = current.toString())
        }
        Text(text = stringResource(R.string.home_selected_count_suffix))
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
    records: List<HomeRecordItem>?,
    newRecordIds: Set<Long>,
    selectedIds: Set<Long>,
    inSelectionMode: Boolean,
    onRecordClick: (Long) -> Unit,
    onRecordToggle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = rememberToday()

    // 第一次查库还没回来，先不占位：此刻还不知道该不该显示「还没有扫描记录」
    val latest = records ?: return

    // 刚存进来的那几条慢一帧才进列表。
    //
    // LazyLayout 的位移比的是"上一轮的键表"：一次合成里就把新记录摆到最终位置，它手里没有可
    // 比较的过去，只会直接画在预期位置上——位移因此演不出来，看着就是"不走路地出现在该在的地方"。
    // 先只摆上一批（新记录已经在缓存里时，这一批就是用户上一条看到的内容），下一帧再放新的，
    // 于是"其余条目从旧位置滑到新位置"这件事有了旧位置可比；新记录自己的淡入照旧由 appearFadeIn 演。
    var placedIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(latest, newRecordIds) {
        withFrameNanos { }
        placedIds = newRecordIds
    }
    val shown = latest.filterNot { it.record.id in newRecordIds && it.record.id !in placedIds }

    // 增删与位移的时长取页内状态切换（见 FreshNowTransitions.stateChange）：删除时选中框也按
    // 同一时长淡出（见 RecordRow），两者同时收尾，不会一个已经没了另一个还在淡。
    //
    // 空态也摆成一个条目，而不是把整条列表换成居中的文字框：删掉最后一条时列表若被换下去，
    // 那条记录会跟着整棵子树一起消失，淡出根本来不及演。
    LazyColumn(modifier = modifier) {
        // 判据用 latest 而不是 shown：新记录还没落地的那一帧列表是空的，但数据并不空，
        // 这时候不该闪一句「还没有扫描记录」
        if (latest.isEmpty()) {
            item(key = EMPTY_LIST_KEY) {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        // 空态文案每次出现都淡入一次：它只在这一个条目里，没有滚动可言，
                        // 每次列表变空都是一次新的出现
                        .appearFadeIn()
                        .animateItem(
                            // 出现的淡入自己演（见 appearFadeIn），animateItem 那条通道只在
                            // 「列表已在场、条目后加入」时认得出来，覆盖不到首次合成
                            fadeInSpec = null,
                            placementSpec = FreshNowTransitions.stateChange(),
                            fadeOutSpec = FreshNowTransitions.stateChange()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_records_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(shown, key = { it.record.id }) { item ->
            RecordRow(
                item = item,
                today = today,
                selected = item.record.id in selectedIds,
                // 选择模式里点按是切换选中：此时点一下是为了多选一条，
                // 若照旧进详情，想加选就会被迫跳走
                onClick = {
                    if (inSelectionMode) onRecordToggle(item.record.id) else onRecordClick(item.record.id)
                },
                onLongClick = { onRecordToggle(item.record.id) },
                // 被删的条目停在原位淡出，其余的滑到新位置。key 已经在 items 上给好，
                // 谁走了谁留下由它认领，这里只负责把过程演出来
                modifier = Modifier
                    // 刚存进来的那几条自己淡入；已经在列表里的条目不动，跟着整页出现
                    .appearFadeIn(animate = item.record.id in newRecordIds)
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = FreshNowTransitions.stateChange(),
                        fadeOutSpec = FreshNowTransitions.stateChange()
                    )
            )
        }
    }
}

/** 空态条目的 key。记录 id 都是 Long，与它不会撞 */
private const val EMPTY_LIST_KEY = "empty"

/**
 * 出现时淡入一次。
 *
 * 没走 `animateItem` 的 `fadeInSpec`，是因为「谁该淡入」的判据不同：后者认的是「这个条目刚被
 * 合成出来」，滚动时进入视口的旧条目也会被算进去。这里由调用方按数据给——[RecordsList] 传的是
 * 「刚存进来的那几条」（见 HomeViewModel.newRecordIds），空态文案则是每次出现都算——滚动于是
 * 不掺和进来，「新的」只指新存的那几条。
 *
 * 空态文案那一路还多一层原因：它出现时列表恰好是首次合成（冷启动且一条记录都没有），
 * animateItem 那时手里没有上一轮可比，本来就演不出来。
 *
 * animate 只在第一次合成时读一次：演到一半时上游若又更新（比如又存了一条），动画不会被中途掐断。
 */
@Composable
private fun Modifier.appearFadeIn(animate: Boolean): Modifier {
    val shouldAnimate = remember { animate }
    if (!shouldAnimate) return this

    val alpha = remember { Animatable(0f) }
    LaunchedEffect(alpha) { alpha.animateTo(1f, FreshNowTransitions.stateChange()) }
    return graphicsLayer { this.alpha = alpha.value }
}

/** 每次都淡入，用于只出现一次、没有滚动可言的条目（空态文案） */
@Composable
private fun Modifier.appearFadeIn(): Modifier = appearFadeIn(animate = true)

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
    // 透明度交给动画，描边的出现与消失才是淡入淡出，而不是硬切。
    val outlineAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = FreshNowTransitions.stateChange(),
        label = "selectionOutlineAlpha"
    )

    val rowShape = MaterialTheme.shapes.extraSmall

    ListItem(
        modifier = modifier
            // 这四边留白 + 裁剪是描边与涟漪共用的那一块区域：涟漪只能在这块圆角矩形里铺开，
            // 描边也画在同一处，两者因此完全重合。它同时决定了相邻两条选中框之间的缝，
            // 所以改这一个数，两者的位置与形状会一起变，不会再各算各的。
            .padding(ROW_INSET)
            .clip(rowShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .selectionOutline(
                color = MaterialTheme.colorScheme.primary,
                cornerSize = rowShape.topStart,
                width = FreshNowSize.selectionOutlineWidth,
                alpha = outlineAlpha
            ),
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

/** 条目四周的留白：描边与涟漪共用这一块区域的边界，去掉它两者都会胀回整行大小 */
private val ROW_INSET = FreshNowSpacing.xxs

/**
 * 选中描边：一圈圆角矩形，正好画在条目（也就是留白之后那一块）的边界上。
 *
 * 不用 Modifier.border 而自己画，是为了把透明度交给动画，描边的出现与消失才是淡入淡出。
 * 它只是画、不参与布局，所以选中前后条目不会跳动；位置与形状则完全交给外层的留白与裁剪
 * （见 RecordRow），描边因此和涟漪必然落在同一块区域的同一条边上。
 */
private fun Modifier.selectionOutline(
    color: Color,
    cornerSize: CornerSize,
    width: Dp,
    alpha: Float
): Modifier = drawWithContent {
    drawContent()
    if (alpha <= 0f) return@drawWithContent

    val stroke = width.toPx()
    // Stroke 以矩形路径为中心向两侧各扩半个线宽，路径要让开半个线宽，描边的外沿才正好压在边界上
    val edge = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(edge, edge),
        size = Size(size.width - edge * 2f, size.height - edge * 2f),
        cornerRadius = CornerRadius(cornerSize.toPx(size, this)),
        style = Stroke(width = stroke),
        alpha = alpha
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
            newRecordIds = emptySet(),
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
            newRecordIds = emptySet(),
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
