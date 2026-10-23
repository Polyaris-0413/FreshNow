package com.freshnow.app.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshnow.app.data.ExpiryOutcome
import com.freshnow.app.ui.component.FreshNowResultFields
import com.freshnow.app.ui.theme.FreshNowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 位置在结果里怎么显示。三件事各是一条：
 *
 * 一是它得跟「名称」挨在一起、排在名称之下——名称说这是什么、位置说它在哪，是一件事；
 * 而下面三块是一条推算链，位置插进去会把它打断。
 *
 * 二是它空着时写「未知」，与其余字段同一套空值规则。
 *
 * 三是扫描页根本不出现这一行（那里传 null）。只摆结果这一块，不走整页：整页要连数据库一起测进来。
 */
@RunWith(AndroidJUnit4::class)
class RecordDetailLocationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheLocationValue() {
        setContent(location = LOCATION)

        composeRule.onNodeWithText(LOCATION).assertIsDisplayed()
    }

    @Test
    fun blankLocationReadsUnknown() {
        setContent(location = "")

        composeRule.onNodeWithText("未知").assertIsDisplayed()
    }

    @Test
    fun locationSitsBelowTheNameAndAboveTheDates() {
        setContent(location = LOCATION)

        val nameTop = top("名称")
        val locationTop = top("位置")
        val productionTop = top("生产日期")

        assertTrue("位置应当在名称之下：name=$nameTop location=$locationTop", locationTop > nameTop)
        assertTrue(
            "位置应当在日期组之上：location=$locationTop production=$productionTop",
            locationTop < productionTop
        )
    }

    /** 扫描页那一处传 null：位置不是模型读到的，扫描结果里不该有它 */
    @Test
    fun withoutLocationTheRowIsAbsent() {
        setContent(location = null)

        composeRule.onNodeWithText("位置").assertDoesNotExist()
        composeRule.onNodeWithText("未知").assertDoesNotExist()
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).getBoundsInRoot().top

    private fun setContent(location: String?) {
        composeRule.setContent {
            FreshNowTheme() {
                FreshNowResultFields(
                    productName = "纯牛奶",
                    productionDate = "2026-01-01",
                    expiry = ExpiryOutcome.Resolved("2026-12-31"),
                    shelfLife = "12个月",
                    location = location
                )
            }
        }
    }

    private companion object {
        const val LOCATION = "冰箱冷藏室"
    }
}
