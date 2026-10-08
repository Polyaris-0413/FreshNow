package com.freshnow.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * 相机权限的状态机。扫描页与拍照页都要这一套，所以放在一处，两页的判定不会分叉。
 *
 * [blocked] 的含义是「系统弹窗已经不会再出现」：rationale 为 false 说明用户已拒两次、
 * 或已被策略禁止（见 Android 关于 shouldShowRequestPermissionRationale 的说明），
 * 此时再调请求不会有任何反应，只能引导用户去设置页。
 *
 * 身份不重要（每帧重组都可能换一个实例，字段都是 val），持有者只读它的字段与 [request]。
 */
@Stable
class CameraPermissionState internal constructor(
    /** 权限已授予 */
    val granted: Boolean,
    /** 请求已发过、且系统不会再弹窗。文案与动作都要据此换一套 */
    val blocked: Boolean,
    /** 请求一次（或再请求一次） */
    val request: () -> Unit
)

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // 请求已发出、结果还没到。首次进入会弹一次，这段时间里系统弹窗正盖在界面上，
    // 不能把「未授予且系统不再弹窗」判成永久拒绝
    var awaitingAnswer by remember { mutableStateOf(!granted) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        awaitingAnswer = false
    }

    // 权限也能在系统设置里改，回来时以真实权限为准（从设置页开完权限回来要立刻恢复取景）
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    return CameraPermissionState(
        granted = granted,
        blocked = !granted && !awaitingAnswer &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false,
        request = {
            awaitingAnswer = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    )
}
