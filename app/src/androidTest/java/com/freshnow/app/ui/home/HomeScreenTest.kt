package com.freshnow.app.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // 缩略图要真的解码一次，用临时目录里的合成图，不碰设备上的图片
    private val thumbnailSource = File(context.cacheDir, "home_list_test_thumb.jpg")

    @Before
    fun writeThumbnailSource() {
        thumbnailSource.outputStream().use { out ->
            Bitmap.createBitmap(600, 480, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(Color.rgb(0x33, 0x99, 0x66)) }
                .compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
    }

    @After
    fun cleanUp() {
        thumbnailSource.delete()
    }

    @Test
    fun showsProductNameAndCountdown() {
        val today = LocalDate.now()
        setContent(
            listOf(
                item(id = 1, productName = "纯牛奶", printedExpiry = today.plusDays(10).toString()),
                item(id = 2, productName = "", printedExpiry = today.toString()),
                item(id = 3, productName = "苏打饼干", printedExpiry = today.minusDays(3).toString()),
                item(id = 4, productName = "酸奶", shelfLife = "见包装")
            )
        )

        composeRule.onNodeWithText("纯牛奶").assertIsDisplayed()
        composeRule.onNodeWithText("还剩 10 天").assertIsDisplayed()
        composeRule.onNodeWithText("今天到期").assertIsDisplayed()
        composeRule.onNodeWithText("苏打饼干").assertIsDisplayed()
        composeRule.onNodeWithText("已过期 3 天").assertIsDisplayed()
        // 品名为空的记录不能是一片空白
        composeRule.onNodeWithText("未知").assertIsDisplayed()
        composeRule.onNodeWithText("过期时间未知").assertIsDisplayed()

        savePreview(PREVIEW_NAME)
    }

    /** 有图显示图片、没图显示占位图标，数量要对得上 */
    @Test
    fun showsThumbnailOnlyForRecordsWithImage() {
        setContent(
            listOf(
                item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"),
                item(id = 2, productName = "苏打饼干", printedExpiry = "2026-12-31"),
                item(id = 3, productName = "酸奶", printedExpiry = "2026-12-31", withImage = true)
            )
        )

        composeRule.onAllNodesWithContentDescription("扫描照片").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("无图片").assertCountEquals(2)
    }

    private fun setContent(items: List<HomeRecordItem>) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                HomeScreen(
                    records = items,
                    onRecordClick = {},
                    onNavigateToScan = {},
                    onNavigateToAbout = {},
                    onNavigateToSettings = {}
                )
            }
        }
    }

    private fun item(
        id: Long,
        productName: String,
        printedExpiry: String = "",
        shelfLife: String = "",
        withImage: Boolean = false
    ) = HomeRecordItem(
        record = ScanRecord(
            id = id,
            productName = productName,
            productionDate = "",
            expiryDate = printedExpiry,
            shelfLife = shelfLife,
            imageName = "",
            savedAt = 0
        ),
        expiry = ExpiryCalculator.resolve(printedExpiry, "", shelfLife),
        image = if (withImage) thumbnailSource else null
    )

    /** 截一张整屏图放到应用缓存目录，便于人工核对版式（缓存目录，系统可随时清掉） */
    private fun savePreview(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val PREVIEW_NAME = "home_list_preview.png"
    }
}
