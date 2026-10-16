package com.freshnow.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 名字列表与库里那一列文本之间的互相转换。
 *
 * 分隔符取换行：照片名是「三十二个十六进制字符 + .jpg」（见 ScanImageStore 的内容散列），
 * 不含换行，所以分隔符与内容不会撞。
 */
class ImageNameListConverterTest {

    private val converter = ImageNameListConverter()

    @Test
    fun roundTrip_keepsTheSingleName() {
        assertEquals(listOf("a.jpg"), converter.toList(converter.fromList(listOf("a.jpg"))))
    }

    @Test
    fun noImages_isStoredAsEmptyTextAndReadBackAsEmptyList() {
        assertEquals("", converter.fromList(emptyList()))
        assertEquals(emptyList<String>(), converter.toList(""))
    }

    @Test
    fun order_isKept() {
        val names = listOf("a.jpg", "b.jpg", "c.jpg")

        assertEquals(names, converter.toList(converter.fromList(names)))
    }
}
