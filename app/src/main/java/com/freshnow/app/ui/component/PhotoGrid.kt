package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlin.math.roundToInt

/** 一条记录最多几张照片：九宫格放满为止。再多就变成相册了，记录页也放不下 */
internal const val MAX_RECORD_IMAGES = 9

/** 九宫格，一行三格 */
private const val COLUMNS = 3

/** 末尾那格「+」在列表里的 key。照片用各自的名字，它不能撞上 */
private const val ADD_KEY = "add-photo-tile"

/**
 * 长按之后拖动多少才算「提起来了」。
 *
 * M3 没有「长按拖动」这个组件的触感档位，它只规定拖拽要在长按之后才认（见 detectDragGesturesAfterLongPress）。
 * 这个放大倍率只是让手指下的那一格看起来被拿住了。
 */
private const val DRAGGING_SCALE = 1.06f

/**
 * 九宫格里的一格。
 *
 * [key] 用来在增删时认住同一格（已存的照片用文件名，草稿用它自己的编号）：只看下标的话，
 * 删掉中间那张会让后面每一格的图都当成「换成了另一张」，动画与解码都会重来一遍。
 */
internal data class PhotoGridItem(val key: String, val image: Bitmap?)

/**
 * 记录里的照片区：一格一张，末尾一格是「+」，**点某一张就把它移除**，**长按拖动可以换位置**。
 *
 * 不在格子上再压一枚 ✕：格子只有一百来 dp，角上叠一个按钮既难看又容易点错，而它要做的只是
 * 「去掉这一张」。整格一个动作之后，误触的代价也只是「再加回来」——草稿里的删除在保存前不落盘。
 *
 * 格子之间的间距取 [FreshNowSpacing.xs]（8dp）：M3 的布局规范给的就是这一档——网格槽间距在
 * compact（手机）上是 8dp，medium 16dp，large 以上 24dp。它不是「拼接块之间那条缝」
 * （[FreshNowSpacing.xxxs]），那一条只用来把同一组里的填充块拼在一起。
 *
 * 用 LazyVerticalGrid 而不是手写的 Column + Row：删掉中间一张时，剩下的格子要有位移与淡出动画
 * （`Modifier.animateItem`），而那套动画只有惰性布局给得了。它自己不许滚
 * （`userScrollEnabled = false`）：这个组件嵌在一个本身会滚动的表单里，抢走手势就会让表单卡住。
 */
@Composable
internal fun PhotoGrid(
    items: List<PhotoGridItem>,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showAdd = items.size < MAX_RECORD_IMAGES
    val cellCount = items.size + if (showAdd) 1 else 0
    if (cellCount == 0) return

    // 正在拖的是哪一格，以及它跟手的位移。这两个只在拖动期间有值
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 三列等宽：总宽减去两条缝再三等分。高度得自己算出来——惰性网格不接受无界高度，
        // 而这里又不许它滚，只能把行数换成固定高度
        val gap = FreshNowSpacing.xs
        val cell = (maxWidth - gap * (COLUMNS - 1)) / COLUMNS
        val rows = (cellCount + COLUMNS - 1) / COLUMNS
        val stepPx = with(LocalDensity.current) { (cell + gap).toPx() }

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(cell * rows + gap * (rows - 1)),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                val dragging = draggingIndex == index
                PhotoTile(
                    item = item,
                    onRemove = { onRemove(index) },
                    modifier = Modifier
                        // 增删时的淡入淡出与其余格子的让位都由它演
                        .animateItem()
                        // 被拿着的那一格浮到其余格子之上，否则它会被后画的格子盖住
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = DRAGGING_SCALE
                                scaleY = DRAGGING_SCALE
                            }
                        }
                        .pointerInput(item.key, items.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    // 消费掉：不消费的话，表单会跟着一起滚
                                    change.consume()
                                    dragOffset += amount
                                    val target = gridTargetIndex(index, dragOffset, stepPx, items.size)
                                    if (target != index) {
                                        onMove(index, target)
                                        // 换过位之后，这一格的中心已经在新位置上，
                                        // 跟手位移得从头算，否则它会继续往同一方向多挪一格
                                        draggingIndex = target
                                        dragOffset = Offset.Zero
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffset = Offset.Zero
                                }
                            )
                        }
                )
            }
            if (showAdd) {
                item(key = ADD_KEY) {
                    AddTile(onClick = onAdd, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

/**
 * 拖到哪儿算落在哪一格。
 *
 * 网格是规则的（等宽等高、间距固定），所以不必去读每一格的坐标：从出发那一格的中心加上跟手位移，
 * 再按「跨过半格就算换位」取整即可（[roundToInt] 就是这条规矩）。
 */
private fun gridTargetIndex(
    from: Int,
    offset: Offset,
    stepPx: Float,
    count: Int
): Int {
    if (stepPx <= 0f) return from
    val column = from % COLUMNS
    val row = from / COLUMNS
    val targetColumn = (column + (offset.x / stepPx).roundToInt()).coerceIn(0, COLUMNS - 1)
    val targetRow = (row + (offset.y / stepPx).roundToInt()).coerceAtLeast(0)
    return (targetRow * COLUMNS + targetColumn).coerceIn(0, count - 1)
}

/**
 * 一格照片。整格可点，点了就是「去掉这一张」——语义挂在可点的那块区域上，
 * 而不是里面的图标上：按语义找节点的测试与无障碍工具找到的必须是能点的那个。
 */
@Composable
private fun PhotoTile(item: PhotoGridItem, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.record_image_remove)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onRemove, onClickLabel = label)
            .semantics { contentDescription = label }
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
    }
}

/**
 * 末尾那格「+」。虚线描边取与输入框同一个颜色角色（outline）：
 * 让「这里还能加一张照片」与「这里还能写点什么」看起来是同一类地方。
 */
@Composable
private fun AddTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val addLabel = stringResource(R.string.record_image_add)
    Box(
        modifier = modifier
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
