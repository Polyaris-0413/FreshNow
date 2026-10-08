package com.freshnow.app.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 编辑表单的横屏版式：与扫描页、详情页同一条判据，宽不小于高时照片在左、字段在右。
 *
 * 容器取 360×220dp。判据看的是可用空间而非设备方向，所以不必真把设备转横屏；
 * 而这个尺寸不超出手机的逻辑宽度（360dp 是最常见的一档），否则 Box 会被屏幕压扁，
 * 320dp 的照片把右栏挤到几十 dp——那是容器本身不成立，不是版式的问题。
 */
@RunWith(AndroidJUnit4::class)
class RecordEditFormLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun landscapeLayout_putsPhotoLeftOfFields() {
        setContentWith(image = null)
        saveScreenshot("edit-landscape.png")

        val photo = composeRule
            .onNodeWithContentDescription(context.getString(R.string.record_image_missing))
            .fetchSemanticsNode().boundsInRoot
        val field = composeRule
            .onNodeWithText(context.getString(R.string.scan_product_name))
            .fetchSemanticsNode().boundsInRoot

        // 宽度必须由可用高度决定：若照片撑满整行，字段会被挤到 0 宽，而上面那条「右边界 ≤ 左边界」照样成立
        val expectedWidth = with(composeRule.density) { 220.dp.toPx() }
        assertEquals("照片宽度应当等于可用高度", expectedWidth, photo.width, 2f)

        assertTrue(
            "照片（右边界 ${photo.right}）应当整个落在字段（左边界 ${field.left}）左侧",
            photo.right <= field.left
        )
    }

    /** 有照片时它是正方形：宽度由版式给，高度由照片自己的比例决定（照片落盘时就是方的） */
    @Test
    fun landscapeLayout_photoIsSquare() {
        setContentWith(image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

        val photo = composeRule
            .onNodeWithContentDescription(context.getString(R.string.record_image))
            .fetchSemanticsNode().boundsInRoot

        assertEquals("照片应当是正方形", photo.width.toFloat(), photo.height.toFloat(), 1f)
    }

    private fun setContentWith(image: Bitmap?) {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 220.dp)) {
                RecordEditForm(
                    uiState = RecordEditUiState(loaded = true, found = true, image = image),
                    onProductNameChange = {},
                    onProductionDateChange = {},
                    onExpiryDateChange = {},
                    onShelfLifeChange = {},
                    onChangePhoto = {}
                )
            }
        }
    }

    /** 截图只为人工看一眼版式，取不到不影响断言 */
    private fun saveScreenshot(name: String) {
        runCatching {
            val shot = composeRule.onRoot().captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir(null), name).outputStream().use {
                shot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
