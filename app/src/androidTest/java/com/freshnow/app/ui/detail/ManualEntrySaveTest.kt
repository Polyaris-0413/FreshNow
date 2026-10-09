package com.freshnow.app.ui.detail

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * 手动录入：开一份空草稿，保存时是首次写入，而不是覆盖某条已有记录。
 *
 * 走真实的库，但只碰本用例自己插进去的那条（按名字回收），不依赖也不改动设备上的其它记录。
 */
@RunWith(AndroidJUnit4::class)
class ManualEntrySaveTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @After
    fun cleanUp() = runBlocking {
        val dao = FreshNowDatabase.getInstance(application).scanRecordDao()
        val ids = dao.observeAll().first().filter { it.productName == PRODUCT_NAME }.map { it.id }
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }

    /** 空草稿不给存：与扫描页「一个字段都没读到就不存」同一条规矩，免得手滑存下一堆空记录 */
    @Test
    fun emptyDraft_cannotBeSaved() {
        val viewModel = RecordEditViewModel(application)
        viewModel.startNew()
        awaitLoaded(viewModel)

        assertTrue("手动录入开出来的应当是新记录", viewModel.uiState.value.isNew)
        assertFalse("一个字段都没填时不给存", viewModel.uiState.value.canSave)

        viewModel.onProductNameChange(PRODUCT_NAME)

        assertTrue("填了一项就该放行", viewModel.uiState.value.canSave)
    }

    /** 保存写进库的是新的一行，值走与模型入库同一套规整 */
    @Test
    fun savingDraft_insertsNewRecord() {
        val viewModel = RecordEditViewModel(application)
        viewModel.startNew()
        awaitLoaded(viewModel)
        viewModel.onProductNameChange(PRODUCT_NAME)
        // 写法故意不用标准写法，验的是落库前会规整
        viewModel.onExpiryDateChange("2026/12/1")

        var imageSaved: Boolean? = null
        viewModel.save { imageSaved = it }
        awaitSaved { imageSaved }

        val saved = runBlocking { FreshNowDatabase.getInstance(application).scanRecordDao().observeAll().first() }
            .single { it.productName == PRODUCT_NAME }

        assertEquals("2026-12-01", saved.expiryDate)
        assertTrue("保存时间应当是保存那一刻，不是 0", saved.savedAt > 0)
        assertEquals("没有照片时这一项不该报失败", true, imageSaved)
    }

    /**
     * 重复开草稿不能冲掉已经填的内容：本页重建（返回、进程内重建）会再调一次 startNew，
     * 而它是一次整体替换——草稿会被空值盖回去。
     */
    @Test
    fun startingNewTwice_keepsDraft() {
        val viewModel = RecordEditViewModel(application)
        viewModel.startNew()
        awaitLoaded(viewModel)
        viewModel.onProductNameChange(PRODUCT_NAME)

        viewModel.startNew()
        // 重开是同步的，没有可等的状态；给足时间让「草稿被空值盖回去」这件事有机会发生
        runBlocking { delay(100) }

        assertEquals(PRODUCT_NAME, viewModel.uiState.value.productName)
    }

    private fun awaitLoaded(viewModel: RecordEditViewModel) = runBlocking {
        withTimeout(5_000) {
            while (!viewModel.uiState.value.loaded) delay(10)
        }
    }

    private fun awaitSaved(imageSaved: () -> Boolean?) = runBlocking {
        withTimeout(5_000) {
            while (imageSaved() == null) delay(10)
        }
    }

    private companion object {
        const val PRODUCT_NAME = "手动录入用例-纯牛奶"
    }
}
