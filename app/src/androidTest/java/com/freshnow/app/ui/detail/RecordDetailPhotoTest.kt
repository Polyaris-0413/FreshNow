package com.freshnow.app.ui.detail

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ScanImageStore
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.theme.FreshNowTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 详情页的照片能点开看图。
 *
 * 走真的库与真的图片文件（同 RecordEditReloadTest 的口径）：这一条要验的正是「界面把两件事接上」，
 * 用假数据就绕过了要验的那一段。
 */
@RunWith(AndroidJUnit4::class)
class RecordDetailPhotoTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private var recordId: Long = 0
    private var imageName: String? = null

    @After
    fun cleanUp() {
        runBlocking {
            if (recordId != 0L) {
                FreshNowDatabase.getInstance(application).scanRecordDao().hardDelete(listOf(recordId))
            }
            imageName?.let { ScanImageStore(application).delete(it) }
        }
    }

    @Test
    fun tappingThePhotoOpensTheViewerAndBackReturns() {
        seedRecordWithPhoto()
        setContent()

        // 照片解码是异步的：等它出来再点（占位图不可点，点不出看图页）
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(PHOTO).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(PHOTO).performClick()

        // 看图页的手势说明只在这一页上有
        composeRule.onNodeWithText(VIEW_HINT).assertIsDisplayed()
        saveScreenshot("detail-photo-viewer.png")

        composeRule.onNodeWithContentDescription(BACK).performClick()

        composeRule.onNodeWithText(VIEW_HINT).assertDoesNotExist()
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
    }

    private fun setContent() {
        composeRule.setContent {
            FreshNowTheme {
                RecordDetailScreen(
                    recordId = recordId,
                    onBack = {},
                    onNavigateToEdit = {},
                    viewModel = RecordDetailViewModel(application)
                )
            }
        }
    }

    /** 造一条带照片的记录：图片走真的 ScanImageStore（名字就是内容散列），记录进真的库 */
    private fun seedRecordWithPhoto() {
        val photo = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val bytes = ByteArrayOutputStream().use { out ->
            photo.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        imageName = runBlocking { ScanImageStore(application).write(bytes) }
        recordId = runBlocking {
            FreshNowDatabase.getInstance(application).scanRecordDao().insert(
                ScanRecord(
                    productName = "全脂纯牛奶",
                    productionDate = "2026-10-01",
                    expiryDate = "2026-12-01",
                    shelfLife = "2个月",
                    imageName = imageName.orEmpty(),
                    savedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 截图存进应用自己的目录，供人工核对版式；测试不强依赖它 */
    private fun saveScreenshot(name: String) {
        runCatching {
            val file = File(application.filesDir, name)
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
        }
    }

    private companion object {
        const val PHOTO = "扫描照片"
        const val VIEW_HINT = "双指放大，拖动查看"
        const val BACK = "返回"
    }
}
