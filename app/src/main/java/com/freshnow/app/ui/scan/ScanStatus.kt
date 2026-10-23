package com.freshnow.app.ui.scan

import com.freshnow.app.data.ScanResult
import com.freshnow.app.data.filledFieldCount

/**
 * 取景框左下角那枚胶囊该说什么。
 * 三种取值互斥：还在读（[Recognizing]）、这一帧读到了几项（[Found]）、没什么可说（[Hidden]）。
 * 之所以是一句话而不是一根进度条：请求每帧发一次，这件事永远没有「做完」的那一刻。
 */
sealed interface ScanStatus {

    data object Hidden : ScanStatus

    data object Recognizing : ScanStatus

    data class Found(val count: Int) : ScanStatus

    companion object {
        /**
         * 由**一帧**的观察结果推出该报几项，口径见 [filledFieldCount]。
         *
         * 传进来的必须是单帧的观察（`AiAnalysis.result`），不是累加后的记录：提示词只要求模型
         * 填「这张照片里确实看得清的字段」，所以单帧项数是会变的；而累加值涨到四项就不再动了。
         */
        fun of(observation: ScanResult): ScanStatus =
            observation.filledFieldCount.let { if (it > 0) Found(it) else Hidden }
    }
}

/**
 * 状态胶囊的最短停留：同一句话在画面里至少留 [dwellMs]，中途报上来的短状态直接丢掉。
 * 请求每秒回来好几次，跟着信号换字的话这句话一秒里能跳三次（真机上看过不加它的样子）。
 * 丢掉而不是排队补演：这些状态说的是「此刻如何」，等停留期满再补一句上一刻的话没有意义。
 * 时间由调用方给，不在内部读时钟：这样这一小段判定是纯的，能在单测里逐拍断言（见 ScanStatusDwellTest）。
 */
internal class ScanStatusDwell(private val dwellMs: Long) {

    private var shown: ScanStatus = ScanStatus.Hidden

    /** 现显示的那句是什么时候上的画面；null 表示没有哪一句占着停留窗口 */
    private var shownAtMs: Long? = null

    /**
     * 报一句想显示的。返回该显示的那句；与正在显示的相同、或距上一句不足 [dwellMs] 时返回 null，
     * 表示这次改动被丢掉了，界面不必动。
     */
    fun offer(nowMs: Long, next: ScanStatus): ScanStatus? {
        if (next == shown) return null
        val since = shownAtMs
        if (since != null && nowMs - since < dwellMs) return null
        shown = next
        shownAtMs = nowMs
        return shown
    }

    /**
     * 直接设过去，不受停留约束，并清掉停留窗口。
     *
     * 给「用户按了清空」这类当场发生的变化用：那一刻四个字段已经变成「未知」，胶囊若还被上一句的
     * 停留拦着，就会挂着「已读到 4 项」跟空白的字段对不上。
     */
    fun reset(status: ScanStatus) {
        shown = status
        shownAtMs = null
    }
}