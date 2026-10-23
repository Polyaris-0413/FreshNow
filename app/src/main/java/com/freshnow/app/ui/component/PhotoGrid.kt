package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

/** 九宫格放满为止。再多就变成相册了，记录页也放不下 */
internal const val MAX_RECORD_IMAGES = 9

private const val COLUMNS = 3

/** 末尾那格「+」在列表里的 key。照片用各自的名字，它不能撞上 */
private const val ADD_KEY = "add-photo-tile"

/**
 * 九宫格里的一格。
 *
 * [key] 用来在增删时认住同一格（已存的照片用文件名，草稿用它自己的编号）：只看下标的话，
 * 删掉中间那张会让后面每一格的图都当成「换成了另一张」，动画与解码都会重来一遍。
 */
internal data class PhotoGridItem(val key: String, val image: Bitmap?)

/**
 * 一格两个手势：**点一下 = 看这张的大图**（与详情页同一套看图页）；**长按 = 问这张怎么办**（用作首图 / 移除）。
 * 分成两个手势是因为后果不同：看只是看，而移除会改草稿——前者不该需要确认，后者不该一下就做。
 * 不在格子上再压一枚 ✕：格子只有一百来 dp，角上叠一个按钮既难看又容易点错。整格一个动作之后，
 * 误触的代价也只是「再多点一下确认」——草稿里的删改在保存前不落盘。
 * 格子间距取 [FreshNowSpacing.xs]（8dp）：M3 布局规范给 compact（手机）的网格槽间距就是 8dp，
 * medium 16dp、large 以上 24dp。它不是「拼接块之间那条缝」（[FreshNowSpacing.xxxs]），
 * 那一条只用来把同一组里的填充块拼在一起。
 * 用 LazyVerticalGrid 而不是手写的 Column + Row：删掉中间一张时，剩下的格子要有位移与淡出动画
 * （`Modifier.animateItem`），那套动画只有惰性布局给得了。它自己不许滚（`userScrollEnabled = false`）：
 * 这个组件嵌在一个本身会滚动的表单里，抢走手势就会让表单卡住。
 */
@Composable
internal fun PhotoGrid(
    items: List<PhotoGridItem>,
    onOpen: (Int) -> Unit,
    onActions: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 每一格额外的修饰（看图的共享元素就挂在它上面）。默认什么都不加：组件不该知道调用方
     * 在用什么过渡。
     */
    tileModifier: @Composable (Int) -> Modifier = { Modifier }
) {
    val showAdd = items.size < MAX_RECORD_IMAGES
    val cellCount = items.size + if (showAdd) 1 else 0
    if (cellCount == 0) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 三列等宽：总宽减去两条缝再三等分。高度得自己算出来——惰性网格不接受无界高度，
        // 而这里又不许它滚，只能把行数换成固定高度
        val gap = FreshNowSpacing.xs
        val cell = (maxWidth - gap * (COLUMNS - 1)) / COLUMNS
        val rows = (cellCount + COLUMNS - 1) / COLUMNS
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
                PhotoTile(
                    item = item,
                    onOpen = { onOpen(index) },
                    onActions = { onActions(index) },
                    // 增删时的淡入淡出与其余格子的让位都由它演；共享元素接在它之后
                    modifier = Modifier.animateItem().then(tileModifier(index))
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
 * 一格照片。
 *
 * 语义分两层：格子的描述说的是「这里是什么」（照片 / 占位图），动作的描述说的是「点了会怎样」
 * （看大图）、「长按会怎样」（照片操作）。两层都要有——只说动作的话，无障碍读屏读到的是
 * 「查看大图、查看大图…」一串，听不出格子里到底有没有图；只说内容的话，用户又不知道为什么它能点。
 */
@Composable
private fun PhotoTile(
    item: PhotoGridItem,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openLabel = stringResource(R.string.record_image_open)
    val actionsLabel = stringResource(R.string.record_image_actions)
    val tileLabel = stringResource(
        if (item.image != null) R.string.record_image else R.string.record_image_missing
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onActions,
                onClickLabel = openLabel,
                onLongClickLabel = actionsLabel
            )
            .semantics { contentDescription = tileLabel }
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
