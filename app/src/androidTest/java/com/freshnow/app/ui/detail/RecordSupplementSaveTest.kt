package com.freshnow.app.ui.detail

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 补充信息页的写入，以及它与编辑记录页之间「不把对方的值清掉」这条界线。
 *
 * 【为什么盯这条界线】位置两页都能改（扫完在补充信息页补，以后要改从详情页的「编辑」进），
 * 而两页保存时写的都是整行。一旦哪天谁 `record.copy` 时漏带了位置，或者写回一条不是刚读到的旧值，
 * 后果是静默的：位置在一次「保存」之后没了，界面上什么都不会报。所以几个方向各验一次。
 *
 * 走真实的库，但只碰本用例自己插进去的那条（按名字前缀回收）。
 */
@RunWith(AndroidJUnit4::class)
class RecordSupplementSaveTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @After
    fun cleanUp() = runBlocking {
        val dao = FreshNowDatabase.getInstance(application).scanRecordDao()
        // 前缀而不是全等：另一个用例会把名称改掉，改完那条也得能收走
        val ids = dao.observeAll().first()
            .filter { it.productName.startsWith(PRODUCT_NAME) }
            .map { it.id }
        if (ids.isNotEmpty()) dao.hardDelete(ids)
    }

    @Test
    fun savingTheLocation_keepsTheOtherFields() = runBlocking {
        val seeded = seed()
        val viewModel = RecordSupplementViewModel(application)
        viewModel.load(seeded.id)
        awaitLoaded(viewModel)
        viewModel.onLocationChange(LOCATION)
        awaitSupplementSaved(viewModel)

        val saved = requireNotNull(find(seeded.id))
        assertEquals("位置要写进去", LOCATION, saved.location)
        assertEquals("还是那一条记录，不是新插一条", seeded.id, saved.id)
        assertEquals(PRODUCT_NAME, saved.productName)
        assertEquals("2026-01-01", saved.productionDate)
        assertEquals("2026-12-31", saved.expiryDate)
        assertEquals("12个月", saved.shelfLife)
        assertEquals("照片那一列与本页无关，不能被改成别的", seeded.imageName, saved.imageName)
        assertTrue("改过就要留下修改时刻，否则跨设备同步认不出谁新", saved.updatedAt > 0)
    }

    /** 反向：编辑记录页改完名称，位置不能跟着没了（编辑页的草稿里带着从库里读到的位置） */
    @Test
    fun editingTheRecord_keepsTheLocation() = runBlocking {
        val seeded = seed(location = LOCATION)
        val viewModel = RecordEditViewModel(application)
        viewModel.load(seeded.id)
        awaitEditLoaded(viewModel)
        viewModel.onProductNameChange("$PRODUCT_NAME-改")
        awaitEditSaved(viewModel)

        val saved = requireNotNull(find(seeded.id))
        assertEquals("改的是名称", "$PRODUCT_NAME-改", saved.productName)
        assertEquals("位置归另一张表单，编辑页不该碰它", LOCATION, saved.location)
    }

    /** 编辑页也能改位置：详情页只留了一个入口，位置就靠它改 */
    @Test
    fun editingTheLocationViaTheEditForm_writesIt() = runBlocking {
        val seeded = seed()
        val viewModel = RecordEditViewModel(application)
        viewModel.load(seeded.id)
        awaitEditLoaded(viewModel)
        viewModel.onLocationChange(LOCATION)
        awaitEditSaved(viewModel)

        val saved = requireNotNull(find(seeded.id))
        assertEquals("位置要写进去", LOCATION, saved.location)
        assertEquals("其余字段照旧", PRODUCT_NAME, saved.productName)
    }

    /** 位置怎么写由用户说了算，只去掉首尾空白（打头那个空格多半是输入法带的） */
    @Test
    fun locationIsTrimmed() = runBlocking {
        val seeded = seed()
        val viewModel = RecordSupplementViewModel(application)
        viewModel.load(seeded.id)
        awaitLoaded(viewModel)
        viewModel.onLocationChange("  $LOCATION  ")
        awaitSupplementSaved(viewModel)

        assertEquals(LOCATION, requireNotNull(find(seeded.id)).location)
    }

    /** 把填过的位置擦掉也是一次编辑，不该被拦下：那一栏空着是「没填」而不是「填错」 */
    @Test
    fun clearingTheLocation_canBeSaved() = runBlocking {
        val viewModel = RecordSupplementViewModel(application)
        viewModel.load(seed(location = LOCATION).id)
        awaitLoaded(viewModel)

        viewModel.onLocationChange("")

        assertTrue("清空也放行", viewModel.uiState.value.canSave)
    }

    private suspend fun seed(location: String = ""): ScanRecord {
        val dao = FreshNowDatabase.getInstance(application).scanRecordDao()
        val id = dao.insert(
            ScanRecord(
                productName = PRODUCT_NAME,
                productionDate = "2026-01-01",
                expiryDate = "2026-12-31",
                shelfLife = "12个月",
                location = location,
                imageName = "seeded.jpg",
                savedAt = 1L,
                syncId = UUID.randomUUID().toString(),
                updatedAt = 1L,
                updatedBy = "dev-a"
            )
        )
        return requireNotNull(dao.findById(id))
    }

    private suspend fun find(id: Long): ScanRecord? =
        FreshNowDatabase.getInstance(application).scanRecordDao().findById(id)

    private fun awaitLoaded(viewModel: RecordSupplementViewModel) = runBlocking {
        withTimeout(5_000) {
            while (!viewModel.uiState.value.loaded) delay(10)
        }
    }

    private fun awaitEditLoaded(viewModel: RecordEditViewModel) = runBlocking {
        withTimeout(5_000) {
            while (!viewModel.uiState.value.loaded) delay(10)
        }
    }

    /** 保存是异步的，回调只报一次；不轮询状态：写入成功的标志就是它没报错 */
    private fun awaitSupplementSaved(viewModel: RecordSupplementViewModel) = runBlocking {
        var saved = false
        viewModel.save { saved = true }
        withTimeout(5_000) { while (!saved) delay(10) }
    }

    private fun awaitEditSaved(viewModel: RecordEditViewModel) = runBlocking {
        var saved = false
        viewModel.save { _ -> saved = true }
        withTimeout(5_000) { while (!saved) delay(10) }
    }

    private companion object {
        const val PRODUCT_NAME = "补充信息用例-纯牛奶"
        const val LOCATION = "冰箱冷藏室"
    }
}
