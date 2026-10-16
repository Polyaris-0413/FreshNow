package com.freshnow.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.RecordField
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 记录表单的字段值，不含照片与推算结果。
 *
 * 三个 `xxxInvalid` 是「认不出这个写法」：空白不算错（模型没读到、还没填都正常），但只要写了
 * 认不出的东西就算，用于挡下保存与显示错误文案。
 */
data class RecordFormFields(
    val productName: String = "",
    val location: String = "",
    val productionDate: String = "",
    val expiryDate: String = "",
    val shelfLife: String = "",
    val productionDateInvalid: Boolean = false,
    val shelfLifeInvalid: Boolean = false,
    val expiryDateInvalid: Boolean = false
)

/**
 * 草稿里的一张照片。
 *
 * 两种身份靠前两个字段分：有 [name] 的是库里已经那一张（保存时按名字留下），有 [bytes] 的是刚加
 * 进来、还没落盘的。两者不会同时有——照片名由内容算出来，只有写盘那一刻才知道。
 *
 * [bitmap] 是这一屏要用它显示的那张图：库里那张解码得来，新加的从字节解；解不出来时为 null，
 * 格子用占位图顶上。
 *
 * [id] 是草稿内的身份，给界面在增删时认住同一格用：[name] 与 [bytes] 都要到保存那一刻才定下来，
 * 只能拿它当 key。
 */
data class EditPhoto(
    val id: Long,
    val name: String = "",
    val bytes: ByteArray? = null,
    val bitmap: Bitmap? = null
)

/**
 * 记录表单：照片九宫格加五个字段（名称、位置、生产日期、保质期、过期日期）。
 *
 * 编辑记录页与扫描页的编辑态共用这一个（设计文档里的反目标 A3）：两处各写一份的话，
 * 「过期日期留空即跟随推算」「写错了什么时候才报」这类规则会从一个分支漏掉。
 *
 * 它吃纯数据、不吃哪一页的 UiState：两端各自持有自己的草稿（编辑页是一份读取来的记录，
 * 扫描页是扫描累加的那份），谁也不该为了复用去伪造对方的状态。
 */
@Composable
internal fun RecordEditForm(
    fields: RecordFormFields,
    photos: List<PhotoGridItem>,
    derivedExpiry: ExpiryOutcome,
    onFieldChange: (RecordField, String) -> Unit,
    onOpenPhoto: (Int) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onAddPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FreshNowSpacing.sm)
    ) {
        // 照片那一栏：九宫格，末尾一格是「+」，每格右上角是「✕」。
        //
        // 从「一枚缩略图 + 更换照片」换成九宫格：一条记录现在能配多张（上限九张），而这一栏要回答
        // 的是「这条记录有哪几张、还能加几张」；一排缩略图放到第五张就得横着滑，看不见自己存了多少。
        //
        // 不再包一层卡片轮廓：格子自己就是可编辑项的边界（虚线那一格说的正是「还能加」），
        // 再套一层描边框等于把「这里可以动」这个意思说两遍。
        PhotoGrid(
            items = photos,
            onOpen = onOpenPhoto,
            onRemove = onRemovePhoto,
            onAdd = onAddPhoto
        )
        EditField(
            value = fields.productName,
            onValueChange = { onFieldChange(RecordField.PRODUCT_NAME, it) },
            label = stringResource(R.string.scan_product_name)
        )
        // 位置排在名称之后：名称说的是「这是什么」，位置说的是「它在哪」，两句是一件事；
        // 而下面三项是一条推算链（生产日期 + 保质期 → 过期日期），位置插在它们中间会把链子打断。
        // 详情页上也是这个次序，两处对齐
        EditField(
            value = fields.location,
            onValueChange = { onFieldChange(RecordField.LOCATION, it) },
            label = stringResource(R.string.record_location)
        )
        EditField(
            value = fields.productionDate,
            onValueChange = { onFieldChange(RecordField.PRODUCTION_DATE, it) },
            label = stringResource(R.string.scan_production_date),
            error = if (fields.productionDateInvalid) {
                stringResource(R.string.record_edit_error_unrecognized)
            } else {
                null
            }
        )
        EditField(
            value = fields.shelfLife,
            onValueChange = { onFieldChange(RecordField.SHELF_LIFE, it) },
            label = stringResource(R.string.scan_shelf_life),
            error = if (fields.shelfLifeInvalid) {
                stringResource(R.string.record_edit_error_unrecognized)
            } else {
                null
            }
        )
        EditField(
            value = fields.expiryDate,
            onValueChange = { onFieldChange(RecordField.EXPIRY_DATE, it) },
            label = stringResource(R.string.scan_expiry_date),
            error = if (fields.expiryDateInvalid) {
                stringResource(R.string.record_edit_error_unrecognized)
            } else {
                null
            },
            hint = if (fields.expiryDate.isBlank()) {
                derivedExpiryHint(derivedExpiry)
            } else {
                null
            },
            // 本项之后没有可跳的下一项，输入法的动作键收在「完成」
            imeAction = ImeAction.Done
        )
    }
}

/** 过期日期留空时的说明：算得出就给日期，算不出就如实说推不出（哪个字段错了由那个字段自己报） */
@Composable
private fun derivedExpiryHint(derived: ExpiryOutcome): String = when (derived) {
    is ExpiryOutcome.Resolved -> stringResource(R.string.record_edit_expiry_hint, derived.date)
    ExpiryOutcome.InsufficientInput, ExpiryOutcome.UnparseableShelfLife ->
        stringResource(R.string.record_edit_expiry_hint_pending)
}
