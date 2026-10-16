package com.freshnow.app.data

/**
 * 主页列表的排序方式。用户能选的就这两种，界面、落盘、排序三处都按它走。
 */
enum class SortOrder {
    /** 新存的在前（按保存时间倒序） */
    CREATED_AT,

    /** 快到期的在前（过期日期升序，已经过期的因此排在最上面） */
    EXPIRY_DATE
}

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
    val manualEntry: Boolean = false,

    /**
     * 扫描页保存之后直接进这条记录的表单补填（见 ScanScreen）。
     *
     * 默认开：这个开关就是为「扫完接着填位置」而生的，默认关掉等于要先找到它才能用上。
     * 关掉时扫描页与加这一项之前完全一样：一颗按钮、存完就退。
     */
    val supplementAfterSave: Boolean = true,

    /**
     * 列表的排序方式。默认按创建时间——那是列表本来的顺序（查库就是 savedAt 倒序），
     * 换成别的等于改了「刚存的那条在哪」，该由用户自己选。
     */
    val sortOrder: SortOrder = SortOrder.CREATED_AT
)
