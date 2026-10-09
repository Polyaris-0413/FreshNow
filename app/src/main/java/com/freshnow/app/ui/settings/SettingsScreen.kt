package com.freshnow.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.SortOrder
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.ListItemText
import com.freshnow.app.ui.component.MenuBottomSheet
import com.freshnow.app.ui.component.SectionHeading
import com.freshnow.app.ui.component.SortOrderMenuItems
import com.freshnow.app.ui.component.hideSheetThen
import com.freshnow.app.ui.component.label
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTheme
import kotlinx.coroutines.launch

/** 当前打开的是哪个编辑面板，null 表示没打开 */
private enum class SettingsEditor { BasicConfig, ExtraRequest }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToSync: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<SettingsEditor?>(null) }
    // 「排序方式」是单选，用的是动作面板而不是编辑面板（见 MenuBottomSheet），单独一个开关
    var choosingSortOrder by remember { mutableStateOf(false) }

    // 收起编辑面板。收起动画结束再清 editing：动画期间面板还在，提前清掉会闪一下
    fun closeEditor() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { editing = null }
    }

    FreshNowSubPage(
        title = stringResource(R.string.settings),
        onBack = onBack,
        modifier = modifier
    ) { innerPadding ->
        // 落盘值到达前不渲染设置项：开关若先按默认值（false）组合出来，随后读到的真值会让
        // Switch 播一次关→开的动画，观感上就是「每次进设置页开关动画都重播」
        if (uiState.loaded) {
            SettingsList(
                summary = uiState.saved.modelName.ifBlank { stringResource(R.string.ai_settings_not_configured) },
                extraSummary = uiState.saved.extraRequestJson.ifBlank {
                    stringResource(R.string.ai_settings_extra_default)
                },
                showReasoning = uiState.saved.showReasoning,
                manualEntry = uiState.behavior.manualEntry,
                onBasicConfigClick = {
                    viewModel.startEditing()
                    editing = SettingsEditor.BasicConfig
                },
                onExtraRequestClick = {
                    viewModel.startEditingExtra()
                    editing = SettingsEditor.ExtraRequest
                },
                onShowReasoningChange = viewModel::onShowReasoningChange,
                onManualEntryChange = viewModel::onManualEntryChange,
                sortOrder = uiState.behavior.sortOrder,
                onSortOrderClick = { choosingSortOrder = true },
                onSyncClick = onNavigateToSync,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            )
        }
    }

    // 排序方式是一个「挑一个」的选择，用动作面板（与主页顶栏那个同一份内容）
    if (choosingSortOrder) {
        MenuBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { choosingSortOrder = false }
        ) {
            SortOrderMenuItems(
                current = uiState.behavior.sortOrder,
                // 选完先收面板再写入：收起动画期间面板还在，提前改会让那两张卡的「选中」当场跳一下
                onSelect = { order ->
                    scope.hideSheetThen(sheetState, onHidden = { choosingSortOrder = false }) {
                        viewModel.onSortOrderChange(order)
                    }
                }
            )
        }
    }

    editing?.let { editor ->
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState
        ) {
            when (editor) {
                SettingsEditor.BasicConfig -> AiBasicConfigEditor(
                    uiState = uiState,
                    onBaseUrlChange = viewModel::onBaseUrlChange,
                    onModelNameChange = viewModel::onModelNameChange,
                    onApiKeyChange = viewModel::onApiKeyChange,
                    onCancel = { closeEditor() },
                    onSave = {
                        if (viewModel.save()) closeEditor()
                    }
                )

                SettingsEditor.ExtraRequest -> ExtraRequestEditor(
                    uiState = uiState,
                    onValueChange = viewModel::onExtraJsonChange,
                    onCancel = { closeEditor() },
                    onSave = {
                        viewModel.saveExtra()
                        closeEditor()
                    }
                )
            }
        }
    }
}

/**
 * 设置项本体，不含编辑面板与落盘：便于按定值直接断言分区归属（见 SettingsScreenTest）
 */
