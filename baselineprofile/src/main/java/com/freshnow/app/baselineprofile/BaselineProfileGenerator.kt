package com.freshnow.app.baselineprofile

import android.util.Log
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
 * 走六条主路径：首次启动的欢迎页（引导）、冷启动进首页、扫描页（CameraX 的预览与分析器在工作线程上
 * 初始化）、设置页、同步页、关于页到开源声明页。控件一律按文案与描述符定位，不用坐标——坐标会随
 * 屏幕尺寸与字体缩放失效。
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
            // 每轮录制都是重装后的干净包，所以本轮首屏是欢迎页——但只有**第一次**启动才是：
            // 引导走完会写下「已看过」标记，而录制要跑多轮（本次配置下 10 轮），后面几轮直接进首页。
            // 所以不能无脑等欢迎页：先看哪种首屏在，再决定要不要引导；两种都没看到就不插手，
            // 交给下面那个等「添加」去报真实的错。
            if (awaitFirstScreen(LAUNCH_TIMEOUT_MS) == FirstScreen.Welcome) {
                walkThroughWelcome()
            }
            awaitDesc(ADD, LAUNCH_TIMEOUT_MS)

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

            openOverflow()
            clickText(ABOUT)
            awaitText(LEGAL)
            clickText(OPEN_SOURCE)
            awaitText(OPEN_SOURCE_ITEM)
            backToHome()
        }
    }

    /**
     * 走完首次启动的欢迎页（引导）。
     *
     * 由调用方判过「这一轮确实在欢迎页」才调；**不能无脑调**——引导走完会写下标记，
     * 同一台设备上后面几轮录制直接进首页（见 generate 里那段判断）。
     *
     * 每一步用**按钮**确认走到位，不靠各步标题的文案：标题改文案时会挂（同步页就吃过这个亏），
     * 而引导每步能点的按钮是固定的——「上一步」从第 2 步起才有，「跳过」只在配置与权限那两步
     * 出现，收尾页是「开始使用」。中间不能省等待：翻页是动画，`currentPage` 过半才变，
     * 不等就再点一次「下一步」，那一下会落在原来那一步上。
     *
     * 调用前已经 `pm grant` 过相机权限，所以权限页不会弹系统窗，点它只是把回调叫回来；
     * 这里仍然点一下，因为进引导时权限就在时应用不会自动跳过那一步（那是有意的，见 WelcomeScreen）。
     */
    private fun MacrobenchmarkScope.walkThroughWelcome() {
        // 首帧刚出来时按钮未必已经合成出来，而托管虚拟机负载重时取一次无障碍树会退化成秒级
        // （文档附录 A-2），所以这里跟首帧一样给足等待，不另用 30 秒那一档
        clickText(WELCOME_NEXT, LAUNCH_TIMEOUT_MS)
        awaitText(WELCOME_PREVIOUS)
        clickText(WELCOME_NEXT)
        awaitText(WELCOME_SKIP)
        clickText(WELCOME_SKIP)
        clickText(WELCOME_GRANT_CAMERA)
        awaitText(WELCOME_START, LAUNCH_TIMEOUT_MS)
        clickText(WELCOME_START)
    }

    /**
     * 等两种首屏中的一种出现。
     * 不用两段串行的 `wait`：那等于给每一种首屏各自一个窗口，而慢设备上第一个窗口就耗光了整个判定期，
     * 第二个窗口开始时应用才刚画完（2026-10-20 就挂在这里：GMD 上 `Displayed` 花了 10.4 秒，两段各 10 秒的
     * 探针全空，于是既没走引导又等不到首页的「添加」）。现在是一个窗口内轮询两个信号，谁先到算谁。
     */
    private fun MacrobenchmarkScope.awaitFirstScreen(timeoutMs: Long): FirstScreen? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.desc(ADD))) return FirstScreen.Home
            if (device.hasObject(By.text(WELCOME_NEXT))) return FirstScreen.Welcome
            Thread.sleep(PROBE_POLL_MS)
        }
        return null
    }

    private fun MacrobenchmarkScope.openOverflow() {
        clickDesc(MORE_OPTIONS)
        // 面板是弹出后才可点的，等它展开再点里面的项
        awaitText(SETTINGS)
    }

    /**
     * 退回首页。
     * 按到达为准，而不是按固定次数返回（二级页可能叠了两层）。判「到了没有」用 [HOME_WAIT_MS] 而不是 1 秒：
     * 托管虚拟机负载重时，一次无障碍树查询会从百毫秒退化成秒级，短等待会造成「还没回首页」的假阴性，
     * 脚本于是连按返回把应用直接退出到桌面（见 docs/r8-baseline-profile-notes.md 的 A-2）。
     */
    private fun MacrobenchmarkScope.backToHome() {
        repeat(MAX_BACK_STEPS) {
            if (device.wait(Until.hasObject(By.desc(ADD)), HOME_WAIT_MS)) return
            // 优先点应用内的返回箭头：模拟器用全面屏手势，系统返回要走一枚边缘手势、经 SystemUI，
            // 设备过载时那条路会卡住（2026-10-20：back 事件在 systemui 里积压到 ANR，连按三次
            // 都没退出去，而页面自己的返回按钮只是一次普通点击）。扫描页没有这个箭头，再退回系统返回。
            device.findObject(By.desc(BACK))?.click() ?: device.pressBack()
            // 扫描页有结果时返回会先问「离开扫描页？」；没有结果时直接退，因此这一支不一定会走到
            if (device.wait(Until.hasObject(By.text(LEAVE_SCAN)), SHORT_TIMEOUT_MS)) {
                clickText(DISCARD)
            }
            device.waitForIdle()
        }
        if (!device.wait(Until.hasObject(By.desc(ADD)), HOME_WAIT_MS)) {
            reportScreen("回不到首页")
        }
        check(device.wait(Until.hasObject(By.desc(ADD)), HOME_WAIT_MS)) { "回不到首页（等不到描述符「$ADD」）" }
    }

    /**
     * 点击前先等控件出现。
     *
     * 不直接用 `findObject`：上一步的「等文案」只能证明目标页已经在树里，
     * 这一刻转场未必走完、要点的那个控件也未必已经合成出来，一次性查找会拷到空。
     */
    private fun MacrobenchmarkScope.clickText(text: String, timeoutMs: Long = TIMEOUT_MS) {
        val node = device.wait(Until.findObject(By.text(text)), timeoutMs)
            ?: error("找不到文案为「$text」的控件")
        node.click()
    }

    private fun MacrobenchmarkScope.clickDesc(desc: String) {
        val node = device.wait(Until.findObject(By.desc(desc)), TIMEOUT_MS)
            ?: error("找不到描述符为「$desc」的控件")
        node.click()
    }

    private fun MacrobenchmarkScope.awaitText(text: String, timeoutMs: Long = TIMEOUT_MS) {
        if (!device.wait(Until.hasObject(By.text(text)), timeoutMs)) {
            reportScreen("等不到文案「$text」")
            error("等不到文案「$text」")
        }
    }

    private fun MacrobenchmarkScope.awaitDesc(desc: String, timeoutMs: Long = TIMEOUT_MS) {
        if (!device.wait(Until.hasObject(By.desc(desc)), timeoutMs)) {
            reportScreen("等不到描述符「$desc」")
            error("等不到描述符「$desc」")
        }
    }

    /**
     * 失败时把当时的窗口树写进 logcat。
     *
     * 托管虚拟机是测完即抛的：截图与 `/sdcard` 下的文件都随设备一起消失，而**只有 logcat 会被
     * GMD 收回来**。没有这一行，失败现场就只能靠猜（2026-10-20 连挂四次，前三次都只能从
     * `Displayed` 与点击日志倒推屏幕状态）。
     */
    private fun MacrobenchmarkScope.reportScreen(why: String) {
        val tree = runCatching {
            device.executeShellCommand("uiautomator dump /sdcard/_bp_fail.xml >/dev/null 2>&1; cat /sdcard/_bp_fail.xml")
        }.getOrNull()
        Log.w(LOG_TAG, "失败现场（$why）：${tree?.take(SCREEN_DUMP_LIMIT)}")
    }

    /** 启动后第一屏是哪一个；null 表示窗口内两种都没看到 */
    private enum class FirstScreen { Home, Welcome }

    private companion object {
        const val PACKAGE_NAME = "com.freshnow.app"

        // 文案取自 app/src/main/res/values/strings.xml
        const val MORE_OPTIONS = "更多选项"
        const val ADD = "添加"
        const val BACK = "返回"
        const val SCAN_TITLE = "扫描"
        const val SETTINGS = "设置"
        const val BASIC_CONFIG = "基础配置"
        const val SHOW_REASONING = "显示思维链"
        const val SYNC_TITLE = "局域网同步"
        const val SYNC_PAIRED_TITLE = "已配对设备"
        const val ABOUT = "关于"
        const val LEGAL = "法律信息"
        const val OPEN_SOURCE = "开源声明"

        // 开源声明页首屏的第一行：等到它就说明清单已经渲染（那一页没有别的结构骨架可等）。
        // 取的是构建期清单里的真实条目名（androidx.activity:activity 的 <name>，字母序之首），
        // 不是归组名——曾经写的是「Android CameraX」，那是手写归并时代的展示名，改成逐条列出后
        // 它就永远等不到了（2026-10-20 因此挂过一次）。
        const val OPEN_SOURCE_ITEM = "Activity"
        const val LEAVE_SCAN = "离开扫描页？"
        const val DISCARD = "不保存"

        // 欢迎页的出口按钮。引导每步能点什么是固定的：第 1 步只有「下一步」，「上一步」从第 2 步起
        // 才有，「跳过」只在配置与权限那两步出现，权限页是「允许使用相机」，收尾页是「开始使用」
        const val WELCOME_NEXT = "下一步"
        const val WELCOME_PREVIOUS = "上一步"
        const val WELCOME_SKIP = "跳过"
        const val WELCOME_GRANT_CAMERA = "允许使用相机"
        const val WELCOME_START = "开始使用"

        const val TIMEOUT_MS = 30_000L
        const val LAUNCH_TIMEOUT_MS = 90_000L
        const val SHORT_TIMEOUT_MS = 1_000L

        /** 失败现场写日志的上限：logcat 单条消息太长会被截断，留最前面的层次结构就够看首屏 */
        const val SCREEN_DUMP_LIMIT = 4_000
        const val LOG_TAG = "FreshNowBaselineProfile"

        /**
     * 探两次无障碍树之间的间隔。托管虚拟机负载重时一次查询能到秒级，密了只是白烧 CPU
     */
        const val PROBE_POLL_MS = 500L

        /** 判「到首页了没有」的等待：给足，慢设备上触摸事件本身就能拖到秒级 */
        const val HOME_WAIT_MS = 20_000L
        const val MAX_BACK_STEPS = 3
    }
}
