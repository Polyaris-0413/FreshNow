package com.freshnow.app.ui.scan

import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.ScanResult
import com.freshnow.app.data.ScanValueFormat
import com.freshnow.app.data.hasAnyValue
import com.freshnow.app.data.isUnrecognizableDate
import com.freshnow.app.data.isUnrecognizableShelfLife
import com.freshnow.app.data.RecordField
import com.freshnow.app.ui.component.RecordFormFields

/**
 * 扫描页的两态（见设计文档 D2/D3）。
 *
 * 扫描态只有相机与只读结果，编辑态才编照片与那几个字；两者是**同一个页面**的两个阶段，
 * 不是两个目的地——翻面之后相机收起来，折回去它还在。
 */
enum class ScanStage { SCANNING, EDITING }

/** 翻面。已经在编辑态就什么都不做：重复点不该动草稿 */
internal fun ScanUiState.startEditing(): ScanUiState =
    if (stage == ScanStage.EDITING) this else copy(stage = ScanStage.EDITING)

/** 折回扫描态。相机只是收起来，草稿里的字与照片都留着 */
internal fun ScanUiState.backToScanning(): ScanUiState =
    if (stage == ScanStage.SCANNING) this else copy(stage = ScanStage.SCANNING)

/**
 * 编辑态里改一项。
 *
 * 改的就是扫描累加的那份 [ScanUiState.record] 本身，不另开一份编辑草稿：两份状态并存的话，
 * 折回扫描态再翻面就会出现「表单里是你写的值、下面只读的却是模型的值」这种分叉。
 */
internal fun ScanUiState.onFieldChange(field: RecordField, value: String): ScanUiState {
    // 动过手就记住：这一项从此不再被识别覆盖（见设计文档 D6）
    val locked = lockedFields + field
    return when (field) {
        RecordField.PRODUCT_NAME -> withRecord(record.copy(productName = value))
        RecordField.LOCATION -> copy(location = value)
        RecordField.PRODUCTION_DATE -> withRecord(record.copy(productionDate = value))
        RecordField.EXPIRY_DATE -> withRecord(record.copy(expiryDate = value))
        RecordField.SHELF_LIFE -> withRecord(record.copy(shelfLife = value))
    }.copy(lockedFields = locked)
}

/** 记录与推算结果必须一起更新，避免两处状态不同步 */
internal fun ScanUiState.withRecord(record: ScanResult) = copy(
    record = record,
    expiry = ExpiryCalculator.resolve(
        printedExpiry = record.expiryDate,
        productionDate = record.productionDate,
        shelfLife = record.shelfLife
    )
)

/** 编辑态要显示的那几项。与编辑页共用同一个类型（见 [com.freshnow.app.ui.component.RecordEditForm]） */
internal val ScanUiState.formFields: RecordFormFields
    get() = RecordFormFields(
        productName = record.productName,
        location = location,
        productionDate = record.productionDate,
        expiryDate = record.expiryDate,
        shelfLife = record.shelfLife,
        productionDateInvalid = isUnrecognizableDate(record.productionDate),
        shelfLifeInvalid = isUnrecognizableShelfLife(record.shelfLife),
        expiryDateInvalid = isUnrecognizableDate(record.expiryDate)
    )

/**
 * 能不能存：读到了内容，且三个日期字段都认得出。
 *
 * 判据沿用编辑页那一条：写错的日期会让推算与剩余天数静默失效，而界面上只剩一句「推不出」。
 */
internal val ScanUiState.canSave: Boolean
    get() = with(formFields) {
        !productionDateInvalid && !shelfLifeInvalid && !expiryDateInvalid && record.hasAnyValue
    }

/**
 * 落库前的规整，用的是与编辑页同一份 [ScanValueFormat]：值不管是谁写的（模型读的、用户手输的），
 * 库里只有一种写法。
 */
internal fun ScanUiState.storedResult(): ScanResult = record.copy(
    productName = record.productName.trim(),
    productionDate = ScanValueFormat.date(record.productionDate.trim()),
    expiryDate = ScanValueFormat.date(record.expiryDate.trim()),
    shelfLife = ScanValueFormat.shelfLife(record.shelfLife.trim())
)
