package com.freshnow.app.ui.scan

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.theme.FreshNowTransitions

/**
 * 手电筒开关：扫描页与拍照页共用，叠在取景框内角（它是相机的配件，贴在画面上才读得出属于相机）。
 *
 * FilledIconToggleButton 自带的选中态配色是瞬时切换的，而这里要的是颜色过渡：选中与未选中
 * 两组色都喂同一个动画值，开关语义仍由 checked 提供，颜色由我们演。两端取值就是该组件的 token
 * （未选中 = secondaryContainer 底 + primary 图标，选中 = primary 底 + onPrimary 图标），
 * 见 FilledIconButtonTokens。
 *
 * 调用方只在相机真的有闪光灯时才调用它：无灯设备上 enableTorch 只会失败。
 */
@Composable
fun CameraTorchButton(
    torchOn: Boolean,
    onTorchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (torchOn) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = FreshNowTransitions.stateChange(),
        label = "flashlightContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (torchOn) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = FreshNowTransitions.stateChange(),
        label = "flashlightContentColor"
    )
    FilledIconToggleButton(
        checked = torchOn,
        onCheckedChange = onTorchChange,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            checkedContainerColor = containerColor,
            checkedContentColor = contentColor,
        ),
        modifier = modifier
    ) {
        // 开与关共用同一个图标，状态只由上面的颜色表达
        Icon(
            painter = painterResource(R.drawable.ic_flashlight_on),
            contentDescription = stringResource(
                if (torchOn) R.string.scan_flashlight_off else R.string.scan_flashlight_on
            )
        )
    }
}
