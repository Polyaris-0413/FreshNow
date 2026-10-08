package com.freshnow.app.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * 驱动被测应用时共用的包名、文案与等待辅助。
 *
 * 两处测试（录制 baseline profile 与掉帧测量）都在操作同一批界面，定位方式必须一致：
 * 控件一律按文案与描述符定位，不用坐标——坐标会随屏幕尺寸与字体缩放失效。
 *
 * 文案取值来自 app/src/main/res/values/strings.xml。
 */
internal const val PACKAGE_NAME = "com.freshnow.app"

internal const val HOME_TITLE = "FreshNow"
internal const val MORE_OPTIONS = "更多选项"
internal const val ADD = "添加"
internal const val SCAN_TITLE = "扫描"
internal const val SETTINGS = "设置"
internal const val BASIC_CONFIG = "基础配置"
internal const val SHOW_REASONING = "显示思维链"
internal const val ABOUT = "关于"
internal const val LEGAL = "法律信息"
internal const val OPEN_SOURCE = "开源声明"
internal const val OPEN_SOURCE_ITEM = "book-story"
internal const val LEAVE_SCAN = "离开扫描页？"
internal const val DISCARD = "不保存"

internal const val TIMEOUT_MS = 30_000L
internal const val LAUNCH_TIMEOUT_MS = 90_000L
internal const val SHORT_TIMEOUT_MS = 1_000L
internal const val MAX_BACK_STEPS = 3

internal fun MacrobenchmarkScope.clickText(text: String) {
    val node = device.findObject(By.text(text)) ?: error("找不到文案为「$text」的控件")
    node.click()
}

internal fun MacrobenchmarkScope.clickDesc(desc: String) {
    val node = device.findObject(By.desc(desc)) ?: error("找不到描述符为「$desc」的控件")
    node.click()
}

internal fun MacrobenchmarkScope.awaitText(text: String, timeoutMs: Long = TIMEOUT_MS) {
    check(device.wait(Until.hasObject(By.text(text)), timeoutMs)) { "等不到文案「$text」" }
}

internal fun MacrobenchmarkScope.awaitDesc(desc: String, timeoutMs: Long = TIMEOUT_MS) {
    check(device.wait(Until.hasObject(By.desc(desc)), timeoutMs)) { "等不到描述符「$desc」" }
}

internal fun MacrobenchmarkScope.openOverflow() {
    clickDesc(MORE_OPTIONS)
    // 面板是弹出后才可点的，等它展开再点里面的项
    awaitText(SETTINGS)
}

/** 退回首页：二级页可能叠了两层（关于 → 开源声明），所以按到达为准，而不是按固定次数返回 */
internal fun MacrobenchmarkScope.backToHome() {
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
