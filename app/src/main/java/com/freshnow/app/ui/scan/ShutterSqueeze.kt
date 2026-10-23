package com.freshnow.app.ui.scan

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.freshnow.app.ui.theme.FreshNowTransitions

/**
 * 按下快门那一下，取景框先收一下再弹回。
 * 【这是「我拍到了」的回执，不是「请等待」】相机的帧一直流着，快门只是取下一帧，从按到结果只有几十毫秒
 * （见 PhotoCaptureScreen 的注释）。给它配一句「正在拍摄…」等于把一段看不见的等待变成看得见的。
 * [trigger] 每按一次加一，用计数而不是布尔：连着按两下也各演一次。首次合成（trigger 为 0）不演。
 * 收多少见 [FreshNowTransitions.SHUTTER_SQUEEZE_SCALE]，回弹取设计源里那族弹簧的 fastSpatial，不另挑参数。
 */
@Composable
internal fun Modifier.shutterSqueeze(trigger: Int): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        // 先直接落到收紧的那一档，再交给弹簧弹回：按下是「一下」，不是「渐变着收」
        scale.snapTo(FreshNowTransitions.SHUTTER_SQUEEZE_SCALE)
        scale.animateTo(1f, FreshNowTransitions.fastSpatial())
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
