package com.freshnow.app.ui.detail

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 重复读入同一条记录不能动草稿。
 *
 * 屏幕旋转会重建本页，`LaunchedEffect` 跟着重跑一次 load，而 load 是一次整体替换：
 * 草稿会被库里的旧值盖回去，裁剪中还没保存的图也会被重置成 null、退回编辑步骤。
 */
@RunWith(AndroidJUnit4::class)
class RecordEditReloadTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private var recordId: Long = 0

    @After
    fun cleanUp() {
        if (recordId != 0L) {
            runBlocking {
                FreshNowDatabase.getInstance(application).scanRecordDao().hardDelete(listOf(recordId))
            }
        }
    }

    @Test
    fun reloadingSameRecord_keepsDraft() {
        recordId = runBlocking {
            FreshNowDatabase.getInstance(application).scanRecordDao().insert(
                ScanRecord(
                    productName = "全脂纯牛奶",
                    productionDate = "2026-10-01",
                    expiryDate = "2026-12-01",
                    shelfLife = "2个月",
                    imageName = "",
                    savedAt = System.currentTimeMillis()
                )
            )
        }

        val viewModel = RecordEditViewModel(application)
        viewModel.load(recordId)
        awaitLoaded(viewModel)
        viewModel.onProductNameChange("改过的名字")

        // 旋转之后重进本页：LaunchedEffect 会再调一次 load
        viewModel.load(recordId)
        // 重读是异步的，而这一路上没有可等的状态（没修好时它只是默默把 uiState 整体换掉）。
        // 所以这里给足时间，让「草稿被库里的旧值盖回去」这件事有机会发生——没发生才算通过。
        // 等待而不能拿 loaded 当信号：它一开始就是 true，断言会在重读完成前就跑完
        runBlocking { delay(1_000) }

        assertEquals("改过的名字", viewModel.uiState.value.productName)
    }

    private fun awaitLoaded(viewModel: RecordEditViewModel) = runBlocking {
        withTimeout(5_000) {
            while (!viewModel.uiState.value.loaded) delay(10)
        }
    }
}
