package com.freshnow.app.ui.detail

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ExpiryOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 写法校验：三个字段一视同仁，空白不算错，认不出就不给保存。
 *
 * 只调 onXxxChange，不调 load / save：前者不碰库，后者会读写设备上应用自己的扫描记录。
 * （ViewModel 构造时会建 Room 实例，但 Room 是懒打开的——不发起查询就不会碰到数据库文件。）
 */
@RunWith(AndroidJUnit4::class)
class RecordEditValidationTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private fun viewModel() = RecordEditViewModel(application)

    /** 空白不算错：模型没读到、用户还没填都正常，不该因此不给保存 */
    @Test
    fun blankFieldsAreNotErrors() {
        val state = viewModel().uiState.value

        assertFalse(state.productionDateInvalid)
        assertFalse(state.shelfLifeInvalid)
        assertFalse(state.expiryDateInvalid)
        assertTrue("四项都空着也应当可以保存", state.canSave)
    }

    /** 三个字段同一套规则：日期看能不能解析，保质期看单位与数量认不认得出 */
    @Test
    fun unrecognizableWritingIsFlaggedEverywhere() {
        val vm = viewModel()
        vm.onProductionDateChange("13月")
        vm.onShelfLifeChange("很久")
        // 日写了三位：数字连成一串时不敢猜，见 ScanValueFormat 的说明
        vm.onExpiryDateChange("2026-10-123")

        val state = vm.uiState.value
        assertTrue(state.productionDateInvalid)
        assertTrue(state.shelfLifeInvalid)
        assertTrue(state.expiryDateInvalid)
        assertFalse(state.canSave)
    }

    /** 认得的写法都不该算错，否则保存会莫名其妙地灰着 */
    @Test
    fun recognizableVariantsAreAccepted() {
        val vm = viewModel()
        vm.onProductionDateChange("2026年10月6日")
        vm.onShelfLifeChange("2周")
        vm.onExpiryDateChange("2026/10/6")

        val state = vm.uiState.value
        assertFalse(state.productionDateInvalid)
        assertFalse(state.shelfLifeInvalid)
        assertFalse(state.expiryDateInvalid)
        assertTrue(state.canSave)
    }

    /** 改回认得出的写法要能立刻恢复可保存，不然错误状态会粘住 */
    @Test
    fun fixingTheValueRestoresSave() {
        val vm = viewModel()
        vm.onExpiryDateChange("2026-10-123")
        assertFalse(vm.uiState.value.canSave)

        vm.onExpiryDateChange("2026-10-12")

        assertTrue(vm.uiState.value.canSave)
    }

    /** 推算跟着草稿走：过期日期留空时，改生产日期或保质期都要当场重算 */
    @Test
    fun derivedExpiryFollowsDraft() {
        val vm = viewModel()
        vm.onExpiryDateChange("")

        vm.onProductionDateChange("2026-10-06")
        vm.onShelfLifeChange("2周")

        assertEquals(ExpiryOutcome.Resolved("2026-10-20"), vm.uiState.value.derivedExpiry)
    }
}
