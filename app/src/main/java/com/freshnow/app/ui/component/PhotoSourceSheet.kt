package com.freshnow.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 添加照片：从哪来。编辑记录页与扫描页的编辑态共用这一个：两页都只是要一张新照片。
 * 就是一叠居中卡片（见 [MenuBottomSheet]），与主页顶栏「更多选项」同一副外观：这里是挑一个动作，
 * 不是一列设置项。
 * 「从相册选择」与「拍照」拼成一块：两者是同一层级的两个来路，中间一条分隔线把两块分干净。
 * 不带「移除照片」那一项：删哪一张由九宫格上那一格回答，而这里只能说「移除照片」，究竟是哪一张并不清楚。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoSourceSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit
) {
    MenuBottomSheet(sheetState = sheetState, onDismissRequest = onDismissRequest) {
        // 两个来源上下拼成一组（上：拍照，下：相册），与结果字段那一组同一套拼法：外圆内方、
        // 中间只留一条 2dp 的缝
        val outerCorner = MaterialTheme.shapes.medium
        val innerCorner = MaterialTheme.shapes.extraSmall
        Column(verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)) {
            MenuSheetItem(
                text = stringResource(R.string.action_take_photo),
                onClick = onTakePhoto,
                shape = splicedShape(top = outerCorner, bottom = innerCorner)
            )
            MenuSheetItem(
                text = stringResource(R.string.record_image_source_gallery),
                onClick = onPickFromGallery,
                shape = splicedShape(top = innerCorner, bottom = outerCorner)
            )
        }
    }
}
