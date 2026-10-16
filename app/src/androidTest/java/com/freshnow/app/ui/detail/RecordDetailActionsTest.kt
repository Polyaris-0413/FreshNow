package com.freshnow.app.ui.detail

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.theme.FreshNowTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 详情页顶栏那个入口。
 *
 * 【为什么盯它】位置从「补充信息」页也进了编辑页之后，详情页就只留「编辑」一个：位置在编辑页里
 * 也能改，再摆一个入口只是让人在两个按钮之间选一个。而反过来，入口一旦全没了，位置就再也改不了，
 * 界面上却看不出少了什么。
 */
@RunWith(AndroidJUnit4::class)
class RecordDetailActionsTest {

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
    fun theOnlyEntryIsEdit() {
        seed()
        var edit = 0
        setContent(onNavigateToEdit = { edit++ })

        composeRule.onNodeWithText("编辑").assertIsDisplayed().performClick()

        assertEquals("编辑照旧", 1, edit)
        composeRule.onNodeWithText("补充信息").assertDoesNotExist()
    }

    private fun seed() = runBlocking {
        val dao = FreshNowDatabase.getInstance(application).scanRecordDao()
        recordId = dao.insert(
            ScanRecord(
                productName = "详情页入口用例",
                productionDate = "2026-01-01",
                expiryDate = "2026-12-31",
                shelfLife = "12个月",
                location = "冰箱冷藏室",
                imageName = "",
                savedAt = 1L,
                syncId = UUID.randomUUID().toString(),
                updatedAt = 1L,
                updatedBy = "dev-a"
            )
        )
    }

    private fun setContent(onNavigateToEdit: () -> Unit) {
        composeRule.setContent {
            FreshNowTheme {
                RecordDetailScreen(
                    recordId = recordId,
                    onBack = {},
                    onNavigateToEdit = onNavigateToEdit,
                    viewModel = RecordDetailViewModel(application)
                )
            }
        }
    }
}
