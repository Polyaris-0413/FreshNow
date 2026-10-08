package com.freshnow.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 录制 FreshNow 的 baseline profile。
 *
 * 走四条主路径：冷启动进首页、扫描页（CameraX 的预览与分析器在工作线程上初始化）、设置页、
 * 关于页到开源声明页。控件一律按文案与描述符定位，不用坐标——坐标会随屏幕尺寸与字体缩放失效。
 *
 * 与 app/src/androidTest 下的界面测试不同，这里驱动的是装好的真实应用，会读写它自己的
 * DataStore（例如「显示思维链」被真的切换一次）。因此只能在一次性虚拟机上跑。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            // 扫描页进入时会自己申请相机权限；不先授的话会停在权限提示上，录不到相机那一段。
            // 每轮录制都是重装后的干净包，权限会丢，所以每轮都授一次。
            device.executeShellCommand("pm grant $PACKAGE_NAME android.permission.CAMERA")

            pressHome()
            startActivityAndWait()
            // 录制载体是没经 R8 收缩的 nonMinified 构建，冷启动比 release 慢得多，
            // 首次界面要单独放宽等待，否则会在首帧刚出来前就判超时
            awaitText(HOME_TITLE, LAUNCH_TIMEOUT_MS)

            // 扫描页
            clickDesc(ADD)
            awaitText(SCAN_TITLE)
            backToHome()

            // 设置页：「显示思维链」整行可点即切换，点标题与点开关等价
            openOverflow()
            clickText(SETTINGS)
            awaitText(BASIC_CONFIG)
            clickText(SHOW_REASONING)
            backToHome()

            // 关于页 → 开源声明页
            openOverflow()
            clickText(ABOUT)
            awaitText(LEGAL)
            clickText(OPEN_SOURCE)
            awaitText(OPEN_SOURCE_ITEM)
            backToHome()
        }
    }
}

