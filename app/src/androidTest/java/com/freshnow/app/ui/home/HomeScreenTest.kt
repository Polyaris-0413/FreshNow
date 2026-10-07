package com.freshnow.app.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                // 快过期两档，含正好卡在阈值上的边界
                item(id = 4, productName = "快过期酸奶", printedExpiry = today.plusDays(2).toString()),
                item(id = 5, productName = "临期面包", printedExpiry = today.plusDays(3).toString()),
                item(id = 6, productName = "酸奶", shelfLife = "见包装")
            )
        )

        composeRule.onNodeWithText("纯牛奶").assertIsDisplayed()
        composeRule.onNodeWithText("还剩 10 天").assertIsDisplayed()
        composeRule.onNodeWithText("今天到期").assertIsDisplayed()
        composeRule.onNodeWithText("苏打饼干").assertIsDisplayed()
        composeRule.onNodeWithText("已过期 3 天").assertIsDisplayed()
        // 阈值边界本身也算快过期
        composeRule.onNodeWithText("还剩 2 天").assertIsDisplayed()
        composeRule.onNodeWithText("还剩 3 天").assertIsDisplayed()
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

    /** 长按是进入选择模式的唯一入口，只按一下不能选中 */
    @Test
    fun longPress_onRow_togglesSelection() {
        val toggled = mutableListOf<Long>()
        setContent(records(), onRecordToggle = { toggled += it })

        composeRule.onNodeWithText("纯牛奶").performTouchInput { longClick() }

        assertEquals(listOf(1L), toggled)
    }

    @Test
    fun tap_onRow_opensDetail() {
        val opened = mutableListOf<Long>()
        setContent(records(), onRecordClick = { opened += it })

        composeRule.onNodeWithText("纯牛奶").performClick()

        assertEquals(listOf(1L), opened)
    }

    /** 选择模式里点按改为切换选中：此时点一下是为了加选，不该跳去详情 */
    @Test
    fun tapInSelectionMode_togglesInsteadOfOpeningDetail() {
        val opened = mutableListOf<Long>()
        val toggled = mutableListOf<Long>()
        setContent(
            records(),
            selectedIds = setOf(2L),
            onRecordClick = { opened += it },
            onRecordToggle = { toggled += it }
        )

        composeRule.onNodeWithText("纯牛奶").performClick()

        assertEquals(emptyList<Long>(), opened)
        assertEquals(listOf(1L), toggled)
    }

    @Test
    fun selectionMode_replacesAppNameWithCountAndMenuWithDelete() {
        setContent(records(), selectedIds = setOf(1L, 3L))

        composeRule.onNodeWithText("已选 2 项").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除").assertIsDisplayed()
        // 应用名与「更多选项」都要让位，否则看不出正在选择
        composeRule.onNodeWithText("FreshNow").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("更多选项").assertCountEquals(0)

        savePreview(SELECTION_PREVIEW_NAME)
    }

    @Test
    fun exitSelection_returnsToNormalList() {
        var exited = false
        setContent(records(), selectedIds = setOf(1L), onExitSelection = { exited = true })

        composeRule.onNodeWithContentDescription("退出选择").performClick()

        assertTrue(exited)
    }

    /** 删除不可恢复，确认之前一条都不能动 */
    @Test
    fun deleteIcon_asksBeforeDeleting() {
        var deleted = false
        setContent(records(), selectedIds = setOf(1L), onDeleteSelected = { deleted = true })

        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.onNodeWithText("删除选中的 1 条记录？").assertIsDisplayed()
        assertFalse(deleted)

        composeRule.onNodeWithText("删除").performClick()
        assertTrue(deleted)
    }

    private fun records() = listOf(
        item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"),
        item(id = 2, productName = "苏打饼干", printedExpiry = "2026-12-31"),
        item(id = 3, productName = "酸奶", printedExpiry = "2026-12-31")
    )

    private fun setContent(
        items: List<HomeRecordItem>,
        selectedIds: Set<Long> = emptySet(),
        onRecordClick: (Long) -> Unit = {},
        onRecordToggle: (Long) -> Unit = {},
        onExitSelection: () -> Unit = {},
        onDeleteSelected: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                HomeScreen(
                    records = items,
                    selectedIds = selectedIds,
                    onRecordClick = onRecordClick,
                    onRecordToggle = onRecordToggle,
                    onExitSelection = onExitSelection,
                    onDeleteSelected = onDeleteSelected,
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
        const val SELECTION_PREVIEW_NAME = "home_selection_preview.png"
    }
}
