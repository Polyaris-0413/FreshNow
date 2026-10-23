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
 * 只能数像素：淡出中的条目已不在语义树里，`onNodeWithText` 这类断言问不出来。
 * 判据一律比「通道差」，不把具体色值写进断言——配色是设计源的事。
 */
internal const val CHANNEL_TOLERANCE = 8

internal fun channelDifference(a: Int, b: Int): Int = maxOf(
    abs(Color.red(a) - Color.red(b)),
    abs(Color.green(a) - Color.green(b)),
    abs(Color.blue(a) - Color.blue(b))
)

internal fun differs(a: Int, b: Int): Boolean = channelDifference(a, b) > CHANNEL_TOLERANCE

/** 文字淡入时它会从 0 涨到最终值 */
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

/** 装饰图形没有语义节点，只能这样验收 */
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
