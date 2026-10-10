package com.freshnow.app.ui.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.openAppSettings
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 取景框里装的东西：相机预览、手电筒开关，以及没有相机权限时的空态。
 *
 * 扫描页与拍照页的取景框装的是同一套，只有两处分岔：帧什么时候要（扫描页每帧都送，拍照页只在
 * 按下快门那一帧）、以及权限文案（两页的用途不同）。那两个由参数给，外壳（多大、是不是方、圆角与
 * 底色）由调用方给，这里只管内容。
 *
 * 合在一处是因为分支不少且每条都有来由：无闪光灯的设备不给按钮、权限被永久拒绝时改跳系统设置。
 * 各写一遍迟早只改到一边，而错的那一边表现为「按钮点了没反应」这种查不出来的毛病。
 *
 * 手电筒可用性是设备属性，要问一次相机才知道，因此状态留在这里——调用方只持有「灯亮没亮」。
 */
@Composable
internal fun CameraViewport(
    permission: CameraPermissionState,
    torchOn: Boolean,
    onTorchChange: (Boolean) -> Unit,
    canAcceptFrame: () -> Boolean,
    onFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    /** 未授予权限时的说明。各页的用途不同（扫描 / 拍照），措辞跟着不同 */
    requiredMessage: String = stringResource(R.string.scan_permission_required)
) {
    val context = LocalContext.current
    var torchAvailable by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (permission.granted) {
            CameraPreview(
                torchOn = torchOn,
                onTorchAvailabilityChange = { torchAvailable = it },
                canAcceptFrame = canAcceptFrame,
                onFrame = onFrame,
                modifier = Modifier.fillMaxSize()
            )
            // 权限未授予时相机根本不存在，也就谈不上开灯，按钮同样不给
            if (torchAvailable) {
                CameraTorchButton(
                    torchOn = torchOn,
                    onTorchChange = onTorchChange,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(FreshNowSpacing.xs)
                )
            }
        } else {
            CameraPermissionHint(
                permissionBlocked = permission.blocked,
                requiredMessage = requiredMessage,
                // 被永久拒绝时再调请求不会有任何反应，改跳系统设置页
                onGrantPermission = {
                    if (permission.blocked) openAppSettings(context) else permission.request()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
