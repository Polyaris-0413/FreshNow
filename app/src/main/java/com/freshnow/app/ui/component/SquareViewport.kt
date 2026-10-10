package com.freshnow.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

/**
 * 居中的正方形视窗：按可用宽度与高度里较小的一边定边，四角与底色与取景框一致。
 *
 * 拍照页的取景框与裁剪页的视窗共用它——两处要的都是「一块方的、能装下整幅画面的框」，
 * 各写一遍的话尺寸算法与外观会分叉，而这两处看起来本就该是同一个东西（裁剪页摆的就是拍照页
 * 拍到的那幅画）。
 *
 * 用 BoxWithConstraints 只是为了量「这块地方有多大」，不是在判窗口尺寸类——本项目不做多窗格
 * （见 ScanScreen 里那条有意偏离 M3 的说明）。因此调用方必须给它一个高度有界的父容器，
 * 无限高（比如放进 verticalScroll）时量不出可用高度。
 */
@Composable
internal fun SquareViewport(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(minOf(maxWidth, maxHeight))
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            content = content
        )
    }
}
