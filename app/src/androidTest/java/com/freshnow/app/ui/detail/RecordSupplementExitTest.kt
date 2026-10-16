package com.freshnow.app.ui.detail

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.theme.FreshNowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 补充信息页的两条出路各走各的。
 *
 * 【为什么盯它】「保存」与「返回」在这里都不是单纯的关页：保存表示这条记录的事办完了（回列表看它），
 * 返回表示还没填完要接着扫（落回扫描页）。两者混成一个的话，一个是办完了却被扔回相机前面、
 * 一个是没保存却已经看不到那条记录了。这里只验「两个动作分别叫了哪个回调」，
 * 落点由那两支导航自己负责（见 SettledNavigatorTest）。
 */
@RunWith(AndroidJUnit4::class)
class RecordSupplementExitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private var recordId: Long = 0L

    @After
    fun cleanUp() = runBlocking {
        if (recordId != 0L) {
            FreshNowDatabase.getInstance(application).scanRecordDao().hardDelete(listOf(recordId))
        }
    }

    @Test
    fun savingGoesWhereTheRecordIs() {
        seed()
        var saved = 0
        var backed = 0
        setContent(onSaved = { saved++ }, onBack = { backed++ })

        composeRule.onNodeWithText("保存").performClick()
        awaitIdle()

        assertEquals("保存之后要去记录待着的地方", 1, saved)
        assertEquals("不该同时触发返回", 0, backed)
    }

    @Test
    fun backingOutReturnsToWhereTheScanWas() {
        seed()
        var saved = 0
        var backed = 0
        setContent(onSaved = { saved++ }, onBack = { backed++ })

        composeRule.onNodeWithContentDescription("返回").performClick()
        awaitIdle()

        assertEquals("返回是接着回去扫，不是保存", 1, backed)
        assertEquals("返回不该顺带保存", 0, saved)
    }

    private fun awaitIdle() {
        composeRule.waitForIdle()
        // 保存是异步写入，回调要等落盘；返回是同步的，这一等对它没有影响
        runBlocking { withTimeout(5_000) { delay(300) } }
        composeRule.waitForIdle()
    }

    private fun seed() = runBlocking {
        val dao = FreshNowDatabase.getInstance(application).scanRecordDao()
        recordId = dao.insert(
            ScanRecord(
                productName = "补充信息出路用例",
                productionDate = "2026-01-01",
                expiryDate = "2026-12-31",
                shelfLife = "12个月",
                imageName = "",
                savedAt = 1L,
                syncId = UUID.randomUUID().toString(),
                updatedAt = 1L,
                updatedBy = "dev-a"
            )
        )
    }

    private fun setContent(onSaved: () -> Unit, onBack: () -> Unit) {
        composeRule.setContent {
            FreshNowTheme {
                RecordSupplementScreen(
                    recordId = recordId,
                    onBack = onBack,
                    onSaved = onSaved,
                    viewModel = RecordSupplementViewModel(application)
                )
            }
        }
    }
}
