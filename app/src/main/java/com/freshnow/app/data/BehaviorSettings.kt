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
     * 「补充信息」这一个开关。它管两件事：
     *
     * 一是扫描页保存之后要不要顺路去补充信息页（见 ScanScreen）；
     * 二是位置这类手填字段要不要在详情页与编辑页出现。两件事是同一个开关的两面：
     * 关着时没人能给这一项赋值，还把它摆出来给人看，就是一栏填不了的东西。
     *
     * 默认开：这个开关就是为「扫完接着填」而生的，默认关掉等于要先找到它才能用上。
     * 关掉时扫描页与加这一项之前完全一样：一颗按钮、存完就退。已经填过的位置不会被删，
     * 只是不再显示；再打开就还在。
     */
    val supplementAfterSave: Boolean = true,

    /**
     * 列表的排序方式。默认按创建时间——那是列表本来的顺序（查库就是 savedAt 倒序），
     * 换成别的等于改了「刚存的那条在哪」，该由用户自己选。
     */
    val sortOrder: SortOrder = SortOrder.CREATED_AT
)
