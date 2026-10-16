package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing

/** 一条记录最多几张照片：九宫格放满为止。再多就变成相册了，记录页也放不下 */
internal const val MAX_RECORD_IMAGES = 9

/** 九宫格，一行三格 */
private const val COLUMNS = 3

/**
 * 九宫格里的一格。
 *
 * [key] 用来在增删时认住同一格（已存的照片用文件名，还在草稿里的用它自己的编号）：只看下标的话，
 * 删掉中间那张会让后面每一格的图都当成「换成了另一张」重放一次动画。
 */
internal data class PhotoGridItem(val key: String, val image: Bitmap?)

/**
 * 记录里的照片区：一格一张，末尾一格是「+」，每格右上角是「✕」。
 *
 * 用九宫格而不是一排缩略图：上限就是九张，格子把「已经存了几张、还能加几张」摆在同一块地方；
 * 一排缩略图放到第五张就得横着滑，用户看不见自己存了多少。
 *
 * 用普通的 Column + Row 而不是 LazyVerticalGrid：格数上限就是九，而这个组件要嵌在一个本身会滚动的
 * 表单里，嵌套的惰性网格会把滚动手势抢走。
 */
@Composable
internal fun PhotoGrid(
    items: List<PhotoGridItem>,
    onOpen: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 把「已有的格子」与末尾那格「+」拼成一串再切行：一行满了自然下移，「+」接在最后一张照片之后。
    // 到顶时不再补那一格，上限因此是看得见的
    val cellCount = items.size + if (items.size < MAX_RECORD_IMAGES) 1 else 0
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)
    ) {
        (0 until cellCount).chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FreshNowSpacing.xxxs)) {
                row.forEach { index ->
                    Box(modifier = Modifier.weight(1f)) {
                        val item = items.getOrNull(index)
                        if (item == null) {
                            AddTile(onClick = onAdd)
                        } else {
                            PhotoTile(
                                item = item,
                                onOpen = { onOpen(index) },
                                onRemove = { onRemove(index) }
                            )
                        }
                    }
                }
                // 一行不满时补上等宽的空位：不补的话最后那一行剩下的格子会被拉宽，与上面几行大小不一
                repeat(COLUMNS - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 一格照片。
 *
 * 语义（「扫描照片」与「移除照片」）挂在两块可点的区域上，而不是里面的图标上：按语义找节点的
 * 测试与无障碍工具找到的必须是能点的那个节点，挂在图标上就会找到一个点不动的节点。
 */
@Composable
private fun PhotoTile(item: PhotoGridItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    val photoLabel = stringResource(R.string.record_image)
    val removeLabel = stringResource(R.string.record_image_remove)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onOpen, onClickLabel = stringResource(R.string.record_image_open))
            .semantics { contentDescription = photoLabel }
    ) {
        if (item.image != null) {
            Image(
                bitmap = remember(item.image) { item.image.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_no_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(FreshNowSize.smallIcon)
            )
        }

        // 用 IconButton 而不是裸图标：它的点击区按 M3 的最小触摸目标给（48dp），
        // 而这个按钮落在照片右上角，触摸区小了就等于点不中
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { contentDescription = removeLabel }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = null,
                modifier = Modifier.size(FreshNowSize.smallIcon)
            )
        }
    }
}

/**
 * 末尾那格「+」。虚线描边取与输入框同一个颜色角色（outline）：
 * 让「这里还能加一张照片」与「这里还能写点什么」看起来是同一类地方。
 */
@Composable
private fun AddTile(onClick: () -> Unit) {
    val addLabel = stringResource(R.string.record_image_add)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(
                width = FreshNowSize.fieldOutlineWidth,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small
            )
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick, onClickLabel = addLabel)
            .semantics { contentDescription = addLabel },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FreshNowSize.smallIcon)
        )
    }
}
