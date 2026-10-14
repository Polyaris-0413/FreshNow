package com.freshnow.app.ui

import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import kotlin.math.abs

/**
 * 动画中途的取样家伙，供各页的测试共用。
 *
 * 为什么这类判据只能靠像素：**淡出中的条目已经不在语义树里了**。列表条目的出现与消失由
 * `Modifier.animateItem` 演，条目一走，语义节点当场摘掉，留下的只是还在画的那一层——于是
 * 「半程上它既不是旧样子也不是新样子」这种话，`onNodeWithText` 这类断言根本问不出来，只能去数像素。
 *
 * 判据一律比「通道差」而不比具体颜色：配色是设计源的事，测试不该把某组色值写进断言。
 */
internal const val CHANNEL_TOLERANCE = 8

internal fun channelDifference(a: Int, b: Int): Int = maxOf(
    abs(Color.red(a) - Color.red(b)),
    abs(Color.green(a) - Color.green(b)),
    abs(Color.blue(a) - Color.blue(b))
)

/** 两个颜色是否看得出差别：通道差超过容差即可 */
internal fun differs(a: Int, b: Int): Boolean = channelDifference(a, b) > CHANNEL_TOLERANCE

/** 该区域里与页面底色差得最远的那个像素差多少：文字淡入时它会从 0 涨到最终值 */
internal fun ComposeTestRule.maxContrast(region: DpRect): Int {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val background = bitmap.getPixel(0, bitmap.height - 1)
    val left = with(density) { region.left.roundToPx() }.coerceIn(0, bitmap.width - 1)
    val top = with(density) { region.top.roundToPx() }.coerceIn(0, bitmap.height - 1)
    val right = with(density) { region.right.roundToPx() }.coerceIn(0, bitmap.width)
    val bottom = with(density) { region.bottom.roundToPx() }.coerceIn(0, bitmap.height)

    var max = 0
    for (y in top until bottom) {
        for (x in left until right) {
            val difference = channelDifference(bitmap.getPixel(x, y), background)
            if (difference > max) max = difference
        }
    }
    return max
}

/** 这条 y 带内有没有画了东西的像素：装饰图形没有语义节点，只能这样验收 */
internal fun ComposeTestRule.drawsSomethingBetween(top: Dp, bottom: Dp): Boolean {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val background = bitmap.getPixel(0, bitmap.height - 1)
    val from = with(density) { top.roundToPx() }.coerceIn(0, bitmap.height - 1)
    val to = with(density) { bottom.roundToPx() }.coerceIn(0, bitmap.height)
    return (from until to).any { y ->
        (0 until bitmap.width).any { x ->
            channelDifference(bitmap.getPixel(x, y), background) > CHANNEL_TOLERANCE
        }
    }
}
