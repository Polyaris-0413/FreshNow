package com.freshnow.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.SquareViewport
import com.freshnow.app.ui.theme.FreshNowSpacing
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 拍一张照片，用于替换记录里的那张。
 *
 * 不做成扫描页的「拍照模式」：扫描页的职责是连续识别，它每一帧都在给 AI 发请求并累加读数，
 * 拍照要的是「按下去才定格一帧」，两者的状态机不是同一件事，塞在一起会让两边都难读。
 *
 * 相机复用 [CameraPreview]，包括它那套「整帧裁成居中正方形 → 长边限到 768 → JPEG」：
 * 所以这里拍到的就是取景框里看到的，规格与扫描页存下的照片完全一致，不必再进裁剪页。
 *
 * 快门 = 取下一帧：相机的帧本来就一直在流，压一个标志位让分析线程只对这一帧做编码，
 * 比另接一条 ImageCapture 通路省事，也避免两条通路各裁一次、各压一次可能出现的画面差异。
 * 帧的延迟是一帧间隔（几十毫秒），肉眼不可感。
 */
@Composable
fun PhotoCaptureScreen(
    onCancel: () -> Unit,
    onCaptured: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val permission = rememberCameraPermissionState()
    // 手电筒是相机的状态而不是页面数据，不进 ViewModel；但旋转会重建 Activity，灯要跟着
    // 用户的意图重新亮起，所以用 rememberSaveable
    var torchOn by rememberSaveable { mutableStateOf(false) }
    // 分析线程读它、主线程写它，故用原子量跨线程传（与 ScanViewModel 表示"请求进行中"同一个理由）
    val shutter = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }

    FreshNowSubPage(
        title = stringResource(R.string.photo_capture_title),
        onBack = onCancel,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(FreshNowSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 快门之外的空间，取景框在其中取正方形：按可用宽度与高度里较小的一边定边，
            // 它因此既保持是方的，又不会顶到快门那一行
            SquareViewport(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CameraViewport(
                    permission = permission,
                    torchOn = torchOn,
                    onTorchChange = { torchOn = it },
                    // 只有按了快门的那一帧才编码：没按下去时帧照常流过，白做编码没意义
                    canAcceptFrame = { shutter.get() },
                    onFrame = { jpeg ->
                        shutter.set(false)
                        onCaptured(jpeg)
                    },
                    requiredMessage = stringResource(R.string.photo_capture_permission_required),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 快门放在取景框外面而不是叠在画面上：它按一下就要离开本页，是页面级的动作，
            // 与「保存」「清空」同档，用填充按钮；叠在画面里反而会让人以为它还能拨动取景
            Button(
                onClick = { shutter.set(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    modifier = Modifier.padding(end = FreshNowSpacing.xs)
                )
                Text(text = stringResource(R.string.action_take_photo))
            }
        }
    }
}