@Composable
internal fun SettingsList(
    summary: String,
    extraSummary: String,
    showReasoning: Boolean,
    manualEntry: Boolean,
    sortOrder: SortOrder,
    onBasicConfigClick: () -> Unit,
    onExtraRequestClick: () -> Unit,
    onShowReasoningChange: (Boolean) -> Unit,
    onManualEntryChange: (Boolean) -> Unit,
    onSortOrderClick: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeading(
            text = stringResource(R.string.ai_settings_section_title),
            // 与页面顶端的距离
            modifier = Modifier.padding(top = FreshNowSpacing.sm)
        )

        // 两行列表项，字号由 ListItem 默认值给出（headline Body Large / supporting Body Medium）
        // 图标是装饰性的，名称已由标题给出，因此 contentDescription 为 null
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_base_config), contentDescription = null)
            },
            headlineContent = {
                ListItemText(stringResource(R.string.ai_settings_basic_group_title), summary)
            },
            modifier = Modifier.clickable(onClick = onBasicConfigClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_thinking_params), contentDescription = null)
            },
            headlineContent = {
                ListItemText(stringResource(R.string.ai_settings_extra_title), extraSummary)
            },
            modifier = Modifier.clickable(onClick = onExtraRequestClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        SectionHeading(
            text = stringResource(R.string.behavior_settings_section_title),
            // 与上一段之间留段间距（设计源的 24），两个分区才分得开
            modifier = Modifier.padding(top = FreshNowSpacing.md)
        )

        // 与「显示思维链」同一种行：整行可点、角色给 Switch（开关自己不吃点击），
        // 说明文字写在下面一行而不是摆在标题里——开关说的是「怎么用「添加」」，标题只给出名字
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_manual_entry), contentDescription = null)
            },
            headlineContent = {
                ListItemText(
                    stringResource(R.string.manual_entry),
                    stringResource(R.string.behavior_manual_entry_support)
                )
            },
            trailingContent = {
                Switch(checked = manualEntry, onCheckedChange = null)
            },
            modifier = Modifier.toggleable(
                value = manualEntry,
                role = Role.Switch,
                onValueChange = onManualEntryChange
            ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        // 与「基础配置」那一行同一种做法：行上显示当前值，点开面板换一个。
        // 当前值就是这一行的说明文字——设置页里「现在用的是哪种」得看得见
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_sort), contentDescription = null)
            },
            headlineContent = {
                ListItemText(stringResource(R.string.behavior_sort_order_title), sortOrder.label())
            },
            modifier = Modifier.clickable(onClick = onSortOrderClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        // 同步是跨设备的行为，排在这一分区的末尾：上面的三项都只影响本机
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_sync), contentDescription = null)
            },
            headlineContent = {
                ListItemText(
                    stringResource(R.string.sync_title),
                    stringResource(R.string.sync_settings_support),
                    trailingLabel = stringResource(R.string.sync_beta_label)
                )
            },
            modifier = Modifier.clickable(onClick = onSyncClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        SectionHeading(
            text = stringResource(R.string.debug_settings_section_title),
            // 与上一段之间留段间距（设计源的 24），两个分区才分得开
            modifier = Modifier.padding(top = FreshNowSpacing.md)
        )

        // 整行可点，Switch 自己不再处理点击（onCheckedChange = null）；整行用 toggleable + Role.Switch
        // 而不是 clickable，才能让读屏软件把这一行读成带开/关状态的开关
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_reasoning), contentDescription = null)
            },
            headlineContent = {
                ListItemText(
                    stringResource(R.string.ai_settings_show_reasoning_title),
                    stringResource(R.string.ai_settings_show_reasoning_support)
                )
            },
            trailingContent = {
                Switch(checked = showReasoning, onCheckedChange = null)
            },
            modifier = Modifier.toggleable(
                value = showReasoning,
                role = Role.Switch,
                onValueChange = onShowReasoningChange
            ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        // 应用、项目、法律信息三段在关于页，不在设置里：设置只管可调项
    }
}


/**
 * 「基础配置」的编辑面板，承载在 ModalBottomSheet 内
 */
@Composable
private fun AiBasicConfigEditor(
    uiState: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = FreshNowSpacing.sm)
            .padding(bottom = FreshNowSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.ai_settings_basic_group_title),
            style = MaterialTheme.typography.titleMedium
        )

        RequiredTextField(
            value = uiState.baseUrl,
            onValueChange = onBaseUrlChange,
            label = stringResource(R.string.ai_settings_base_url_label),
            isError = uiState.baseUrlError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = uiState.modelName,
            onValueChange = onModelNameChange,
            label = stringResource(R.string.ai_settings_model_name_label),
            isError = uiState.modelNameError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
        )

        RequiredTextField(
            value = uiState.apiKey,
            onValueChange = onApiKeyChange,
            label = stringResource(R.string.ai_settings_api_key_label),
            isError = uiState.apiKeyError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FreshNowSpacing.xs)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        }
    }
}

/**
 * 「思考参数」的编辑面板，承载在 ModalBottomSheet 内
 */
@Composable
private fun ExtraRequestEditor(
    uiState: SettingsUiState,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = FreshNowSpacing.sm)
            .padding(bottom = FreshNowSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.ai_settings_extra_title),
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = uiState.extraJson,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.ai_settings_extra_label)) },
            supportingText = {
                Text(
                    text = stringResource(R.string.ai_settings_extra_hint)
                )
            },
            minLines = 3,
            // 关掉自动更正：JSON 里的半角引号一旦被输入法换成中文引号就解析不了了
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FreshNowSpacing.xs)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        }
    }
}

/**
 * 必填文本项，仅在未填时给出错误文案
 */@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        supportingText = if (isError) {
            { Text(text = stringResource(R.string.error_required_field)) }
        } else {
            null
        },
        isError = isError,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions
    )
}

@Preview(showBackground = true)
@Composable
private fun AiBasicConfigEditorPreview() {
    FreshNowTheme {
        AiBasicConfigEditor(
            uiState = SettingsUiState(),
            onBaseUrlChange = {},
            onModelNameChange = {},
            onApiKeyChange = {},
            onCancel = {},
            onSave = {},
            modifier = Modifier.padding(FreshNowSpacing.sm)
        )
    }
}
