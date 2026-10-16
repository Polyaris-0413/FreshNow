package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 编辑表单：五项都在、改动能传出去、写错了怎么报、过期日期的来源交代清楚。
 *
 * 位置也在这张表单里（补充信息页也有一份，两处改的是同一条记录）：详情页只有「编辑」一个入口，
 * 位置若只归补充信息页，从那儿就再也改不了。
 *
 * 只摆表单本体（不含读库与落盘）：走真的 RecordEditScreen 要连数据库一起测进来，
 * 而这里要盯的是三件事——「留空即跟随推算」「写错了在失焦之后才报」「打开就写错的旧值当场报」。
 */
@RunWith(AndroidJUnit4::class)
class RecordEditFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 五项一项都不能少：编辑页的意义就是把这五项都改一遍 */
    @Test
    fun showsEveryEditableField() {
        setContent()

        listOf("名称", "位置", "生产日期", "保质期", "过期日期").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    /**
     * 「补充信息」开关关着时位置那一栏不出现：那时没人能给这一项赋值（扫描那一步也不会去补充页），
     * 还把它摆出来给人看，就是一栏填不了的东西
     */
    @Test
    fun locationIsHiddenWhenSupplementIsOff() {
        setContent(uiState = EMPTY.copy(supplementEnabled = false))

        composeRule.onNodeWithText("位置").assertDoesNotExist()
        composeRule.onNodeWithText("名称").assertIsDisplayed()
    }

    /** 位置排在名称下面，与详情页一致：名称说这是什么、位置说它在哪，两句是一件事 */
    @Test
    fun locationSitsRightBelowTheName() {
        setContent()

        val nameTop = composeRule.onNodeWithText("名称").getBoundsInRoot().top
        val locationTop = composeRule.onNodeWithText("位置").getBoundsInRoot().top
        val productionTop = composeRule.onNodeWithText("生产日期").getBoundsInRoot().top

        assertTrue("位置应当在名称之下：name=$nameTop location=$locationTop", locationTop > nameTop)
        assertTrue("位置应当在生产日期之上：location=$locationTop production=$productionTop", locationTop < productionTop)
    }

    /**
     * 照片与四个字段同屏。它也是这条记录的一个字段，藏在别的页面会让人以为照片改不了；
     * 没有照片时那块占位图同样要在，否则换个照片就没处可点
     */
    @Test
    fun showsPhotoField() {
        setContent()

        composeRule.onNodeWithContentDescription("无图片").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("扫描照片").assertDoesNotExist()
    }

    /** 有照片时画的是照片而不是占位图 */
    @Test
    fun showsPhotoWhenPresent() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        setContent(uiState = EMPTY.copy(photos = listOf(EditPhoto(id = 1, bitmap = bitmap))))

        composeRule.onNodeWithContentDescription("无图片").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("扫描照片").assertIsDisplayed()
    }

    /** 草稿里几张，格子就几格 */
    @Test
    fun gridShowsEveryDraftPhoto() {
        setContent(uiState = EMPTY.copy(photos = listOf(photo(1), photo(2), photo(3))))

        composeRule.onAllNodesWithContentDescription("移除照片").assertCountEquals(3)
    }

    /** 一张都没有时照片区仍有那格「+」：新记录就是这样开始加图的 */
    @Test
    fun emptyPhotoAreaStillOffersAdd() {
        setContent(uiState = EMPTY.copy(photos = emptyList()))

        composeRule.onNodeWithContentDescription("添加照片").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("移除照片").assertCountEquals(0)
    }

    /** 点某一格的 ✕，报的是那一格的下标 */
    @Test
    fun removingAPhotoReportsItsIndex() {
        var removed = -1
        setContent(
            uiState = EMPTY.copy(photos = listOf(photo(1), photo(2), photo(3))),
            onRemovePhoto = { removed = it }
        )

        composeRule.onAllNodesWithContentDescription("移除照片")[1].performClick()

        assertEquals("第二格的 ✕ 要报回下标 1", 1, removed)
    }

    /**
     * 两条路各一个入口：点「+」是加图，点某一格是看它。两个动作分开之后，
     * 「点照片」不再有歧义——它只可能是「我要看这张」。
     */
    @Test
    fun tappingTheAddTileAsksToAddAPhoto() {
        var added = 0
        setContent(onAddPhoto = { added++ })

        composeRule.onNodeWithContentDescription("添加照片").performClick()

        assertEquals(1, added)
    }

    @Test
    fun tappingAPhotoOpensThatOne() {
        var opened = -1
        setContent(
            uiState = EMPTY.copy(photos = listOf(photo(1), photo(2))),
            onOpenPhoto = { opened = it }
        )

        composeRule.onAllNodesWithContentDescription("扫描照片")[1].performClick()

        assertEquals("点第二格报的要是 1", 1, opened)
    }

    /** 输入要能传到调用方，否则字段只是个摆设 */
    @Test
    fun typingReportsNewValue() {
        val changed = mutableListOf<String>()
        setContent(uiState = EMPTY, onProductNameChange = { changed += it })

        composeRule.onNodeWithText("名称").performTextInput("牛奶")

        assertEquals(listOf("牛奶"), changed)
    }

    /** 位置同样能改：详情页只有「编辑」一个入口，靠这里改 */
    @Test
    fun typingLocationReportsNewValue() {
        val changed = mutableListOf<String>()
        setContent(uiState = EMPTY, onLocationChange = { changed += it })

        composeRule.onNodeWithText("位置").performTextInput("冰箱冷藏室")

        assertEquals(listOf("冰箱冷藏室"), changed)
    }

    /** 过期日期留空时，推算结果显示在同一屏里——这正是编辑页存在的理由 */
    @Test
    fun blankExpiryShowsDerivedDate() {
        setContent(uiState = EMPTY.copy(derivedExpiry = ExpiryOutcome.Resolved("2027-04-01")))

        composeRule.onNodeWithText("2027-04-01", substring = true).assertIsDisplayed()
    }

    /** 填了值就是标签印刷值，以它为准，推算提示收起来免得两种说法并存 */
    @Test
    fun filledExpiryHidesDerivedHint() {
        setContent(
            uiState = EMPTY.copy(
                expiryDate = "2027-05-01",
                derivedExpiry = ExpiryOutcome.Resolved("2027-04-01")
            )
        )

        composeRule.onNodeWithText("留空则按生产日期与保质期推算", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("2027-04-01", substring = true).assertDoesNotExist()
    }

    /** 推不出就如实说推不出，不再替上游字段解释原因（哪个字段写错了由那个字段自己报） */
    @Test
    fun blankExpirySaysItCannotDeriveYet() {
        setContent(uiState = EMPTY.copy(derivedExpiry = ExpiryOutcome.UnparseableShelfLife))

        composeRule.onNodeWithText("当前还推不出", substring = true).assertIsDisplayed()
    }

    /**
     * 写错了不跟着敲键闪红：日期是一字符一字符敲的，中途总是认不出，所以只在字段失焦之后才报
     * （规范：show error text only after user interaction with a field）
     */
    @Test
    fun invalidValueReportsOnlyAfterLeavingTheField() {
        composeRule.setContent {
            var uiState by mutableStateOf(EMPTY)
            FreshNowTheme() {
                RecordEditForm(
                    uiState = uiState,
                    onProductNameChange = {},
                    onLocationChange = {},
                    // 上层是 ViewModel 的 revalidate，这里直接摆它算出来的结果
                    onProductionDateChange = { value ->
                        uiState = uiState.copy(
                            productionDate = value,
                            productionDateInvalid = value.isNotBlank()
                        )
                    },
                    onExpiryDateChange = {},
                    onShelfLifeChange = {},
                    onOpenPhoto = {},
                    onRemovePhoto = {},
                    onAddPhoto = {}
                )
            }
        }

        composeRule.onNodeWithText("生产日期").performClick()
        composeRule.onNodeWithText("生产日期").performTextInput("13月")

        // 已经认不出了，但人还没离开这个字段 → 不报
        composeRule.onNodeWithText(UNRECOGNIZED, substring = true).assertDoesNotExist()

        // 换到下一个字段即失焦 → 这时才报
        composeRule.onNodeWithText("保质期").performClick()
        composeRule.onNodeWithText(UNRECOGNIZED, substring = true).assertIsDisplayed()
    }

    /** 打开页面时就已经写错的旧值例外：它不是用户刚敲的，而且不报的话保存为什么灰着没法解释 */
    @Test
    fun prefilledInvalidValueReportsImmediately() {
        setContent(uiState = EMPTY.copy(productionDate = "13月", productionDateInvalid = true))

        composeRule.onNodeWithText(UNRECOGNIZED, substring = true).assertIsDisplayed()
    }

    /**
     * 保质期写错也报同样一句（三个字段共用同一条文案，见 strings.xml）。
     *
     * 这里验的是「保质期这个字段自己会报」，而不是文案长什么样：文案只有一份，全应用一处可改。
     */
    @Test
    fun unrecognizableShelfLifeReportsToo() {
        setContent(uiState = EMPTY.copy(shelfLife = "很久", shelfLifeInvalid = true))

        composeRule.onNodeWithText(UNRECOGNIZED, substring = true).assertIsDisplayed()
    }

    private fun setContent(
        uiState: RecordEditUiState = EMPTY,
        onProductNameChange: (String) -> Unit = {},
        onLocationChange: (String) -> Unit = {},
        onOpenPhoto: (Int) -> Unit = {},
        onRemovePhoto: (Int) -> Unit = {},
        onAddPhoto: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme() {
                RecordEditForm(
                    uiState = uiState,
                    onProductNameChange = onProductNameChange,
                    onLocationChange = onLocationChange,
                    onProductionDateChange = {},
                    onExpiryDateChange = {},
                    onShelfLifeChange = {},
                    onOpenPhoto = onOpenPhoto,
                    onRemovePhoto = onRemovePhoto,
                    onAddPhoto = onAddPhoto
                )
            }
        }
    }

    /** 草稿里的一张照片。这里只看格子在不在，所以名字与字节都不给 */
    private fun photo(id: Long) = EditPhoto(id = id)

    private companion object {
        /** 三个字段写错时共用的那一句，见 strings.xml */
        const val UNRECOGNIZED = "认不出这个写法"
        val EMPTY = RecordEditUiState(loaded = true, found = true, supplementEnabled = true)
    }
}
