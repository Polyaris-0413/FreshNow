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
 * 走五条主路径：冷启动进首页、扫描页（CameraX 的预览与分析器在工作线程上初始化）、设置页、
 * 同步页、关于页到开源声明页。控件一律按文案与描述符定位，不用坐标——坐标会随屏幕尺寸与
 * 字体缩放失效。
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
            // 首次界面要单独放宽等待，否则会在首帧刚出来前就判超时。
            // 等的是「添加」这个描述符而不是首页标题：标题就是应用名，中文环境下是「食不宜迟」，
            // 按它定位会让脚本随语言变
            awaitDesc(ADD, LAUNCH_TIMEOUT_MS)

            // 扫描页
            clickDesc(ADD)
            awaitText(SCAN_TITLE)
            backToHome()

            // 设置页：「显示思维链」整行可点即切换，点标题与点开关等价
            openOverflow()
            clickText(SETTINGS)
            awaitText(BASIC_CONFIG)
            clickText(SHOW_REASONING)
            // 同步页。进去会起服务端与 mDNS 发现，而在录制机上什么都发现不到也无妨：
            // 这一段录的是界面合成与列表装配，不是网络那一层
            clickText(SYNC_TITLE)
            // 等的是页面上的区块标题，不是某句说明：说明会被改、被删（「其他设备靠配对码与
            // 本机建立连接」就删过一次，当场把这个脚本弄挂了），而区块标题是页面结构的骨架，
            // 它不在就说明本页没进来
            awaitText(SYNC_PAIRED_TITLE)
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

    /**
     * 退回首页。
     *
     * 按到达为准，而不是按固定次数返回（二级页可能叠了两层）。关键是用 [HOME_WAIT_MS] 而不是
     * 1 秒来判「到了没有」：托管虚拟机负载重时，一次无障碍树查询会从百毫秒退化成秒级，
     * 短等待会造成「还没回首页」的假阴性，脚本于是连按返回把应用直接退出到桌面，
     * 最后 30 秒也等不到那个描述符（见 r8-baseline-profile-notes.md 的 A-2）。
     */
    private fun MacrobenchmarkScope.backToHome() {
        repeat(MAX_BACK_STEPS) {
            if (device.wait(Until.hasObject(By.desc(ADD)), HOME_WAIT_MS)) return
            device.pressBack()
            // 扫描页有结果时返回会先问「离开扫描页？」；没有结果时直接退，因此这一支不一定会走到
            if (device.wait(Until.hasObject(By.text(LEAVE_SCAN)), SHORT_TIMEOUT_MS)) {
                clickText(DISCARD)
            }
            device.waitForIdle()
        }
        check(device.wait(Until.hasObject(By.desc(ADD)), HOME_WAIT_MS)) { "回不到首页（等不到描述符「$ADD」）" }
    }

    /**
     * 点击前先等控件出现。
     *
     * 不直接用 `findObject`：上一步的「等文案」只能证明目标页已经在树里，
     * 这一刻转场未必走完、要点的那个控件也未必已经合成出来，一次性查找会拷到空。
     */
    private fun MacrobenchmarkScope.clickText(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), TIMEOUT_MS)
            ?: error("找不到文案为「$text」的控件")
        node.click()
    }

    private fun MacrobenchmarkScope.clickDesc(desc: String) {
        val node = device.wait(Until.findObject(By.desc(desc)), TIMEOUT_MS)
            ?: error("找不到描述符为「$desc」的控件")
        node.click()
    }

    private fun MacrobenchmarkScope.awaitText(text: String, timeoutMs: Long = TIMEOUT_MS) {
        check(device.wait(Until.hasObject(By.text(text)), timeoutMs)) { "等不到文案「$text」" }
    }

    private fun MacrobenchmarkScope.awaitDesc(desc: String, timeoutMs: Long = TIMEOUT_MS) {
        check(device.wait(Until.hasObject(By.desc(desc)), timeoutMs)) { "等不到描述符「$desc」" }
    }

    private companion object {
        const val PACKAGE_NAME = "com.freshnow.app"

        // 文案取自 app/src/main/res/values/strings.xml
        const val MORE_OPTIONS = "更多选项"
        const val ADD = "添加"
        const val SCAN_TITLE = "扫描"
        const val SETTINGS = "设置"
        const val BASIC_CONFIG = "基础配置"
        const val SHOW_REASONING = "显示思维链"
        const val SYNC_TITLE = "局域网同步"
        const val SYNC_PAIRED_TITLE = "已配对设备"
        const val ABOUT = "关于"
        const val LEGAL = "法律信息"
        const val OPEN_SOURCE = "开源声明"
        const val OPEN_SOURCE_ITEM = "book-story"
        const val LEAVE_SCAN = "离开扫描页？"
        const val DISCARD = "不保存"

        const val TIMEOUT_MS = 30_000L
        const val LAUNCH_TIMEOUT_MS = 90_000L
        const val SHORT_TIMEOUT_MS = 1_000L

        /** 判「到首页了没有」的等待：给足，免得无障碍树慢一次就被当成没到而多按返回 */
        const val HOME_WAIT_MS = 10_000L
        const val MAX_BACK_STEPS = 3
    }
}
