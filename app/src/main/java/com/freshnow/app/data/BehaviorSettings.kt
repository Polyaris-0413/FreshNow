package com.freshnow.app.data

/**
 * 行为：应用该怎么做事，而不是它连到哪儿、怎么连（那些在 [AiSettings]）。
 *
 * 与 [AiSettings] 分开存是为了各管各的默认值：两者唯一的关系是都摆在设置页上，
 * 合在一份里会让「恢复 AI 配置」之类的操作连带碰到行为开关。
 */
data class BehaviorSettings(
    /**
     * 主页的「添加」直接开录入页，跳过相机（见 RecordEditViewModel.startNew）。
     *
     * 默认关：这个应用的主线是扫描，手动录入是给拍不到标签的场景留的旁路，
     * 默认打开就把主线藏起来了。
     */
    val manualEntry: Boolean = false
)
