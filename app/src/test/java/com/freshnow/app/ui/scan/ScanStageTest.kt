package com.freshnow.app.ui.scan

import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.data.ScanResult
import com.freshnow.app.ui.component.RecordField
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 扫描页两态之间的搬运：翻面带过去的字段、折回来之后草稿还在不在、以及能不能存。
 *
 * 只摆纯逻辑，位图一律不给：把那一帧解成位图的事留在 ViewModel 里，那部分要设备。
 */
class ScanStageTest {

    /** 翻面之后表单里看到的就是刚读到的那些值 */
    @Test
    fun flippingIntoEditingCarriesTheScannedFields() {
        val state = ScanUiState(record = ScanResult(productName = "纯牛奶", shelfLife = "12个月"))

        val editing = state.startEditing()

        assertEquals(ScanStage.EDITING, editing.stage)
        assertEquals("纯牛奶", editing.formFields.productName)
        assertEquals("12个月", editing.formFields.shelfLife)
    }

    /**
     * 折回扫描态再翻面，手改的值必须在。
     *
     * 这是「一份状态」这条约束的用处所在（见 ScanStage）：表单与扫描累加若是两份状态，
     * 这里就会看到识别结果把手写的那一项盖回去。
     */
    @Test
    fun flippingBackAndForthKeepsWhatTheUserTyped() {
        val edited = ScanUiState(record = ScanResult(productName = "纯牛奶"))
            .onFieldChange(RecordField.PRODUCT_NAME, "牛奶")

        val back = edited.backToScanning()
        val again = back.startEditing()

        assertEquals(ScanStage.SCANNING, back.stage)
        assertEquals("手改的品名不该被识别结果盖回去", "牛奶", again.formFields.productName)
    }

    /** 改字段要跟着重算过期日期，否则表单里那句话会停在旧值上 */
    @Test
    fun editingFieldsRecomputesTheExpiry() {
        val state = ScanUiState(record = ScanResult())
            .onFieldChange(RecordField.PRODUCTION_DATE, "2026-01-01")
            .onFieldChange(RecordField.SHELF_LIFE, "12个月")

        assertEquals("2027-01-01", (state.expiry as ExpiryOutcome.Resolved).date)
    }

    /** 认不出的写法不给保存：与编辑页同一条规矩 */
    @Test
    fun anUnrecognizableDateBlocksSaving() {
        val state = ScanUiState(record = ScanResult(productName = "牛奶"))
            .onFieldChange(RecordField.PRODUCTION_DATE, "13月")

        assertEquals(false, state.canSave)
    }

    /** 一个字段都没读到就不给存：与「清空 / 下一步」置灰用的是同一条判据 */
    @Test
    fun anEmptyRecordBlocksSaving() {
        assertEquals(false, ScanUiState().startEditing().canSave)
    }

    /** 落库前把首尾空白去掉：两条保存路径（扫描、编辑）用的是同一种写法 */
    @Test
    fun storedResultTrimsTheTextFields() {
        val state = ScanUiState(record = ScanResult(productName = " 牛奶 "))

        assertEquals("牛奶", state.storedResult().productName)
    }
}
