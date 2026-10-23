package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowTransitions

/**
 * 记录里的扫描照片，详情页与编辑页共用（编辑页换照片时要当场看出换成了哪张）。
 *
 * 照片在采集或裁剪时就已按正方形落成（见 ScanImageCodec），这里按图片自身比例铺满宽度即可，
 * 不做二次裁剪——旧记录里可能存着更早期未裁方的图，同样按原比例显示。
 *
 * 没图时（保存时没有可用画面，或图片落盘、解码失败，以及用户刚移除了照片）用占位图标顶上去。
 * 占位块与照片同一尺寸（正方形），不另给一个高度：同一页里「有图 / 没图」不该换一套版式，
 * 翻到下一条记录时照片区的高度也不该跳。
 *
 * 三种变化里只有「移除照片」演淡化（占位与照片交叉），另两种直接切，理由见正文那处注释。
 *
 * [onClick] 非 null 时整块可点（编辑页用它换照片）：点击区就在照片本身，与文本框点哪里都能改
 * 是同一套操作习惯；[onClickLabel] 必须给，读屏软件会把这块读成一个只有内容描述、没有动作的
 * 图片，不说明点了会怎样。水波纹挂在下述 clip 之后才能被圆角裁开。
 *
 * [contentScale] 默认按宽度铺满：详情页上它不是一块定高的东西，图多高就占多高。放进定尺寸的
 * 框里时要传 [ContentScale.Crop]——那时宽高都由框说了算，不裁就只能溢出，而溢出的那一半会被
 * clip 切掉、只留顶上一条（编辑页那枚 48dp 缩略图就是这么用的）。
 *
 * [shape] 默认取卡片类容器的 medium：详情页上这整块就是页面里的一个容器。放进小尺寸的地方要换更
 * 小的一档——圆角是绝对値，48dp 的方块配 12dp 圆角看上去整个角都圆掉了；编辑页那枚缩略图取
 * extraSmall，跟它所在的那块框同一个圆角。
 *
 * [placeholderIconSize] 默认取 [FreshNowSize.icon]（48dp，整屏空态那一档）。小格子要显式换小：
 * 48dp 的图标摆进 48dp 的方块里会顶满整格，淡化时看上去就像一巴掌扇进去（见 [FreshNowSize.smallIcon]）。
 */
@Composable
fun ScanPhoto(
    image: Bitmap?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentScale: ContentScale = ContentScale.FillWidth,
    shape: Shape = MaterialTheme.shapes.medium,
    placeholderIconSize: Dp = FreshNowSize.icon
) {
    // contentDescription 要在 clickable 之前取：下面那段 lambda 不是 composable，里面取不了资源
    val missingLabel = stringResource(R.string.record_image_missing)
    val photoModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .then(
            if (onClick == null) {
                Modifier
            } else {
                Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
            }
        )

    // 只有「移除照片」这一向演淡化，其余直接切。
    // 移除是编辑页里就地发生的页内变化（removeImage 只改草稿，页不换），没有转场盖着，硬切会「啪」地闪一下，
    // 所以给它页内状态切换那一档——与列表里条目被删掉用的是同一档。
    // 另外两向都发生在整页或整步转场里，再叠一层淡化等于叠在自己身上；而且照片解码只要十几毫秒，
    // 给「出现」加淡化会把一段看不见的等待拉长成看得见的（试过一版，真机上反而更差）。
    // 判据看目标值是不是 null：目标为 null 只可能是「移除」这一向。
    Crossfade(
        targetState = image,
        animationSpec = if (image == null) FreshNowTransitions.stateChange() else snap(),
        label = "photo"
    ) { current ->
        if (current == null) {
            Box(
                // 语义挂在整块上，里面的图标只是装饰（与有图那一支对等：那边也是整块叫「扫描照片」）。
                // 挂到图标上的话，读屏软件读的是那一枚 48dp 的图标，而这一块正方形的占位区没有名字
                modifier = photoModifier
                    .aspectRatio(1f)
                    .semantics { contentDescription = missingLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_no_image),
                    contentDescription = null,
                    modifier = Modifier.size(placeholderIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Image(
                bitmap = remember(current) { current.asImageBitmap() },
                contentDescription = stringResource(R.string.record_image),
                modifier = photoModifier,
                contentScale = contentScale
            )
        }
    }
}
