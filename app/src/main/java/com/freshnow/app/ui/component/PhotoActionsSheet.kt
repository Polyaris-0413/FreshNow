package com.freshnow.app.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R

/**
 * 长按一张照片之后：问这一张要怎么办。
 * 点一下只是看图（见 [PhotoGrid]），改照片得走长按——移除与设为首图都会改草稿，不该由手指滑过触发。
 * [canSetFirst] 为 false 时不给「用作首图」：长按的正好是第一张，留着那一项就是一个按下去什么都不会变的动作。
 * 用底部面板而不是对话框：M3 里打断式的对话框留给「不可撤销、或影响很大」的操作，而这里只是在草稿里
 * 挪一张、去一张——保存之前都不落盘，退出编辑就当作没改过。
 * 两个动作都先收起面板、收起完再回调（[hideSheetThen]），不能留给调用方：直接把这页从组合里拿掉的话，
 * 收起动画根本没机会播，同一个 [SheetState] 还会停在展开态——下次打开时 `show()` 立刻完成，弹入动画也就没了
 * （用户看到的是「操作完一张、马上长按另一张，第二片面板突然冒出来」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoActionsSheet(
    sheetState: SheetState,
    canSetFirst: Boolean,
    onDismissRequest: () -> Unit,
    onSetFirst: () -> Unit,
    onRemove: () -> Unit
) {
    val scope = rememberCoroutineScope()

    MenuBottomSheet(sheetState = sheetState, onDismissRequest = onDismissRequest) {
        if (canSetFirst) {
            MenuSheetItem(
                text = stringResource(R.string.record_image_set_first),
                onClick = {
                    scope.hideSheetThen(sheetState, onHidden = onDismissRequest, action = onSetFirst)
                }
            )
        }
        MenuSheetItem(
            text = stringResource(R.string.record_image_remove),
            onClick = {
                scope.hideSheetThen(sheetState, onHidden = onDismissRequest, action = onRemove)
            }
        )
    }
}
