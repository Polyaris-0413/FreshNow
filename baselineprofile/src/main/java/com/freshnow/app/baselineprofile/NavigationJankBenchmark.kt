package com.freshnow.app.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「主页 → 扫描页」这段跳转的掉帧测量。
 *
 * 用 [FrameTimingMetric] 采集 measureBlock 期间每一帧的耗时，看两件事：
 * frameOverrunMs 是否为负（正数即该帧超出了它的 deadline，也就是掉帧），
 * 以及 frameDurationCpuMs 的高分位（P90/P99）有多贴 16.7 ms 这条 60 Hz 的线。
 *
 * 不测应用启动：startupMode 取 WARM、每轮由 setupBlock 退回主页，进程始终存活，
 * 这样量到的才是「页面跳转」本身，而不是「冷启动 + 首屏」。编译模式取带 baseline profile
 * 的那档，因为它才是 release 包实际所处的情形。
 *
 * 两条路径一起量：扫描页是应用里最重的一次跳转（进页即起 CameraX 预览与每两秒一次的取帧分析），
 * 设置页是长短相仿、但不碰相机的普通跳转。只看扫描页的绝对数字说明不了问题——
 * 托管虚拟机是软件 GPU，什么页面都掉；要拿扫描页跟同样跑在本机上的设置页比，
 * 差值才是扫描页自己多出来的开销。
 */
@RunWith(AndroidJUnit4::class)
class NavigationJankBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeToScan() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            // 扫描页进入时会自己申请相机权限；停在系统弹窗上，量到的就不是页面本身了。
            // 每轮都授一次：命令幂等，而已授权时应用不会再去申请
            device.executeShellCommand("pm grant $PACKAGE_NAME android.permission.CAMERA")
            pressHome()
            startActivityAndWait()
            // 与录制脚本同一理由：载体是没经 R8 收缩的构建，首次界面要放宽等待
            awaitDesc(ADD, LAUNCH_TIMEOUT_MS)
        }
    ) {
        clickDesc(ADD)
        awaitText(SCAN_TITLE)
        // 转场与首帧都在被测范围内，要等界面真正静止再收尾，否则会在动画中途停止采集
        device.waitForIdle()
    }

    @Test
    fun homeToSettings() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            awaitDesc(ADD, LAUNCH_TIMEOUT_MS)
        }
    ) {
        openOverflow()
        clickText(SETTINGS)
        awaitText(BASIC_CONFIG)
        device.waitForIdle()
    }

    private companion object {
        // 帧时序噪声大，官方建议至少 5 轮。一次跳转约 3 秒，取 10 轮不至于把录制任务拖长多少
        const val ITERATIONS = 10
    }
}
