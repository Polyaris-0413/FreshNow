package com.freshnow.app.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowSpacing
import com.freshnow.app.ui.theme.FreshNowTransitions

/**
 * 取景框左下角那枚状态胶囊。
 * 底色取容器色而不是半透明：背景是相机画面，也就是一张不归我们管的照片——拍牛奶盒是浅底，拍酱油瓶可能是深底。
 * 半透明底压上去，字要跟照片挣边界；实心容器色的取值来自主题，深浅两套各对一套，完全不看照片脸色。
 * 位置与手电筒按钮对称：那个在右上角，这个在左下角，边距都取 [FreshNowSpacing.xs]。
 * 不做 liveRegion：这句话每次变化都会打断读屏用户，而它说的东西下面那四个字段本来就会读出来。
 */
@Composable
internal fun ScanStatusChip(status: ScanStatus, modifier: Modifier = Modifier) {
    // 退场那 200ms 里 status 已经回到 Hidden（例如记录刚被清空），但这一句还得接着演完，
    // 所以文本不能照着当下的 status 现算
    val latest = remember { mutableStateOf<ScanStatus?>(null) }
    if (status != ScanStatus.Hidden) latest.value = status

    AnimatedVisibility(
        visible = status != ScanStatus.Hidden,
        enter = fadeIn(FreshNowTransitions.stateChange()),
        exit = fadeOut(FreshNowTransitions.stateChange()),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            val text = when (val current = latest.value) {
                is ScanStatus.Found -> stringResource(R.string.scan_status_found, current.count)
                else -> stringResource(R.string.scan_status_recognizing)
            }
            // 文案长度会变（「正在识别…」↔「找到 N 项」），胶囊宽度跟着变：用带 SizeTransform
            // 的那个，尺寸变化跟同一档时长走，而不是 Compose 默认的 spring
            AnimatedContent(
                targetState = text,
                transitionSpec = { FreshNowTransitions.fadeSwapAnimatedSize() },
                label = "scanStatus"
            ) { current ->
                Text(
                    text = current,
                    modifier = Modifier.padding(
                        horizontal = FreshNowSpacing.xs,
                        vertical = FreshNowSpacing.xxs
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}
