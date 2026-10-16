package com.freshnow.app.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R

/**
 * 确认移除某一张照片。
 *
 * 点一下格子就删掉是太轻的动作——误触一下那张图就没了。改成先起一片底部面板，面板上只有
 * 「移除照片」这一项（用户要求）：动作本身没变，只是从「一下就删」变成「先说要删哪一张」。
 *
 * 用底部面板而不是对话框：M3 里打断式的对话框留给「不可撤销、或影响很大」的操作，而这里只是
 * 从草稿里去掉一张——保存之前都不落盘，退出编辑就当作没删过。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemovePhotoSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    MenuBottomSheet(sheetState = sheetState, onDismissRequest = onDismissRequest) {
        MenuSheetItem(
            text = stringResource(R.string.record_image_remove),
            onClick = onConfirm
        )
    }
}
