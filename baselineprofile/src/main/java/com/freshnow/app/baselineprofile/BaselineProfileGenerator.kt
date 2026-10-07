package com.freshnow.app.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
            awaitText(HOME_TITLE)

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

    private fun MacrobenchmarkScope.openOverflow() {
        clickDesc(MORE_OPTIONS)
        // 面板是弹出后才可点的，等它展开再点里面的项
        awaitText(SETTINGS)
    }

    /** 退回首页：二级页可能叠了两层（关于 → 开源声明），所以按到达为准，而不是按固定次数返回 */
    private fun MacrobenchmarkScope.backToHome() {
        repeat(MAX_BACK_STEPS) {
            if (device.wait(Until.hasObject(By.desc(ADD)), SHORT_TIMEOUT_MS)) return
            device.pressBack()
            // 扫描页有结果时返回会先问「离开扫描页？」；没有结果时直接退，因此这一支不一定会走到
            if (device.wait(Until.hasObject(By.text(LEAVE_SCAN)), SHORT_TIMEOUT_MS)) {
                clickText(DISCARD)
            }
        }
        awaitDesc(ADD)
    }

    private fun MacrobenchmarkScope.clickText(text: String) {
        val node = device.findObject(By.text(text)) ?: error("找不到文案为「$text」的控件")
        node.click()
    }

    private fun MacrobenchmarkScope.clickDesc(desc: String) {
        val node = device.findObject(By.desc(desc)) ?: error("找不到描述符为「$desc」的控件")
        node.click()
    }

    private fun MacrobenchmarkScope.awaitText(text: String) {
        check(device.wait(Until.hasObject(By.text(text)), TIMEOUT_MS)) { "等不到文案「$text」" }
    }

    private fun MacrobenchmarkScope.awaitDesc(desc: String) {
        check(device.wait(Until.hasObject(By.desc(desc)), TIMEOUT_MS)) { "等不到描述符「$desc」" }
    }

    private companion object {
        const val PACKAGE_NAME = "com.freshnow.app"

        // 文案取自 app/src/main/res/values/strings.xml
        const val HOME_TITLE = "FreshNow"
        const val MORE_OPTIONS = "更多选项"
        const val ADD = "添加"
        const val SCAN_TITLE = "扫描"
        const val SETTINGS = "设置"
        const val BASIC_CONFIG = "基础配置"
        const val SHOW_REASONING = "显示思维链"
        const val ABOUT = "关于"
        const val LEGAL = "法律信息"
        const val OPEN_SOURCE = "开源声明"
        const val OPEN_SOURCE_ITEM = "book-story"
        const val LEAVE_SCAN = "离开扫描页？"
        const val DISCARD = "不保存"

        const val TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 1_000L
        const val MAX_BACK_STEPS = 3
    }
}
