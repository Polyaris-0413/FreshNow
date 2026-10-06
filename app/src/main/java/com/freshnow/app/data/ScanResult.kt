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

/**
 * 合并一帧的观察结果：空字符串表示「本帧没有看到这一项」，因此保留已有记录；
 * 只有本帧确实读到了内容才覆盖。实时扫描时标签的各个字段通常分散在不同帧里，
 * 直接覆盖会让后一帧清掉前一帧刚读到的字段。
 */
fun ScanResult.mergeObservation(observation: ScanResult) = ScanResult(
    productionDate = observation.productionDate.ifBlank { productionDate },
    expiryDate = observation.expiryDate.ifBlank { expiryDate },
    shelfLife = observation.shelfLife.ifBlank { shelfLife }
)
