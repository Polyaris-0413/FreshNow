package com.freshnow.app.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 主题里的 primary，由 [setContent] 在合成里记下来，供描边像素判定用 */
    private var primaryArgb = 0

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

        onCountNode(2).assertIsDisplayed()
        composeRule.onNodeWithText(SELECTED_COUNT_PREFIX, substring = true).assertExists()
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

    /**
     * 相邻两条同时选中时，两条描边之间必须留缝。
     *
     * 描边贴着条目边界画的话，上一条的下边会和下一条的上边叠在一起，看起来是一条粗线。
     * 这里不去量具体像素，而是数「有描边像素的 y 连成了几段」：每条选中项各占一段
     * （左右两条竖边把该段的上下两条横边连起来），两条相邻的选中项中间应当断开。
     */
    @Test
    fun adjacentSelectedRows_leaveGapBetweenOutlines() {
        setContent(records(), selectedIds = setOf(1L, 2L))
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val hasOutline = BooleanArray(bitmap.height) { y ->
            (0 until bitmap.width).any { x -> isPrimary(bitmap.getPixel(x, y)) }
        }

        // 两段，而不是挤在一起的一段
        assertEquals(2, countRuns(hasOutline))
    }

    /**
     * 计数变化是滚动的，不是直接跳。
     *
     * 自己拿一份选中状态而不是走 [setContent]：这里要手动改状态、还要把动画时钟停在半路，
     * 与其它用例只摆一个静态状态不同。
     */
    @Test
    fun countChange_rollsInsteadOfJumping() {
        val selectedIds = mutableStateOf(setOf(1L))
        setContentTrackingSelection(selectedIds)

        onCountNode(1).assertIsDisplayed()

        // 停掉自动推进，改完状态把时钟停在动画走到一半的位置
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { selectedIds.value = setOf(1L, 2L) }
        composeRule.mainClock.advanceTimeBy(COUNT_ROLL_HALF_MS)

        // 滚到一半时新旧两个数字同时在——说明是在滚，而不是直接跳过去
        onCountNode(1).assertExists()
        onCountNode(2).assertExists()
        // 而前后两段固定文字各只有一个节点：滚的只有数字，整句没有跟着一起动
        composeRule.onAllNodesWithText(SELECTED_COUNT_PREFIX, substring = true).assertCountEquals(1)

        savePreview(COUNT_ROLL_PREVIEW_NAME)
    }

    /**
     * 取消掉最后一个选中项时，正在淡出的顶栏要停在它自己的计数上，
     * 不能先滚一遍「已选 0 项」再淡出——那是把同一件事演了两遍。
     */
    @Test
    fun deselectingLastItem_keepsLastCountWhileFadingOut() {
        val selectedIds = mutableStateOf(setOf(1L))
        setContentTrackingSelection(selectedIds)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { selectedIds.value = emptySet() }
        composeRule.mainClock.advanceTimeBy(COUNT_ROLL_HALF_MS)

        onCountNode(1).assertExists()
        composeRule.onNodeWithText("0", useUnmergedTree = true).assertDoesNotExist()
    }

    /** 需要一个改得动选中状态的容器，与其它用例只摆一个静态状态不同 */
    private fun setContentTrackingSelection(selectedIds: MutableState<Set<Long>>) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                HomeScreen(
                    records = records(),
                    selectedIds = selectedIds.value,
                    onRecordClick = {},
                    onRecordToggle = {},
                    onExitSelection = {},
                    onDeleteSelected = {},
                    onNavigateToScan = {},
                    onNavigateToAbout = {},
                    onNavigateToSettings = {}
                )
            }
        }
    }

    /**
     * 计数标题由「已选」「数字」「项」三段拼成，按未合并的树取数字那一节：
     * 合并后的父节点拿到的是三段文字的列表，写整句反而匹配不上。
     */
    private fun onCountNode(count: Int) =
        composeRule.onNodeWithText(count.toString(), useUnmergedTree = true)

    /** 选中框是实心描边，只有边缘会被抗锯齿磨淡，所以容一点通道差即可 */
    private fun isPrimary(pixel: Int): Boolean = abs(Color.red(pixel) - Color.red(primaryArgb)) <= CHANNEL_TOLERANCE &&
        abs(Color.green(pixel) - Color.green(primaryArgb)) <= CHANNEL_TOLERANCE &&
        abs(Color.blue(pixel) - Color.blue(primaryArgb)) <= CHANNEL_TOLERANCE

    /** 连续 true 的段数 */
    private fun countRuns(flags: BooleanArray): Int {
        var runs = 0
        var previous = false
        flags.forEach { current ->
            if (current && !previous) runs++
            previous = current
        }
        return runs
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
                // 断言描边颜色要用主题里的 primary，顺手在合成里记下来
                primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
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
        const val COUNT_ROLL_PREVIEW_NAME = "home_count_roll_preview.png"
        const val CHANNEL_TOLERANCE = 8

        /** 选中计数标题里数字之前那一段文字，用来断言它没有跟着数字一起动 */
        const val SELECTED_COUNT_PREFIX = "已选"

        /** 页内状态切换时长的一半，停在动画中途用；与 FreshNowTransitions 的 short4（200ms）对应 */
        const val COUNT_ROLL_HALF_MS = 100L
    }
}
