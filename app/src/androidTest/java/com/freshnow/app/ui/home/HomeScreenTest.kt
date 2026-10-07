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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.theme.FreshNowSize
import com.freshnow.app.ui.theme.FreshNowSpacing
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

    /** 一条记录都没有时要给一句话。空态现在也是列表里的一个条目（见 RecordsList），别被列表吃掉 */
    @Test
    fun emptyRecordList_showsEmptyHint() {
        setContent(emptyList())

        composeRule.onNodeWithText("还没有扫描记录").assertIsDisplayed()
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
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)

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
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)

        onCountNode(1).assertExists()
        composeRule.onNodeWithText("0", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * 删掉一条时，下面的条目要滑上去，而不是直接跳到新位置。
     *
     * 「酸奶」排在最后，前面删掉一条后它该落到上一格。动画走到一半时它必须已经离开原位、
     * 又还没到位——两条同时成立，才说明它在路上，而不是一步跳过去。
     */
    @Test
    fun deletingRow_slidesRowsBelowToNewPosition() {
        val records = mutableStateOf(records())
        setContentTracking(records, mutableStateOf(emptySet()))

        val start = composeRule.onNodeWithText("酸奶").getBoundsInRoot().top

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { records.value = records().drop(1) }
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)
        val middle = composeRule.onNodeWithText("酸奶").getBoundsInRoot().top

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = composeRule.onNodeWithText("酸奶").getBoundsInRoot().top
        composeRule.mainClock.autoAdvance = true

        assertTrue("中途应当已经离开原位：start=$start middle=$middle", middle < start)
        assertTrue("中途应当还没到位：middle=$middle end=$end", middle > end)
    }

    /**
     * 被删的那条要淡出，不能"啪"地消失。
     *
     * 判据取缩略图中心那一个像素，删除前后都在同一处比：淡到一半时它既不是删除前的原色，
     * 也不是删完后的背景色。两个"都不是"同时成立，才说明它正在淡，而不是一步就没。
     */
    @Test
    fun deletedRow_fadesOutInsteadOfVanishing() {
        val records = mutableStateOf(
            listOf(
                item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31", withImage = true),
                item(id = 2, productName = "苏打饼干", printedExpiry = "2026-12-31"),
                item(id = 3, productName = "酸奶", printedExpiry = "2026-12-31")
            )
        )
        setContentTracking(records, mutableStateOf(emptySet()))

        // 按未合并的树取图片本身：条目是可点击的，它的语义会把里面的文字与图片合并成一行，
        // 合并后的节点是整行而不是那块缩略图
        val thumbnail = composeRule
            .onNodeWithContentDescription("扫描照片", useUnmergedTree = true)
            .getBoundsInRoot()
        // 取点要落到像素上，边界是 dp，得按屏幕密度换算
        val x = with(composeRule.density) { (thumbnail.left + thumbnail.width / 2).roundToPx() }
        val y = with(composeRule.density) { (thumbnail.top + thumbnail.height / 2).roundToPx() }
        fun samplePixel() = composeRule.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y)

        val before = samplePixel()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { records.value = records.value.drop(1) }
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)
        val middle = samplePixel()

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val after = samplePixel()
        composeRule.mainClock.autoAdvance = true

        assertTrue("半途应当还没淡干净：before=$before middle=$middle after=$after", differs(middle, after))
        assertTrue("半途应当已经淡下去一些：before=$before middle=$middle", differs(middle, before))
    }

    /** 两个颜色是否看得出差别：通道差超过容差即可，不比较具体颜色，免得把配色写进断言 */
    private fun differs(a: Int, b: Int): Boolean = channelDifference(a, b) > CHANNEL_TOLERANCE

    private fun channelDifference(a: Int, b: Int): Int = maxOf(
        abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)),
        abs(Color.blue(a) - Color.blue(b))
    )

    /**
     * 删掉最后一条时，空态文案要淡入，不能硬出现。
     *
     * 判据取文案那一块里"与页面底色差得最远的那个像素"（记作对比度）：淡到一半时它应当
     * 已经看得见（大于 0），但还没到最终的样子（小于走完后的对比度）。两条同时成立才是淡入；
     * 若是一步到位，中途的对比度会直接等于最终值。
     */
    @Test
    fun emptyHint_fadesInAfterLastRowIsDeleted() {
        val records = mutableStateOf(
            listOf(item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"))
        )
        setContentTracking(records, mutableStateOf(emptySet()))

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { records.value = emptyList() }
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)

        val region = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot()
        val middle = maxContrast(region)

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue("半途应当已经看得见：middle=$middle", middle > 0)
        assertTrue("半途应当还没到最终的样子：middle=$middle end=$end", middle < end)
    }

    /**
     * 本页重新合成时（转场返回、转屏、冷启动），列表整片淡入，而不是直接出现。
     *
     * 用空态那一屏来验：冷启动时列表先是空的，空态文案正是「跟着这次合成一起出现」的东西，
     * 而它恰好是 LazyLayout 认不出、animateItem 演不出来的那一种。时钟停在合成处，
     * 第一帧它还不该看得见；走到一半应当看得见、但还没到最终的样子。
     */
    @Test
    fun listFadesInOnFirstComposition() {
        composeRule.mainClock.autoAdvance = false
        setContent(emptyList())

        val region = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot()
        val atStart = maxContrast(region)

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)
        val middle = maxContrast(region)

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertEquals("合成后的第一帧还不该看得见：atStart=$atStart", 0, atStart)
        assertTrue("半途应当已经看得见：middle=$middle", middle > 0)
        assertTrue("半途应当还没到最终的样子：middle=$middle end=$end", middle < end)
    }

    /**
     * 回到主页时新记录已经在列表里了（`WhileSubscribed` 那 5 秒窗口内保存的就是这种），
     * 它仍要淡入——这一条是「有时能演、有时不能」的正主：原来的判据落在合成时机上，
     * 条目跟着列表一起首次合成时 animateItem 认不出它是新的，于是整片列表只有整页在淡。
     *
     * 新记录慢一帧才进列表（见 RecordsList），所以取样前要先让那一帧过去。
     */
    @Test
    fun newRowAlreadyInListAtComposition_fadesIn() {
        composeRule.mainClock.autoAdvance = false
        setContent(
            items = listOf(
                item(id = 3, productName = "新扫的酸奶", printedExpiry = "2026-12-31"),
                item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31")
            ),
            newRecordIds = setOf(3L)
        )

        // 第一帧上它还没落地：新记录要先等上一批摆好
        composeRule.onNodeWithText("新扫的酸奶").assertDoesNotExist()

        // 取样从半程开始：这一帧上旧条目正从上方滑过，取到的对比度是它的，不是新条目的
        composeRule.mainClock.advanceTimeBy(DEFER_FRAME_MS + STATE_CHANGE_HALF_MS)
        val middle = maxContrast(composeRule.onNodeWithText("新扫的酸奶").getBoundsInRoot())

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = maxContrast(composeRule.onNodeWithText("新扫的酸奶").getBoundsInRoot())
        composeRule.mainClock.autoAdvance = true

        assertTrue(
            "半程应当已经看得见、但还没到最终的样子（在淡，而不是一步到位）：middle=$middle end=$end",
            middle in 1 until end
        )
    }

    /**
     * 新记录出现时，已在列表里的条目要从旧位置滑到新位置，而不是直接出现在该在的地方。
     *
     * 这一条盯的是位移：它和淡入是两件事，判据也不同——淡入看那个条目的对比度变化，
     * 位移看下面那条的落在这两点之间。新记录慢一帧进列表，正是为了让 LazyLayout 手里有一份
     * 「上一轮的位置」可比较；没有它，条目只会被直接画在最终位置上。
     */
    @Test
    fun newRowAlreadyInListAtComposition_pushesRowsBelowDown() {
        composeRule.mainClock.autoAdvance = false
        setContent(
            items = listOf(
                item(id = 3, productName = "新扫的酸奶", printedExpiry = "2026-12-31"),
                item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"),
                item(id = 2, productName = "苏打饼干", printedExpiry = "2026-12-31")
            ),
            newRecordIds = setOf(3L)
        )

        // 第一帧上只有上一批，旧条目还在上面的旧位置
        val start = composeRule.onNodeWithText("纯牛奶").getBoundsInRoot().top

        // 让新记录进场，再走半程
        composeRule.mainClock.advanceTimeBy(DEFER_FRAME_MS + STATE_CHANGE_HALF_MS)
        val middle = composeRule.onNodeWithText("纯牛奶").getBoundsInRoot().top

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = composeRule.onNodeWithText("纯牛奶").getBoundsInRoot().top
        composeRule.mainClock.autoAdvance = true

        assertTrue("中途应当已经被推下去：start=$start middle=$middle", middle > start)
        assertTrue("中途应当还没滑到位：middle=$middle end=$end", middle < end)
    }

    /**
     * 已经露过面的条目不该再淡一遍，它们跟着整页出现就行。
     *
     * 若这里也淡，用户看到的是"整片列表在淡"，恰恰看不出是哪一条新加进来的——上一版就是
     * 用整片淡入盖住了出现的差异，问题看着像"有时有动画、有时没有"。
     */
    @Test
    fun alreadyShownRows_doNotFade() {
        composeRule.mainClock.autoAdvance = false
        setContent(
            items = listOf(
                item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"),
                item(id = 2, productName = "苏打饼干", printedExpiry = "2026-12-31")
            )
        )

        val region = composeRule.onNodeWithText("纯牛奶").getBoundsInRoot()
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)
        val middle = maxContrast(region)

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue("中途不该有半透明的时候：middle=$middle end=$end", abs(middle - end) <= 1)
    }

    /**
     * 还没查到记录时不能先下结论说「没有记录」。
     *
     * 冷启动时 Flow 的第一条要等查库，若这一段时间按空列表画，用户会先看到一句
     * 「还没有扫描记录」，等记录到达再被硬切掉——同一屏里前后两个互相矛盾的结论。
     * 这里把「数据没到」与「确实没有」摆成两屏对比：前者不该出现任何空态文案。
     */
    @Test
    fun loadingRecords_showsNoEmptyHint() {
        setContent(items = null)

        composeRule.onNodeWithText("还没有扫描记录").assertDoesNotExist()
    }

    /** 空态：一条记录都没有时要给一句话，正上方那枚曲奇也要真的画出来 */
    @Test
    fun emptyRecords_showEmptyHint() {
        setContent(items = emptyList<HomeRecordItem>())

        composeRule.onNodeWithText("还没有扫描记录").assertIsDisplayed()

        // 曲奇是装饰、没有 contentDescription，只能按位置验：文案上方紧挨着的那一块必须有画东西
        val textTop = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot().top
        assertTrue(
            "文案正上方应当画着曲奇（空态整块居中，曲奇在文案之上一个组件间距处）",
            drawsSomethingBetween(
                top = textTop - FreshNowSpacing.sm - FreshNowSize.icon,
                bottom = textTop - FreshNowSpacing.sm
            )
        )
    }

    /** 这条 y 带内有没有画了东西的像素：装饰图形没有语义节点，只能这样验收 */
    private fun drawsSomethingBetween(top: Dp, bottom: Dp): Boolean {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val background = bitmap.getPixel(0, bitmap.height - 1)
        val from = with(composeRule.density) { top.roundToPx() }.coerceIn(0, bitmap.height - 1)
        val to = with(composeRule.density) { bottom.roundToPx() }.coerceIn(0, bitmap.height)
        return (from until to).any { y ->
            (0 until bitmap.width).any { x ->
                channelDifference(bitmap.getPixel(x, y), background) > CHANNEL_TOLERANCE
            }
        }
    }

    /** 该区域里与页面底色差得最远的那个像素差多少：文字淡入时它会从 0 涨到最终值 */
    private fun maxContrast(region: DpRect): Int {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val background = bitmap.getPixel(0, bitmap.height - 1)
        val left = with(composeRule.density) { region.left.roundToPx() }.coerceIn(0, bitmap.width - 1)
        val top = with(composeRule.density) { region.top.roundToPx() }.coerceIn(0, bitmap.height - 1)
        val right = with(composeRule.density) { region.right.roundToPx() }.coerceIn(0, bitmap.width)
        val bottom = with(composeRule.density) { region.bottom.roundToPx() }.coerceIn(0, bitmap.height)

        var max = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val difference = channelDifference(bitmap.getPixel(x, y), background)
                if (difference > max) max = difference
            }
        }
        return max
    }

    /**
     * 新记录出现时要淡入，不能凭空冒出来；同一时间它把下面的条目推下去。
     *
     * 判据与删除那条对称：新记录插在最前面，占的正是原来第一条的位置，取样点因此取原来
     * 第一条的缩略图中心——淡到一半时它既不是插入前的占位灰，也不是淡完后的照片色。
     */
    @Test
    fun newRow_fadesInAndPushesRowsBelowDown() {
        val records = mutableStateOf(
            listOf(
                item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"),
                item(id = 2, productName = "苏打饼干", printedExpiry = "2026-12-31")
            )
        )
        // 新记录是随这次数据更新一起被标成"刚存进来的"，与 HomeViewModel 里同一步
        val newRecordIds = mutableStateOf(emptySet<Long>())
        setContentTracking(records, mutableStateOf(emptySet()), newRecordIds)

        val firstThumbnail = composeRule
            .onAllNodesWithContentDescription("无图片", useUnmergedTree = true)[0]
            .getBoundsInRoot()
        val x = with(composeRule.density) { (firstThumbnail.left + firstThumbnail.width / 2).roundToPx() }
        val y = with(composeRule.density) { (firstThumbnail.top + firstThumbnail.height / 2).roundToPx() }
        fun samplePixel() = composeRule.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y)

        val before = samplePixel()
        val pushedStart = composeRule.onNodeWithText("苏打饼干").getBoundsInRoot().top

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            records.value = listOf(
                item(id = 3, productName = "新扫的酸奶", printedExpiry = "2026-12-31", withImage = true)
            ) + records.value
            newRecordIds.value = setOf(3L)
        }
        // 取样从半程开始：新记录慢一帧才进列表，而它刚进场时旧条目正从上方滑过，
        // 那一带的像素还不是它的（占位图标与页面底色在深色主题下几乎同色，比也白比）
        composeRule.mainClock.advanceTimeBy(DEFER_FRAME_MS + STATE_CHANGE_HALF_MS)
        val middle = samplePixel()
        val pushedMiddle = composeRule.onNodeWithText("苏打饼干").getBoundsInRoot().top

        // 再走一段，越过整段时长让动画收尾
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val after = samplePixel()
        val pushedEnd = composeRule.onNodeWithText("苏打饼干").getBoundsInRoot().top
        composeRule.mainClock.autoAdvance = true

        assertTrue("半途应当还没淡完：before=$before middle=$middle after=$after", differs(middle, after))
        assertTrue("半途应当已经淡出来一些：before=$before middle=$middle", differs(middle, before))
        assertTrue("下面的条目中途应当已经被推下去：start=$pushedStart middle=$pushedMiddle", pushedMiddle > pushedStart)
        assertTrue("中途应当还没推到位：middle=$pushedMiddle end=$pushedEnd", pushedMiddle < pushedEnd)
    }

    /** 需要一个改得动列表或选中状态的容器，与其它用例只摆一个静态状态不同 */
    private fun setContentTracking(
        records: MutableState<List<HomeRecordItem>>,
        selectedIds: MutableState<Set<Long>>,
        newRecordIds: MutableState<Set<Long>> = mutableStateOf(emptySet())
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                HomeScreen(
                    records = records.value,
                    newRecordIds = newRecordIds.value,
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

    /** 需要一个改得动选中状态的容器，与其它用例只摆一个静态状态不同 */
    private fun setContentTrackingSelection(selectedIds: MutableState<Set<Long>>) {
        setContentTracking(mutableStateOf(records()), selectedIds)
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
        items: List<HomeRecordItem>?,
        newRecordIds: Set<Long> = emptySet(),
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
                    newRecordIds = newRecordIds,
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
        const val STATE_CHANGE_HALF_MS = 100L

        /** 新记录慢一帧进列表，取样前要越过这一帧；两帧是因为写完状态还要等下一帧重新合成 */
        const val DEFER_FRAME_MS = 32L
    }
}
