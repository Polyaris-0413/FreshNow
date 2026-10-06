package com.freshnow.app.data

/**
 * 模型从标签上读到的原文。[expiryDate] 只表示标签**印刷**的过期日期，
 * 由生产日期与保质期推算出的结果不写在这里，见 [ExpiryCalculator]
 */
data class ScanResult(
    val productionDate: String = "",
    val expiryDate: String = "",
    val shelfLife: String = ""
)
