package com.freshnow.app.data

/**
 * 模型从标签上读到的原文。[expiryDate] 只表示标签**印刷**的过期日期，
 * 由生产日期与保质期推算出的结果不写在这里，见 [ExpiryCalculator]
 */
data class ScanResult(
    /** 通用品名，按提示词要求不含品牌 */
    val productName: String = "",
    val productionDate: String = "",
    val expiryDate: String = "",
    val shelfLife: String = ""
)

/**
 * 合并一帧的观察结果：空字符串表示「本帧没有看到这一项」，因此保留已有记录；
 * 只有本帧确实读到了内容才覆盖。实时扫描时标签的各个字段通常分散在不同帧里，
 * 直接覆盖会让后一帧清掉前一帧刚读到的字段。
 */
fun ScanResult.mergeObservation(observation: ScanResult) = mergeObservation(this, observation, emptySet())

/**
 * 合并一帧的观察结果，但跳过 [locked] 里那几项。
 *
 * 锁表示「用户自己写过这一项」（见扫描页）：自己写的字被机器悄悄冲掉是最让人不信任的事，
 * 所以那一项此后只归用户。没锁的项仍是上面那条规则。
 */
fun mergeObservation(current: ScanResult, observation: ScanResult, locked: Set<RecordField>) = ScanResult(
    productName = current.orObservation(observation.productName, RecordField.PRODUCT_NAME, locked),
    productionDate = current.orObservation(observation.productionDate, RecordField.PRODUCTION_DATE, locked),
    expiryDate = current.orObservation(observation.expiryDate, RecordField.EXPIRY_DATE, locked),
    shelfLife = current.orObservation(observation.shelfLife, RecordField.SHELF_LIFE, locked)
)

/** 这一项归谁：锁着就用用户写的（哪怕他写的是空），没锁就用本帧读到的（空则保留旧值）*/
private fun ScanResult.orObservation(
    observed: String,
    field: RecordField,
    locked: Set<RecordField>
): String = when {
    field in locked -> field.valueOf(this)
    else -> observed.ifBlank { field.valueOf(this) }
}

/** 按字段取出这一项当前的值 */
private fun RecordField.valueOf(result: ScanResult): String = when (this) {
    RecordField.PRODUCT_NAME -> result.productName
    // 位置不在 [ScanResult] 里（模型读不到它，见 ScanRecord.location），锁对它也就没有过作用
    RecordField.LOCATION -> ""
    RecordField.PRODUCTION_DATE -> result.productionDate
    RecordField.EXPIRY_DATE -> result.expiryDate
    RecordField.SHELF_LIFE -> result.shelfLife
}

/**
 * 四个字段里有没有哪一个填了东西。
 *
 * 「扫描累加到了内容没有」与「手填的草稿值不值得存」是同一个判据，所以只留这一处实现：
 * 分开写两份的话，日后改判定（比如加一个字段）会只改到一边。
 */
internal fun anyFieldFilled(
    productName: String,
    productionDate: String,
    expiryDate: String,
    shelfLife: String
): Boolean = filledFieldCount(productName, productionDate, expiryDate, shelfLife) > 0

/**
 * 四个字段里填了几个。扫描页取景框里那枚状态胶囊报的「已读到 N 项」就是它。
 *
 * 与 [anyFieldFilled] 共用一套口径：两处各算各的，界面就会出现「已读到 3 项」而
 * 「清空 / 保存」还是灰的这种自相矛盾。
 *
 * 上限就是 4：标签上能读的就这四项，所以它只能当「读到了些什么」用，不能当进度用。
 * 单帧的观察与累加后的记录都用它算，区别只在传进来的是哪一个（见 ScanStatus.of）。
 */
internal fun filledFieldCount(
    productName: String,
    productionDate: String,
    expiryDate: String,
    shelfLife: String
): Int = listOf(productName, productionDate, expiryDate, shelfLife).count { it.isNotBlank() }

/** 是否已经累计到任何内容，用于决定要不要给出「清空」入口 */
val ScanResult.hasAnyValue: Boolean
    get() = anyFieldFilled(productName, productionDate, expiryDate, shelfLife)

/** 已填字段数，口径见 [filledFieldCount] */
internal val ScanResult.filledFieldCount: Int
    get() = filledFieldCount(productName, productionDate, expiryDate, shelfLife)
