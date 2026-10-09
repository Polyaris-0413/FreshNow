package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 编辑表单：四项都在、改动能传出去、写错了怎么报、过期日期的来源交代清楚。
 *
 * 只摆表单本体（不含读库与落盘）：走真的 RecordEditScreen 要连数据库一起测进来，
 * 而这里要盯的是三件事——「留空即跟随推算」「写错了在失焦之后才报」「打开就写错的旧值当场报」。
 */
@RunWith(AndroidJUnit4::class)
class RecordEditFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 四项一项都不能少：编辑页的意义就是把这四项都改一遍 */
    @Test
    fun showsEveryEditableField() {
        setContent()

        listOf("名称", "生产日期", "保质期", "过期日期").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
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
        setContent(uiState = EMPTY.copy(image = bitmap))

        composeRule.onNodeWithContentDescription("无图片").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("扫描照片").assertIsDisplayed()
    }

    /**
     * 点是照片本身而不是旁边的控件：文本框点哪里都能改，照片也一样。
     * 两条各一次：有图时点的是照片，没图时点的是占位图。
     */
    @Test
    fun tappingThePhotoAsksToChangeIt() {
        var clicked = 0
        setContent(onChangePhoto = { clicked++ })

        composeRule.onNodeWithContentDescription("无图片").performClick()

        assertEquals(1, clicked)
    }

    @Test
    fun tappingThePlaceholderAsksToAddAPhoto() {
        var clicked = 0
        setContent(
            uiState = EMPTY.copy(image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)),
            onChangePhoto = { clicked++ }
        )

        composeRule.onNodeWithContentDescription("扫描照片").performClick()

        assertEquals(1, clicked)
    }

    /** 输入要能传到调用方，否则字段只是个摆设 */
    @Test
    fun typingReportsNewValue() {
        val changed = mutableListOf<String>()
        setContent(uiState = EMPTY, onProductNameChange = { changed += it })

        composeRule.onNodeWithText("名称").performTextInput("牛奶")

        assertEquals(listOf("牛奶"), changed)
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
            FreshNowTheme(dynamicColor = false) {
                RecordEditForm(
                    uiState = uiState,
                    onProductNameChange = {},
                    // 上层是 ViewModel 的 revalidate，这里直接摆它算出来的结果
                    onProductionDateChange = { value ->
                        uiState = uiState.copy(
                            productionDate = value,
                            productionDateInvalid = value.isNotBlank()
                        )
                    },
                    onExpiryDateChange = {},
                    onShelfLifeChange = {},
                    onChangePhoto = {}
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
        onChangePhoto: () -> Unit = {}
    ) {
        composeRule.setContent {
            FreshNowTheme(dynamicColor = false) {
                RecordEditForm(
                    uiState = uiState,
                    onProductNameChange = onProductNameChange,
                    onProductionDateChange = {},
                    onExpiryDateChange = {},
                    onShelfLifeChange = {},
                    onChangePhoto = onChangePhoto
                )
            }
        }
    }

    private companion object {
        /** 三个字段写错时共用的那一句，见 strings.xml */
        const val UNRECOGNIZED = "认不出这个写法"
        val EMPTY = RecordEditUiState(loaded = true, found = true)
    }
}
