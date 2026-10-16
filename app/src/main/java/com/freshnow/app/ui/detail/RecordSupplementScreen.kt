package com.freshnow.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.ui.component.EditField
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 补充信息页：只有用户自己知道、AI 读不到的那几项（现在只有「位置」）。
 *
 * 单独一页而不是直接进编辑记录页：扫完一条之后要做的事是「把位置补上」，而编辑记录页那一屏是
 * 「模型认错了什么」（照片、名称、四项日期都摆在那里），直接落在那里等于在一堆与当下目的无关的
 * 控件里找一个字段。往后这类字段还会多，都加在这一页里。
 *
 * 入口只有扫描页保存后那一个；以后要改位置从详情页的「编辑」进去，那里也有这一栏。
 *
 * 两条出路各是各的意思：[onSaved] 是「这条记录的事办完了」（回列表看它），[onBack] 是
 * 「还没保存，回去接着扫」（落回扫描页）——两者不能混成一个。
 */
@Composable
fun RecordSupplementScreen(
    recordId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordSupplementViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(recordId) { viewModel.load(recordId) }

    FreshNowSubPage(
        title = stringResource(R.string.record_supplement_title),
        onBack = onBack,
        actions = {
            // 记录读不到就没有可存的东西，不留一个按下去没反应的按钮
            if (uiState.found) {
                TextButton(
                    onClick = { viewModel.save(onSaved) },
                    enabled = uiState.canSave
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (!uiState.loaded) return@FreshNowSubPage

        if (!uiState.found) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.record_detail_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@FreshNowSubPage
        }

        RecordSupplementForm(
            uiState = uiState,
            onLocationChange = viewModel::onLocationChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 与编辑记录页同一套：键盘顶上来时内容能滚，字段不会被键盘盖住
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(FreshNowSpacing.sm)
        )
    }
}

/**
 * 表单本体。现在只有一项，仍用一列排布：往后每多一个手填字段就是往下添一行，排法与编辑页一致
 */
@Composable
internal fun RecordSupplementForm(
    uiState: RecordSupplementUiState,
    onLocationChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        EditField(
            value = uiState.location,
            onValueChange = onLocationChange,
            label = stringResource(R.string.record_location),
            // 本项之后没有可跳的下一项，输入法的动作键收在「完成」
            imeAction = ImeAction.Done
        )
    }
}
