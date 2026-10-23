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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.R
import com.freshnow.app.data.ExpiryCalculator
import com.freshnow.app.data.SortOrder
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.ui.CHANNEL_TOLERANCE
import com.freshnow.app.ui.differs
import com.freshnow.app.ui.drawsSomethingBetween
import com.freshnow.app.ui.maxContrast
import com.freshnow.app.ui.sync.SyncMessage
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

    private val appName = InstrumentationRegistry.getInstrumentation()
        .targetContext.getString(R.string.app_name)

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
        composeRule.onNodeWithText("还剩 2 天").assertIsDisplayed()
        composeRule.onNodeWithText("还剩 3 天").assertIsDisplayed()
        // 品名为空的记录不能是一片空白
        composeRule.onNodeWithText("未知").assertIsDisplayed()
        composeRule.onNodeWithText("过期时间未知").assertIsDisplayed()

        savePreview(PREVIEW_NAME)
    }

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

    /** 空态现在也是列表里的一个条目（见 RecordsList），别被列表吃掉 */
    @Test
    fun emptyRecordList_showsEmptyHint() {
        setContent(emptyList())

        composeRule.onNodeWithText("还没有扫描记录").assertIsDisplayed()
    }

    /** 长按是进入选择模式的唯一入口 */
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

    /** 此时点一下是为了加选，不该跳去详情 */
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
        // 应用名与「更多选项」都要让位，否则看不出正在选择。
        // 名字从资源取而不是写死 "FreshNow"：中文环境下它是「食不宜迟」
        composeRule.onNodeWithText(appName).assertDoesNotExist()
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

    /** 删除不可恢复 */
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

        assertEquals(2, countRuns(hasOutline))
    }

    /**
     * 自己拿一份选中状态而不是走 [setContent]：要手动改状态并把动画时钟停在半路。
     */
    @Test
    fun countChange_rollsInsteadOfJumping() {
        val selectedIds = mutableStateOf(setOf(1L))
        setContentTrackingSelection(selectedIds)

        onCountNode(1).assertIsDisplayed()

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
     * 取消最后一个选中项时，正在淡出的顶栏要停在它自己的计数上，不能先滚一遍「已选 0 项」再淡出——那是把同一件事演了两遍。
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
     * 判据：动画半程时「酸奶」已离开原位、又还没到位，两条同时成立才说明它在路上。
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

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = composeRule.onNodeWithText("酸奶").getBoundsInRoot().top
        composeRule.mainClock.autoAdvance = true

        assertTrue("中途应当已经离开原位：start=$start middle=$middle", middle < start)
        assertTrue("中途应当还没到位：middle=$middle end=$end", middle > end)
    }

    /**
     * 判据取缩略图中心像素：半程时既不是删除前的原色，也不是删完后的背景色。
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
        /**
         * 取 1/4 处而不是半程：M3 的 standard 曲线起手快，半程时已淡到接近底色，像素探针分不出它和收尾后的差别。
         */
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS / 2)
        val middle = samplePixel()

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val after = samplePixel()
        composeRule.mainClock.autoAdvance = true

        assertTrue("半途应当还没淡干净：before=$before middle=$middle after=$after", differs(middle, after))
        assertTrue("半途应当已经淡下去一些：before=$before middle=$middle", differs(middle, before))
    }

    /**
     * 第一条记录出现时，空态要淡出——它和记录行是同一次换位的两半，不该硬消失。
     *
     * 判据：取样那块（空态文案所在）在换位半程上应当还看得见、但比原来淡；换位结束之后那块
     * 应当只剩底色（文案不在那儿了，记录行在上方）。
     */
    @Test
    fun emptyHint_fadesOutWhenFirstRowArrives() {
        val records = mutableStateOf(emptyList<HomeRecordItem>())
        val newRecordIds = mutableStateOf(emptySet<Long>())
        setContentTracking(records, mutableStateOf(emptySet()), newRecordIds)

        // 先让空态淡到位，取它的满值作对照
        composeRule.waitForIdle()
        val region = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot()
        val full = composeRule.maxContrast(region)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            records.value = listOf(item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"))
            newRecordIds.value = setOf(1L)
        }

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)
        val middle = composeRule.maxContrast(region)

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_MS * 2)
        val end = composeRule.maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue("半途应当已经淡下去一些、但还没淡完：full=$full middle=$middle", middle in 1 until full)
        assertEquals("淡完之后那块应当只剩底色：end=$end", 0, end)
    }

    /**
     * 删掉最后一条时，空态要淡入，而且**不是紧接着条目淡出就淡**。
     *
     * 判据分三段：条目还在淡出的那半程里它必须还看不见——确认删除的对话框正是在这段时间退场，
     * 跟着一起淡等于前半段整段被对话框盖住，用户看到的就是「对话框一关它已经在那儿」；
     * 等过这一拍才开始淡入，半途看得见但没到最终的样子。
     */
    @Test
    fun emptyHint_fadesInAfterLastRowIsDeleted() {
        val records = mutableStateOf(
            listOf(item(id = 1, productName = "纯牛奶", printedExpiry = "2026-12-31"))
        )
        val justEmptied = mutableStateOf(false)
        setContentTracking(records, mutableStateOf(emptySet()), justEmptied = justEmptied)

        composeRule.mainClock.autoAdvance = false
        // 与 HomeViewModel 同步：这一次查库是从"有"变成"没有"
        composeRule.runOnUiThread {
            records.value = emptyList()
            justEmptied.value = true
        }

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)
        val region = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot()
        assertEquals("条目淡出没结束前不该已经看得见：${composeRule.maxContrast(region)}", 0, composeRule.maxContrast(region))

        // 等过这一拍，它才开始淡入
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_MS)
        val middle = composeRule.maxContrast(region)

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_MS * 2)
        val end = composeRule.maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue("半途应当已经看得见、但还没到最终的样子：middle=$middle end=$end", middle in 1 until end)
    }

    /**
     * 从别的页面回到主页时，空态不该再淡一遍。
     * 那不是变化：空态早就显示过，那次它跟着整页一起出现就够了。回到本页是一次新的合成，若照合成演一遍，
     * 用户每次回来都会看到它重新淡入一次——正是这条要拦住的。
     * 冷启动那一次不归这条管，由 firstEmptyLoad 单独驱动（见 emptyHint_fadesInOnFirstLoad）。
     * 判据取「自始至终都是满的」：不演淡入时它从第一帧起就是终态，没有任何半透明的时刻。
     */
    @Test
    fun emptyHint_doesNotFadeOnReentry() {
        composeRule.mainClock.autoAdvance = false
        // 本页重新合成、并不是"刚被删空"、也不是"第一次查库就是空"，与 HomeViewModel 一致
        setContent(items = emptyList<HomeRecordItem>())

        val region = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot()
        val atStart = composeRule.maxContrast(region)

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_MS * 2)
        val late = composeRule.maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue(
            "空态不该因重新合成而淡入（应当一出现就是终态）：atStart=$atStart late=$late",
            atStart > 0 && atStart == late
        )
    }

    /**
     * 第一次进主页（刚走完引导那一次）空态要淡入：整棵列表都是新合成的，硬出现就是一枚曲奇凭空冒出。
     * 与删空那一档的区别是不等一拍——冷启动前面没有条目淡出与对话框退场要等。
     */
    @Test
    fun emptyHint_fadesInOnFirstLoad() {
        composeRule.mainClock.autoAdvance = false
        // 与 HomeViewModel.firstEmptyLoad 同步：这个 ViewModel 第一次查库就是空的
        setContent(items = emptyList<HomeRecordItem>(), firstEmptyLoad = true)

        val region = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot()
        assertEquals("第一帧还看不见：${composeRule.maxContrast(region)}", 0, composeRule.maxContrast(region))

        // 半程：已经在淡入的路上（没有延迟，所以这时候看得见一点）
        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS)
        val middle = composeRule.maxContrast(region)

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_MS * 2)
        val end = composeRule.maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue("半程应当已经看得见、但还没到最终的样子：middle=$middle end=$end", middle in 1 until end)
    }

    /**
     * 回到主页时新记录已经在列表里了（`WhileSubscribed` 那 5 秒窗口内保存的就是这种），它仍要淡入。
     * 判据不能再落在合成时机上：跟着列表首次合成时 animateItem 认不出它是新的。
     * 新记录慢一帧才进列表（见 RecordsList），取样前要先让那一帧过去。
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

        // 新记录要先等上一批摆好，所以第一帧上还没有它
        composeRule.onNodeWithText("新扫的酸奶").assertDoesNotExist()

        // 取样从半程开始：这一帧上旧条目正从上方滑过，取到的对比度是它的，不是新条目的
        composeRule.mainClock.advanceTimeBy(DEFER_FRAME_MS + STATE_CHANGE_HALF_MS)
        val middle = composeRule.maxContrast(composeRule.onNodeWithText("新扫的酸奶").getBoundsInRoot())

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = composeRule.maxContrast(composeRule.onNodeWithText("新扫的酸奶").getBoundsInRoot())
        composeRule.mainClock.autoAdvance = true

        assertTrue(
            "半程应当已经看得见、但还没到最终的样子（在淡，而不是一步到位）：middle=$middle end=$end",
            middle in 1 until end
        )
    }

    /**
     * 位移的判据与淡入不同：淡入看对比度，位移看下面那条落在两点之间。
     * 新记录慢一帧进列表，正是让 LazyLayout 手里有一份「上一轮的位置」可比较；没有它，条目只会被直接画在最终位置上。
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

        composeRule.mainClock.advanceTimeBy(DEFER_FRAME_MS + STATE_CHANGE_HALF_MS)
        val middle = composeRule.onNodeWithText("纯牛奶").getBoundsInRoot().top

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = composeRule.onNodeWithText("纯牛奶").getBoundsInRoot().top
        composeRule.mainClock.autoAdvance = true

        assertTrue("中途应当已经被推下去：start=$start middle=$middle", middle > start)
        assertTrue("中途应当还没滑到位：middle=$middle end=$end", middle < end)
    }

    /**
     * 已经露过面的条目不该再淡一遍，跟着整页出现就行。
     * 若这里也淡，用户看到的是「整片列表在淡」，恰恰看不出哪一条是新的。
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
        val middle = composeRule.maxContrast(region)

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val end = composeRule.maxContrast(region)
        composeRule.mainClock.autoAdvance = true

        assertTrue("中途不该有半透明的时候：middle=$middle end=$end", abs(middle - end) <= 1)
    }

    /**
     * 还没查到记录时不能先下结论说「没有记录」。
     * 冷启动时 Flow 的第一条要等查库，这段时间按空列表画会先闪一句「还没有扫描记录」，再被硬切掉。
     * 判据：把「数据没到」与「确实没有」摆成两屏对比，前者不该出现任何空态文案。
     */
    @Test
    fun loadingRecords_showsNoEmptyHint() {
        setContent(items = null)

        composeRule.onNodeWithText("还没有扫描记录").assertDoesNotExist()
    }

    @Test
    fun emptyRecords_showEmptyHint() {
        setContent(items = emptyList<HomeRecordItem>())

        composeRule.onNodeWithText("还没有扫描记录").assertIsDisplayed()

        // 曲奇是装饰、没有 contentDescription，只能按位置验：文案上方紧挨着的那一块必须有画东西
        val textTop = composeRule.onNodeWithText("还没有扫描记录").getBoundsInRoot().top
        assertTrue(
            "文案正上方应当画着曲奇（空态整块居中，曲奇在文案之上一个组件间距处）",
            composeRule.drawsSomethingBetween(
                top = textTop - FreshNowSpacing.sm - FreshNowSize.icon,
                bottom = textTop - FreshNowSpacing.sm
            )
        )
    }

    /**
     * 判据与删除那条对称：取样点取原来第一条的缩略图中心，半程时既不是占位灰，也不是照片色。
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

        composeRule.mainClock.advanceTimeBy(STATE_CHANGE_HALF_MS * 2)
        val after = samplePixel()
        val pushedEnd = composeRule.onNodeWithText("苏打饼干").getBoundsInRoot().top
        composeRule.mainClock.autoAdvance = true

        assertTrue("半途应当还没淡完：before=$before middle=$middle after=$after", differs(middle, after))
        assertTrue("半途应当已经淡出来一些：before=$before middle=$middle", differs(middle, before))
        assertTrue("下面的条目中途应当已经被推下去：start=$pushedStart middle=$pushedMiddle", pushedMiddle > pushedStart)
        assertTrue("中途应当还没推到位：middle=$pushedMiddle end=$pushedEnd", pushedMiddle < pushedEnd)
    }

    /**
     * 行为开关「手动输入」决定「添加」去哪一页：关着进扫描，开着进录入页。
     * 两条路在同一个用例里各走一遍：判据的全部内容就是「两者取其一」，拆开就断不出「两个回调都被叫到」。
     */
    @Test
    fun addGoesToScanOrManualEntryByBehaviorSwitch() {
        val manualEntry = mutableStateOf(false)
        val targets = mutableListOf<String>()
        composeRule.setContent {
            FreshNowTheme() {
                HomeScreen(
                    records = null,
                    newRecordIds = emptySet(),
                    justEmptied = false,
                    firstEmptyLoad = false,
                    selectedIds = emptySet(),
                    manualEntry = manualEntry.value,
                    sortOrder = SortOrder.CREATED_AT,
                    onSortOrderChange = {},
                    onRecordClick = {},
                    onRecordToggle = {},
                    onExitSelection = {},
                    onDeleteSelected = {},
                    refreshing = false,
                    canRefresh = true,
                    syncMessage = null,
                    onSync = {},
                    onSyncMessageShown = {},
                    onNavigateToScan = { targets += SCAN },
                    onNavigateToManualEntry = { targets += MANUAL_ENTRY },
                    onNavigateToAbout = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("添加").performClick()
        composeRule.runOnUiThread { manualEntry.value = true }
        composeRule.onNodeWithContentDescription("添加").performClick()

        assertEquals("关着时进扫描、开着时进录入页", listOf(SCAN, MANUAL_ENTRY), targets)
    }

    /**
     * 当前那一项要标成选中：再打开面板时得认得出现在用的是哪种。顺带盯住这个动作在 overflow 之前。
     */
    @Test
    fun sortAction_offersBothOrdersAndReportsTheChoice() {
        val changes = mutableListOf<SortOrder>()
        setContent(items = records(), onSortOrderChange = { changes += it })

        composeRule.onNodeWithContentDescription("排序").performClick()
        composeRule.onNodeWithText("创建时间").assertIsSelected()
        composeRule.onNodeWithText("过期日期").assertIsNotSelected()

        composeRule.onNodeWithText("过期日期").performClick()
        // 选完先收面板再回传（见 HomeScreen），收起动画得先演完
        composeRule.waitForIdle()

        assertEquals(listOf(SortOrder.EXPIRY_DATE), changes)
    }

    /** 需要一个改得动列表或选中状态的容器，与其它用例只摆一个静态状态不同 */
    private fun setContentTracking(
        records: MutableState<List<HomeRecordItem>>,
        selectedIds: MutableState<Set<Long>>,
        newRecordIds: MutableState<Set<Long>> = mutableStateOf(emptySet()),
        justEmptied: MutableState<Boolean> = mutableStateOf(false),
        firstEmptyLoad: MutableState<Boolean> = mutableStateOf(false)
    ) {
        composeRule.setContent {
            FreshNowTheme() {
                HomeScreen(
                    records = records.value,
                    newRecordIds = newRecordIds.value,
                    justEmptied = justEmptied.value,
                    firstEmptyLoad = firstEmptyLoad.value,
                    selectedIds = selectedIds.value,
                    manualEntry = false,
                    sortOrder = SortOrder.CREATED_AT,
                    onSortOrderChange = {},
                    onRecordClick = {},
                    onRecordToggle = {},
                    onExitSelection = {},
                    onDeleteSelected = {},
                    refreshing = false,
                    canRefresh = true,
                    syncMessage = null,
                    onSync = {},
                    onSyncMessageShown = {},
                    onNavigateToScan = {},
                    onNavigateToManualEntry = {},
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

    /**
     * 下拉刷新要真的叫到同步。
     *
     * 手势从顶栏之下开始（顶栏那一带不吃这个手势），终点停在列表里：屏幕底部那一带是「添加」
     * 浮钮的地盘。两端都按根节点的边界算，不写死屏幕尺寸。
     */
    @Test
    fun pullDown_asksForSync() {
        var syncs = 0
        setContent(items = records(), onSync = { syncs++ })

        pullDown()

        assertEquals("下拉应当叫到一次同步", 1, syncs)
    }

    /**
     * 选择模式下不响应下拉。
     *
     * 那一刻用户在挑要删的，而这期间同步把某条删掉或改掉，会让「已选 N 项」与眼前的列表对不上。
     */
    @Test
    fun pullDown_isIgnoredInSelectionMode() {
        var syncs = 0
        setContent(items = records(), selectedIds = setOf(1L), onSync = { syncs++ })

        pullDown()

        assertEquals("选择模式下不该叫同步", 0, syncs)
    }

    /**
     * 没有配对过设备时，下拉连同步都不该叫。
     *
     * 那一刻没有对方可同步，唯一的结果是一句「还没有配对」——那句话该在设置里说，
     * 不该由一个手势换来。禁用做在 enabled 上，所以连指示器都不会出现。
     */
    @Test
    fun pullDown_isIgnoredWithoutPairedPeer() {
        var syncs = 0
        setContent(items = records(), canRefresh = false, onSync = { syncs++ })

        pullDown()

        assertEquals("没有配对设备时不该叫同步", 0, syncs)
    }

    private fun pullDown() {
        composeRule.onRoot().performTouchInput {
            swipeDown(
                startY = top + PULL_START_OFFSET.toPx(),
                endY = bottom - PULL_END_INSET.toPx()
            )
        }
        composeRule.waitForIdle()
    }

    private fun setContent(
        items: List<HomeRecordItem>?,
        newRecordIds: Set<Long> = emptySet(),
        justEmptied: Boolean = false,
        firstEmptyLoad: Boolean = false,
        selectedIds: Set<Long> = emptySet(),
        manualEntry: Boolean = false,
        sortOrder: SortOrder = SortOrder.CREATED_AT,
        onRecordClick: (Long) -> Unit = {},
        onRecordToggle: (Long) -> Unit = {},
        onExitSelection: () -> Unit = {},
        onDeleteSelected: () -> Unit = {},
        refreshing: Boolean = false,
        canRefresh: Boolean = true,
        syncMessage: SyncMessage? = null,
        onSync: () -> Unit = {},
        onSyncMessageShown: () -> Unit = {},
        onNavigateToScan: () -> Unit = {},
        onNavigateToManualEntry: () -> Unit = {},
        onSortOrderChange: (SortOrder) -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme() {
                // 断言描边颜色要用主题里的 primary，顺手在合成里记下来
                primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
                HomeScreen(
                    records = items,
                    newRecordIds = newRecordIds,
                    justEmptied = justEmptied,
                    firstEmptyLoad = firstEmptyLoad,
                    selectedIds = selectedIds,
                    manualEntry = manualEntry,
                    sortOrder = sortOrder,
                    onSortOrderChange = onSortOrderChange,
                    onRecordClick = onRecordClick,
                    onRecordToggle = onRecordToggle,
                    onExitSelection = onExitSelection,
                    onDeleteSelected = onDeleteSelected,
                    refreshing = refreshing,
                    canRefresh = canRefresh,
                    syncMessage = syncMessage,
                    onSync = onSync,
                    onSyncMessageShown = onSyncMessageShown,
                    onNavigateToScan = onNavigateToScan,
                    onNavigateToManualEntry = onNavigateToManualEntry,
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
        /** 「添加」的两个去处，只作为判据用，不是真的路由名 */
        const val SCAN = "scan"
        const val MANUAL_ENTRY = "manualEntry"

        const val PREVIEW_NAME = "home_list_preview.png"
        const val SELECTION_PREVIEW_NAME = "home_selection_preview.png"
        const val COUNT_ROLL_PREVIEW_NAME = "home_count_roll_preview.png"

        /** 选中计数标题里数字之前那一段文字，用来断言它没有跟着数字一起动 */
        const val SELECTED_COUNT_PREFIX = "已选"

        /** 页内状态切换时长的一半，停在动画中途用；与 FreshNowTransitions 的 short4（200ms）对应 */
        const val STATE_CHANGE_HALF_MS = 100L

        /** 页内状态切换的整段时长，用来把时钟推过"等一拍"那道延迟 */
        const val STATE_CHANGE_MS = STATE_CHANGE_HALF_MS * 2

        /** 新记录慢一帧进列表，取样前要越过这一帧；两帧是因为写完状态还要等下一帧重新合成 */
        const val DEFER_FRAME_MS = 32L

        /** 下拉手势的起点：越过顶栏（二级页与主页的顶栏都是 64dp 上下） */
        val PULL_START_OFFSET = 160.dp

        /** 终点离屏幕底边的距离：把「添加」浮钮那一带让出来 */
        val PULL_END_INSET = 120.dp
    }
}
