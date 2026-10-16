package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.freshnow.app.ui.theme.FreshNowTransitions
import kotlinx.coroutines.launch
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

    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    /** 被拖的那一格现在占着哪个位置（渲染用的顺序里）*/
    var hoverIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    // 拿起来的放大要渐变：在 1.0 与 1.06 之间硬跳，松手那一刻会看见它「突然缩一下」
    val draggingScale by animateFloatAsState(
        targetValue = if (draggingKey != null) DRAGGING_SCALE else 1f,
        animationSpec = FreshNowTransitions.fastSpatial(),
        label = "draggingScale"
    )

    /**
     * 拖动期间给界面看的那一份顺序：被拖的那一格占住 [hoverIndex]，其余格子依次让位。
     *
     * 它是**当帧算出来的**（不经过任何异步状态），所以「手指位置」与「格子位置」永远在同一帧
     * 里自洽——之前那些闪与跳，根源都是这两件事分属不同帧：布局要等列表更新（下一帧），
     * 而跟手位移是当帧改的。
     */
    val visualItems = items.withDraggedAt(draggingKey, hoverIndex)
    /**
     * 被拖的那一格在**真实列表**里的下标，在长按开始那一刻定下。
     *
     * 它必须是 state，不能是组合时算出来的值：拖动开始之前根本没有人在拖，那时算出来的
     * 是无意义的 -1；而手势协程与 `graphicsLayer` 的绘制回调都只在**第一次组合**时把外层的
     * 值捕获一次，此后永远读到那个 -1——于是起始位置算不出来（让位错位）、松手也提交不了
     * （`from < 0` 直接返回）。这与上一轮那个「协程捕获了组合时的 index」是同一类错误。
     */
    var dragFromIndex by remember { mutableStateOf(-1) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 三列等宽：总宽减去两条缝再三等分。高度得自己算出来——惰性网格不接受无界高度，
        // 而这里又不许它滚，只能把行数换成固定高度
        val gap = FreshNowSpacing.xs
        val cell = (maxWidth - gap * (COLUMNS - 1)) / COLUMNS
        val rows = (cellCount + COLUMNS - 1) / COLUMNS
        val stepPx = with(LocalDensity.current) { (cell + gap).toPx() }
        val count = items.size

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(cell * rows + gap * (rows - 1)),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            itemsIndexed(visualItems, key = { _, item -> item.key }) { index, item ->
                val latestIndex by rememberUpdatedState(index)
                val dragging = item.key == draggingKey
                PhotoTile(
                    // 报的是**真实列表**里的下标（渲染顺序在拖动期间与它不同）
                    item = item,
                    onRemove = { onRemove(items.indexOfFirst { it.key == item.key }) },
                    modifier = Modifier
                        // 增删时淡入淡出；其余格子让位靠它。被拖的那一格不要位移动画：
                        // 它的位置由手指决定（上面那套换算已经把两者对齐了）
                        .animateItem(
                            placementSpec = if (dragging) null else FreshNowTransitions.defaultSpatial()
                        )
                        // 被拿着的那一格浮到其余格子之上，否则它会被后画的格子盖住
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                // 它现在占着 hoverIndex 那一格，而位移的参考点是起始格：
                                // 起始格中心 + dragOffset 是手指位置，两者之差才是要加的位移
                                val delta = gridDelta(
                                    dragFromIndex,
                                    hoverIndex ?: dragFromIndex,
                                    stepPx
                                )
                                translationX = dragOffset.x - delta.x
                                translationY = dragOffset.y - delta.y
                                scaleX = draggingScale
                                scaleY = draggingScale
                            }
                        }
                        .pointerInput(item.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragFromIndex = latestIndex
                                    dragOffset = Offset.Zero
                                    hoverIndex = latestIndex
                                    draggingKey = item.key
                                },
                                onDrag = { change, amount ->
                                    // 消费掉：不消费的话，表单会跟着一起滚
                                    change.consume()
                                    dragOffset += amount
                                    // 只改「占着哪一格」，不动真实列表：让位是上面那份渲染顺序的事
                                    hoverIndex = gridTargetIndex(
                                        dragFromIndex,
                                        dragOffset,
                                        stepPx,
                                        count
                                    )
                                },
                                onDragEnd = {
                                    val from = dragFromIndex
                                    val to = (hoverIndex ?: from).coerceIn(0, items.lastIndex)
                                    if (from !in items.indices || to == from) {
                                        dragFromIndex = -1
                                        dragOffset = Offset.Zero
                                        hoverIndex = null
                                        draggingKey = null
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    // 先把位移折算成「相对目标格」的值：提交之后它在列表里的位置就是
                                    // 目标格，那时这个值才是对的。这一帧真实列表还没变，而
                                    // 「位移 − 格子差」的合计没变，所以画面不会动
                                    dragOffset -= gridDelta(from, to, stepPx)
                                    val settle = dragOffset
                                    scope.launch {
                                        // 先提交顺序，再让它从手指处滑到格子正中
                                        onMove(from, to)
                                        animate(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = FreshNowTransitions.fastSpatial()
                                        ) { value, _ -> dragOffset = settle * (1f - value) }
                                        dragOffset = Offset.Zero
                                        hoverIndex = null
                                        draggingKey = null
                                        dragFromIndex = -1
                                    }
                                },
                                onDragCancel = {
                                    dragFromIndex = -1
                                    dragOffset = Offset.Zero
                                    hoverIndex = null
                                    draggingKey = null
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
 * 拖动期间给界面看的那一份顺序：把 [key] 那一格挪到 [hoverIndex] 上，其余格子依次让位。
 *
 * 纯函数、只看入参：界面每一帧拿同样的入参就能算出一致的顺序，不存在「等列表更新」的下一帧。
 * 越界或找不到那一格时原样返回。
 */
internal fun List<PhotoGridItem>.withDraggedAt(
    key: String?,
    hoverIndex: Int?
): List<PhotoGridItem> {
    if (key == null || hoverIndex == null) return this
    val from = indexOfFirst { it.key == key }
    val to = hoverIndex.coerceIn(0, lastIndex)
    if (from < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * 从一格移到另一格，位置差了多少像素。
 *
 * 列差乘一个步长（格宽 + 缝），行差乘一个步长——网格是等宽等高的，所以两轴共用同一个步长。
 */
private fun gridDelta(from: Int, to: Int, stepPx: Float): Offset = Offset(
    x = ((to % COLUMNS) - (from % COLUMNS)) * stepPx,
    y = ((to / COLUMNS) - (from / COLUMNS)) * stepPx
)

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
 * 一格照片。整格可点，点了就是「要移除这一张」——语义挂在可点的那块区域上，
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
