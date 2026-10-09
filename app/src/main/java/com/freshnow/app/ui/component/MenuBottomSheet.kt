package com.freshnow.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 动作面板：底部弹出的一叠卡片，每张一个动作，文字居中。
 *
 * 主页顶栏的「更多选项」与编辑页的照片来源都用它，两处外观因此不会分叉。容器仍是 M3 的
 * 底部面板——面板本身没问题，这里定的是它**里面装什么**：M3 的列表项（ListItem）是给设置页
 * 那种「一行一条、左图右文」的清单用的，换成一叠居中的卡片，才读得出是「挑一个动作」。
 *
 * 拖动手柄那段涟漪处理：M3 把拖动手柄整个槽（一块 32×48 的触摸区）包了一层不带形状的
 * clickable，涟漪于是按那块矩形铺开，与里面 4dp 的胶囊完全不是一回事。手柄这里本来也不需要
 * 按压反馈——「点它收起面板」没有歧义——所以直接关掉：给 LocalRippleConfiguration 传 null
 * 就是 M3 为这件事留的开关（见 Ripple.kt 的 KDoc）。手柄的点击、长按提示与无障碍语义都不受影响。
 * 面板内部要把默认配置恢复回去，否则卡片的按压反馈也会一起被关掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val defaultRippleConfiguration = LocalRippleConfiguration.current

    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = modifier
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides defaultRippleConfiguration) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FreshNowSpacing.sm)
                        .padding(bottom = FreshNowSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xs)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 面板里的一张卡片。整张可点，颜色取容器色最亮的一档，与面板自己的底色（surfaceContainerLow）
 * 拉开一层；文字居中而不左对齐：一叠卡片本来就不长，居中读起来更像「选一个」。
 *
 * [selected] 是「单选里的当前项」：非 null 表示这是一叠可选项，选中那一项的文字换成 primary；
 * 传 null（默认）表示这只是一个动作（关于、设置、移除照片）。
 *
 * 选项用 selectable 而不是 clickable：读屏软件才会把它念成「两项里的第几项、选没选中」，
 * 单选该有的语义就在这里。不另加对勾图标：面板里选项本来就不多，换个颜色已经够读。
 *
 * 提为 public 是为了让动作面板的调用方拼装内容（包括把两张并排拼成一块）。
 */
@Composable
fun MenuSheetItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null
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
                .then(
                    if (selected == null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = onClick
                        )
                    }
                )
                .padding(vertical = FreshNowSpacing.sm),
            style = MaterialTheme.typography.bodyLarge,
            // Color.Unspecified 即继承 LocalContentColor（卡片上的默认文字色）
            color = if (selected == true) MaterialTheme.colorScheme.primary else Color.Unspecified,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 先收起面板，收起动画结束后再执行 [action]。
 *
 * 跳转或拉起系统选择器与收起动画同时进行会互相打断：前者把面板连底下那页一起换掉，后者就停在
 * 半路上；系统选择器盖上来时面板还开着，回来会看到它悬在那儿。收完再动，两件事各自完整。
 *
 * 收起被别的原因打断（用户又滑上去了）时不执行动作：那时面板还在，动作该由用户再点一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
fun CoroutineScope.hideSheetThen(
    sheetState: SheetState,
    onHidden: () -> Unit,
    action: () -> Unit
) {
    launch { sheetState.hide() }.invokeOnCompletion {
        if (!sheetState.isVisible) {
            onHidden()
            action()
        }
    }
}
