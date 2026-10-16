package com.freshnow.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.freshnow.app.ui.theme.FreshNowTransitions
import com.freshnow.app.ui.theme.backEnter
import com.freshnow.app.ui.theme.backExit
import com.freshnow.app.ui.theme.forwardEnter
import com.freshnow.app.ui.theme.forwardExit

/** 两个整屏步骤之间的关系：往下一级、退回上一级、平级（互相切换） */
internal enum class StepDirection { FORWARD, BACK, SIDE }

/**
 * 整屏步骤之间的转场。
 *
 * 编辑记录页（表单 / 裁剪 / 拍照）与扫描页（扫描 / 编辑 / 裁剪 / 拍照）都有「一整屏平级替换」这件
 * 事。两处各写一份 AnimatedContent 的话，方向判据迟早会分家——改了这一处、忘了那一处，而表现只是
 * 某一条路径的转场方向反了，很难注意到。
 *
 * [direction] 由调用方给：只有它知道自己的哪两个步骤之间是进、哪个是退。三个方向里
 * [StepDirection.SIDE] 是「平级且不会直接互换」用的（比如裁剪与拍照之间），给它一段淡入淡出，
 * 免得两个整屏硬切。
 *
 * 退场那帧两页同时在、各自半透明地叠着，所以底下要垫一层主题背景色：不垫就会透出窗口背景，
 * 深色模式下表现为一下黑闪（与 NavHost 同一个理由）。
 */
@Composable
internal fun <T> StepTransition(
    step: T,
    direction: (from: T, to: T) -> StepDirection,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                when (direction(initialState, targetState)) {
                    StepDirection.FORWARD -> forwardEnter() togetherWith forwardExit()
                    StepDirection.BACK -> backEnter() togetherWith backExit()
                    StepDirection.SIDE -> FreshNowTransitions.fadeSwap()
                }
            },
            label = label
        ) { current ->
            content(current)
        }
    }
}
