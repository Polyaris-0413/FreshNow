package com.freshnow.app.ui.scan

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.RecordField
import com.freshnow.app.data.local.FreshNowDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 补充信息态按下保存之后，页面就此离开——不能先演一遍翻回扫描态的转场。
 *
 * 那一下转场是整屏的（[ScanStep.Scanning] 排在 [ScanStep.Editing] 之前），相机重新展开、两屏
 * 各叠半程，随后才被 pop 回主页；用户看到的就是「保存后先回扫描页，再回主页」。
 *
 * 走真实的库，但只碰本用例自己写进去的那条（按名字回收）。
 */
@RunWith(AndroidJUnit4::class)
class ScanSaveLeaveTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @After
    fun cleanUp() = runBlocking {
        val dao = FreshNowDatabase.getInstance(application).scanRecordDao()
        val ids = dao.observeAll().first().filter { it.productName == PRODUCT_NAME }.map { it.id }
        if (ids.isNotEmpty()) dao.hardDelete(ids)
    }

    @Test
    fun savingFromTheSupplementStage_keepsTheStageUntilThePageLeaves() {
        val viewModel = draftInSupplementStage()

        val savedId = saveAndAwaitId(viewModel)

        assertEquals("保存之后不能再演一遍翻回扫描态的转场", ScanStage.EDITING, viewModel.uiState.value.stage)
        assertTrue("这一次写入要真的落库", savedId > 0)
        assertEquals("同一次保存只该写进一条", 1, countSavedRows())
    }

    /** 草稿留在屏幕上直到离场，所以防连点不能再靠「清空后按钮自动置灰」 */
    @Test
    fun whileSaving_theSaveActionIsClosed() {
        val viewModel = draftInSupplementStage()
        var savedId: Long? = null

        viewModel.save { savedId = it }

        assertFalse("提交那一刻保存按钮就该关上", viewModel.uiState.value.canSave)
        awaitSaved { savedId }
    }

    private fun draftInSupplementStage(): ScanViewModel = ScanViewModel(application).apply {
        onFieldChange(RecordField.PRODUCT_NAME, PRODUCT_NAME)
        nextStep()
    }

    /** 返回新记录的 id；回调发生在 viewModelScope 上，这里等它落地 */
    private fun saveAndAwaitId(viewModel: ScanViewModel): Long {
        var savedId: Long? = null
        viewModel.save { savedId = it }
        awaitSaved { savedId }
        return requireNotNull(savedId)
    }

    private fun awaitSaved(savedId: () -> Long?) = runBlocking {
        withTimeout(5_000) {
            while (savedId() == null) delay(10)
        }
    }

    private fun countSavedRows(): Int = runBlocking {
        FreshNowDatabase.getInstance(application).scanRecordDao()
            .observeAll().first().count { it.productName == PRODUCT_NAME }
    }

    private companion object {
        const val PRODUCT_NAME = "扫描保存用例-纯牛奶"
    }
}
