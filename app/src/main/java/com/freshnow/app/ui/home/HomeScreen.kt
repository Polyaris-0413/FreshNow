package com.freshnow.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.component.FreshNowTopAppBar
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val SAVED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun HomeRoute(
    onNavigateToScan: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()

    HomeScreen(
        records = records,
        onNavigateToScan = onNavigateToScan,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    records: List<ScanRecord>,
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FreshNowSpacing.sm)
                    .padding(bottom = FreshNowSpacing.sm),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
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

@Composable
private fun SheetMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = FreshNowSpacing.sm),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RecordsList(
    records: List<ScanRecord>,
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

    LazyColumn(modifier = modifier) {
        items(records, key = { it.id }) { record ->
            RecordRow(record)
        }
    }
}

@Composable
private fun RecordRow(
    record: ScanRecord,
    modifier: Modifier = Modifier
) {
    val unknown = stringResource(R.string.scan_value_unknown)
    val expiry = remember(record) {
        ExpiryCalculator.resolve(record.expiryDate, record.productionDate, record.shelfLife)
    }
    val expiryText = when (expiry) {
        is ExpiryOutcome.Resolved -> expiry.date
        ExpiryOutcome.UnparseableShelfLife -> stringResource(R.string.scan_expiry_unparseable)
        ExpiryOutcome.InsufficientInput -> unknown
    }

    ListItem(
        modifier = modifier,
        overlineContent = { Text(text = formatSavedAt(record.savedAt)) },
        headlineContent = { Text(text = stringResource(R.string.record_expiry, expiryText)) },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.record_detail,
                    record.productionDate.ifBlank { unknown },
                    record.shelfLife.ifBlank { unknown }
                )
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun formatSavedAt(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(SAVED_AT_FORMATTER)

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    FreshNowTheme {
        HomeScreen(
            records = listOf(
                ScanRecord(
                    id = 1,
                    productionDate = "2025-01-01",
                    expiryDate = "",
                    shelfLife = "18个月",
                    savedAt = 0
                )
            ),
            onNavigateToScan = {},
            onNavigateToAbout = {},
            onNavigateToSettings = {}
        )
    }
}
