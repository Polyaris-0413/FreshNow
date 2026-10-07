package com.freshnow.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 图标「编译通过但不显示」是这个项目踩过的坑：Material Symbols 的 SVG 视口从 -960 起，
 * 直接照抄进 Android vector 会整体画在视口外，编译器一句提示都没有（见 AGENTS.md）。
 *
 * 这类错误只有真的画一次才看得见，所以这里不比对路径、只断言结果：每个图标画进位图后，
 * 视口内必须至少有一个被点亮的像素。
 */
@RunWith(AndroidJUnit4::class)
class IconVectorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyIconDrawsInsideItsViewport() {
        iconNames().forEach { name ->
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            assertTrue("$name 没有对应的 drawable 资源", id != 0)

            val drawable = requireNotNull(ContextCompat.getDrawable(context, id)) { name }
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, SIZE, SIZE)
            drawable.draw(Canvas(bitmap))

            assertTrue("$name 整个画在视口外，界面上看不见", bitmap.hasVisiblePixel())
        }
    }

    private fun iconNames(): List<String> = R.drawable::class.java.fields
        .map { it.name }
        .filter { it.startsWith(DRAWABLE_PREFIX) }
        .sorted()

    private fun Bitmap.hasVisiblePixel(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { it != 0 }
    }

    private companion object {
        const val DRAWABLE_PREFIX = "ic_"
        const val SIZE = 96
    }
}
