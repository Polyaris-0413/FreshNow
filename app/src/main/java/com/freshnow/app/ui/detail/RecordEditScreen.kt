package com.freshnow.app.ui.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.theme.FreshNowSpacing

@Composable
fun RecordEditScreen(
    recordId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordEditViewModel = viewModel()
) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    FreshNowSubPage(
        title = stringResource(R.string.record_edit_title),
        onBack = onBack,
        actions = {
            // 记录都读不到了就没有可存的东西，不留一个按下去没反应的按钮
            if (uiState.found) {
                TextButton(
                    onClick = { viewModel.save(onSaved = onBack) },
                    enabled = uiState.canSave
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        },
        // 点空白处收键盘兼“结束这个字段的输入”：保存被禁用时错误文案才显出来（见 RecordEditForm），
        // 而禁用的按钮不吃点击，那一下点击只有靠这里接住。挂在页面这一层是因为顶栏在表单之外
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        }
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

        RecordEditForm(
            uiState = uiState,
            onProductNameChange = viewModel::onProductNameChange,
            onProductionDateChange = viewModel::onProductionDateChange,
            onExpiryDateChange = viewModel::onExpiryDateChange,
            onShelfLifeChange = viewModel::onShelfLifeChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(FreshNowSpacing.sm)
        )
    }
}

/**
 * 编辑表单本体，不含读库与落盘：便于按定值直接断言字段与推算提示（见 RecordEditFormTest）。
 *
 * 四个字段一律是文本框，不挂日期选择器：这里改的是「模型读错的地方」，存量值可能是任意写法
 * （`2026.10.01`、`2026年10月`、空串），而选择器只能产出标准日期，一确认就会把原值覆盖掉。
 *
 * 错误文案的时机按 Material 的 Errors 模式：*"Show error text only after user interaction with a
 * field"*——所以只在字段失焦之后才报，不跟着敲键闪红（日期是一字符一字符敲的，中途总是认不出）。
 * 打开页面时就已经写错的旧值例外：它不是用户刚敲的，而且不说的话保存被禁用会变得没理由。
 *
 * 过期日期下面是它的来源说明：留空表示跟随推算，所以把推算结果显示在同一屏里；填了值就是标签
 * 印刷值，以它为准，不再显示推算——一屏之内就把「谁说了算」交代清楚。两者天然不会同时出现：
 * 推算说明只在留空时给，而错误文案只在非空且认不出时给。
 */
@Composable
internal fun RecordEditForm(
    uiState: RecordEditUiState,
    onProductNameChange: (String) -> Unit,
    onProductionDateChange: (String) -> Unit,
    onExpiryDateChange: (String) -> Unit,
    onShelfLifeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        EditField(
            value = uiState.productName,
            onValueChange = onProductNameChange,
            label = stringResource(R.string.scan_product_name)
        )
        EditField(
            value = uiState.productionDate,
            onValueChange = onProductionDateChange,
            label = stringResource(R.string.scan_production_date),
            error = if (uiState.productionDateInvalid) stringResource(R.string.record_edit_error_date) else null
        )
        EditField(
            value = uiState.shelfLife,
            onValueChange = onShelfLifeChange,
            label = stringResource(R.string.scan_shelf_life),
            error = if (uiState.shelfLifeInvalid) stringResource(R.string.record_edit_error_shelf_life) else null
        )
        EditField(
            value = uiState.expiryDate,
            onValueChange = onExpiryDateChange,
            label = stringResource(R.string.scan_expiry_date),
            error = if (uiState.expiryDateInvalid) stringResource(R.string.record_edit_error_date) else null,
            hint = if (uiState.expiryDate.isBlank()) {
                derivedExpiryHint(uiState.derivedExpiry)
            } else {
                null
            },
            // 本项之后没有可跳的下一项，输入法的动作键收在「完成」
            imeAction = ImeAction.Done
        )
    }
}

/**
 * 一个字段。[error] 是「这个写法认不出」，[hint] 是该字段自己的说明，两者共用同一个槽位
 * （规范：until the error is fixed, the error replaces the helper text）。
 *
 * 错误要等字段失焦才报，但已经写错的旧值一打开就报（见 [RecordEditForm]）。
 *
 * 输入法的「完成」也要算一次结束：本字段之后没有可跳的下一项，按「完成」只会收起键盘、
 * 不失焦，于是敲完一个认不出的值盯着屏幕看，什么都不会出现——那看起来就像这个字段不校验。
 */
@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val focusManager = LocalFocusManager.current
    var leftField by remember { mutableStateOf(false) }
    // 初值取「进来时就已写错」：这种值不是用户刚敲的，不说的话保存为什么灰着就没法解释
    var reported by remember { mutableStateOf(error != null) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    leftField = true
                } else if (leftField) {
                    reported = true
                }
            },
        label = { Text(text = label) },
        isError = reported && error != null,
        supportingText = when {
            reported && error != null -> {
                { Text(text = error) }
            }
            hint != null -> {
                { Text(text = hint) }
            }
            else -> null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    )
}

/** 过期日期留空时的说明：算得出就给日期，算不出就如实说推不出（哪个字段错了由那个字段自己报） */
@Composable
private fun derivedExpiryHint(derived: ExpiryOutcome): String = when (derived) {
    is ExpiryOutcome.Resolved -> stringResource(R.string.record_edit_expiry_hint, derived.date)
    ExpiryOutcome.InsufficientInput, ExpiryOutcome.UnparseableShelfLife ->
        stringResource(R.string.record_edit_expiry_hint_pending)
}
