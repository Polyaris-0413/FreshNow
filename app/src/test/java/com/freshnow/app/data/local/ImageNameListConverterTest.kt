package com.freshnow.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 名字列表与库里那一列文本之间的互相转换。
 * 分隔符取换行：照片名是「三十二个十六进制字符 + .jpg」（见 ScanImageStore），不含换行。
 * 不另开照片表：LWW 是「一行一个版本」，拆成子表就等于把一行拆成多行多版本，墓碑、合并、破平全要重想。
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
