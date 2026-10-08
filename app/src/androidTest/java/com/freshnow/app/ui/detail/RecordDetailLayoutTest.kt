package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.R
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 详情页的横屏版式：与扫描页同一条判据（见 ScanScreen），宽不小于高时照片在左、信息在右。
 *
 * 触发方式只给一个宽大于高的容器，不去改设备方向——判据看的是可用空间，与设备实际是不是横屏无关，
 * 这一点沿用 ScanScreen 的说明。因此这条用例在竖屏设备上同样跑得动。
 */
@RunWith(AndroidJUnit4::class)
class RecordDetailLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var recordId: Long = 0

    @After
    fun cleanUp() {
        if (recordId != 0L) {
            runBlocking {
                FreshNowDatabase.getInstance(context).scanRecordDao().deleteByIds(listOf(recordId))
            }
        }
    }

    @Test
    fun landscapeLayout_putsPhotoLeftOfFields() {
        recordId = runBlocking {
            FreshNowDatabase.getInstance(context).scanRecordDao().insert(
                ScanRecord(
                    productName = PRODUCT_NAME,
                    productionDate = "2026-10-01",
                    expiryDate = "2026-12-01",
                    shelfLife = "2个月",
                    // 没有照片时用占位块，它同样要落在左栏：版式与有没有照片无关
                    imageName = "",
                    savedAt = System.currentTimeMillis()
                )
            )
        }

        rule.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 220.dp)) {
                RecordDetailScreen(recordId = recordId, onBack = {}, onNavigateToEdit = {})
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(PRODUCT_NAME).fetchSemanticsNodes().isNotEmpty()
        }

        saveScreenshot("detail-landscape.png")

        val photo = rule.onNodeWithContentDescription(context.getString(R.string.record_image_missing))
            .fetchSemanticsNode().boundsInRoot
        val name = rule.onNodeWithText(PRODUCT_NAME).fetchSemanticsNode().boundsInRoot

        // 照片不能撑满整行：它一旦占满，字段就被挤到 0 宽，而上面那条「右边界 ≤ 左边界」照样成立。
        // 不写死具体宽度：详情页有顶栏，可用高度取决于顶栏高度，不在这条用例里重算那套尺寸
        val containerWidth = with(rule.density) { 360.dp.toPx() }
        assertTrue("照片不应撑满整行（实际 ${photo.width}px）", photo.width < containerWidth * 0.9f)
        assertTrue("字段不应被挤到最右侧之外", name.left < containerWidth)

        assertTrue(
            "照片（右边界 ${photo.right}）应当整个落在信息（左边界 ${name.left}）左侧",
            photo.right <= name.left
        )
    }

    /** 截图只为人工看一眼版式，取不到不影响断言 */
    private fun saveScreenshot(name: String) {
        runCatching {
            val shot = rule.onRoot().captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir(null), name).outputStream().use {
                shot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private companion object {
        const val PRODUCT_NAME = "全脂纯牛奶"
    }
}
