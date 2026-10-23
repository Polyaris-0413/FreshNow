package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

/**
 * 一个字段：标签 + 输入框，[error] 与 [hint] 共用同一条辅助文字槽位。
 * 两处表单共用，因为要的正是同一套行为：「失焦之后才报错」「初值就写错时当场报」「回车收键盘」。
 * [error] 是「这个写法认不出」，[hint] 是该字段自己的说明；两者不会同时出现。
 * **不在用户刚敲第一个字符时报错**：日期是一个字符一个字符敲的，中途总是认不出，跟着敲键闪红只会让人以为坏了。
 * 规范原话是 show error text only after user interaction with a field，这里的「交互完」取「失焦」；
 * 初值就写错的例外——它不是用户刚敲的，不报的话「保存为什么灰着」没法解释。
 * 输入法的「完成」也算一次结束：本字段之后没有可跳的下一项时，按「完成」只收键盘、不失焦，于是敲完一个认不出的值盯着屏幕看什么都不会出现。
 */
@Composable
internal fun EditField(
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
