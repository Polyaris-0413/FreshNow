package com.freshnow.app.ui.detail

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ScanImageStore
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.theme.FreshNowTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private var recordId: Long = 0
    private var imageNames: List<String> = emptyList()
    private lateinit var viewModel: RecordDetailViewModel

    @After
    fun cleanUp() {
        runBlocking {
            if (recordId != 0L) {
                FreshNowDatabase.getInstance(application).scanRecordDao().hardDelete(listOf(recordId))
            }
            imageNames.forEach { ScanImageStore(application).delete(it) }
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
        composeRule.onNodeWithText(VIEWER_TITLE).assertIsDisplayed()
        saveScreenshot("detail-photo-viewer.png")

        // 直接触发 BackHandler，不走系统返回键：targetSdk 37 上系统返回会先跑 predictive back 那段
        // 预测动画，动画期间窗口一直在请求布局，Espresso 等不到「窗口稳定」就抛
        // RootViewWithoutFocusException（这个用例先前就偶发挂在这里）。
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText(VIEWER_TITLE).assertDoesNotExist()
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
    }

    /**
     * 点开照片是「照片长到整屏」的过渡，不是硬切。
     *
     * 硬切也能通过[前面的]开关断言，所以这里比的是三帧：点开前、过渡中途、过渡结束。
     * 时钟放开手动推进，才能在中间取到一帧；中途那一帧与结束那一帧不同，就说明它没有
     * 一步到位。
     */
    @Test
    fun openingTheViewerIsAnimatedNotAHardCut() {
        seedRecordWithPhoto()
        setContent()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(PHOTO).fetchSemanticsNodes().isNotEmpty()
        }
        val before = capture()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription(PHOTO).performClick()
        // 取过渡开头一帧：M3 的 fast spatial 大约 300ms 收住，头一帧必然还在半路上
        composeRule.mainClock.advanceTimeBy(MID_SAMPLE_MS)
        val middle = capture()
        saveScreenshot("detail-photo-open-mid.png")

        // 一帧一帧推到画面不再变：既是「走到终点」，也顺手量出这段过渡用了多久
        var elapsed = MID_SAMPLE_MS
        var end = middle
        var last = middle
        while (elapsed < MAX_TRANSITION_MS) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            elapsed += FRAME_MS
            val now = capture()
            if (diff(last, now) == 0) break
            last = now
            end = now
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue("点开后画面应变了", diff(before, middle) > 0)
        assertTrue("过渡中途应与终止画面不同（相同即硬切）", diff(middle, end) > 0)
        // 上界挡「拖泥带水」：M3 的 fast spatial 约 300ms 收住，放宽到 500ms 留余量
        assertTrue("过渡不该拖泥带水：约 ${elapsed}ms", elapsed < 500)
        saveScreenshot("detail-photo-open-end.png")
    }

    private fun capture() = composeRule.onRoot().captureToImage().toPixelMap()

    /** 两帧之间有多少像素不一样 */
    private fun diff(a: androidx.compose.ui.graphics.PixelMap, b: androidx.compose.ui.graphics.PixelMap): Int {
        var count = 0
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a[x, y] != b[x, y]) count++
            }
        }
        return count
    }

    private fun setContent() {
        viewModel = RecordDetailViewModel(application)
        composeRule.setContent {
            FreshNowTheme {
                RecordDetailScreen(
                    recordId = recordId,
                    onBack = {},
                    onNavigateToEdit = {},
                    viewModel = viewModel
                )
            }
        }
    }

    /**
     * 两张时主图右侧给一枚「下一张」的箭头：缩略图那一排已经拆了（它把正文挤下去），
     * 翻页的指引改压在照片边缘上
     */
    @Test
    fun twoImagesShowNextArrow() {
        seedRecordWithPhoto(count = 2)
        setContent()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(NEXT).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(NEXT).assertIsDisplayed()
        // 站在第一张上，左侧没有上一张
        composeRule.onNodeWithContentDescription(PREVIOUS).assertDoesNotExist()
    }

    /** 一张时两枚箭头都不给：没得翻，画了反而让人以为还有图 */
    @Test
    fun singleImageHasNoPagingArrows() {
        seedRecordWithPhoto()
        setContent()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(PHOTO).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(NEXT).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(PREVIOUS).assertDoesNotExist()
    }

    /** 点「下一张」，主图换成它，并且这时左边出现「上一张」 */
    @Test
    fun tappingNextArrowSwapsTheMainImage() {
        seedRecordWithPhoto(count = 2)
        setContent()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(NEXT).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(NEXT).performClick()
        composeRule.waitForIdle()

        assertEquals("主图下标该跟着换", 1, viewModel.uiState.value.mainIndex)
        composeRule.onNodeWithContentDescription(PREVIOUS).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(NEXT).assertDoesNotExist()
    }

    /**
     * 一张照片都没有时是占位图，而且点不开看图页（看图页里没有任何图可看）。
     * 这是 Review Focus 里那条「0 张照片」：老记录、用户把图全删了、同步来的一条没图，
     * 三种情形在这一页长得一样。
     */
    @Test
    fun noImageShowsPlaceholderAndIsNotClickable() {
        seedRecordWithPhoto(count = 0)
        setContent()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(MISSING).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(MISSING).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(VIEWER_TITLE).assertDoesNotExist()
    }

    /**
     * 造一条带照片的记录：图片走真的 ScanImageStore（名字就是内容散列），记录进真的库。
     *
     * 多张时每张颜色不同：内容寻址下同色的图会算出同一个名字，那条记录其实只有一张。
     */
    private fun seedRecordWithPhoto(count: Int = 1) {
        val colors = listOf(Color.RED, Color.BLUE, Color.GREEN)
        imageNames = (0 until count).map { index ->
            val photo = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
                eraseColor(colors[index % colors.size])
            }
            val bytes = ByteArrayOutputStream().use { out ->
                photo.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
            runBlocking { ScanImageStore(application).write(bytes) }.orEmpty()
        }
        recordId = runBlocking {
            FreshNowDatabase.getInstance(application).scanRecordDao().insert(
                ScanRecord(
                    productName = "全脂纯牛奶",
                    productionDate = "2026-10-01",
                    expiryDate = "2026-12-01",
                    shelfLife = "2个月",
                    imageNames = imageNames,
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
        /** 没有照片时的占位图描述 */
        const val MISSING = "无图片"
        /** 主图左右那两枚翻页箭头 */
        const val NEXT = "下一张"
        const val PREVIOUS = "上一张"
        /** 取样与步进：一帧 16ms，开头取一帧当「半路」的样本 */
        const val FRAME_MS = 16L
        const val MID_SAMPLE_MS = 80L
        /** 允许的最长过渡：只用来挡「拖泥带水」 */
        const val MAX_TRANSITION_MS = 1_200L
        /** 看图页的标题：详情页里同样的字是照片的内容描述，不是文本，两处不会撞 */
        const val VIEWER_TITLE = "扫描照片"
    }
}
