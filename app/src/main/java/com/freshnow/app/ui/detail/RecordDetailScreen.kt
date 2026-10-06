package com.freshnow.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowDateFields
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.formatSavedAt
import com.freshnow.app.ui.theme.FreshNowSpacing

@Composable
fun RecordDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordDetailViewModel = viewModel()
) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FreshNowSubPage(
        title = stringResource(R.string.record_detail_title),
        onBack = onBack,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(FreshNowSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
            ) {
                Text(
                    text = formatSavedAt(record.savedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FreshNowDateFields(
                    productionDate = record.productionDate,
                    expiry = uiState.expiry,
                    shelfLife = record.shelfLife
                )
            }
        }
    }
}
