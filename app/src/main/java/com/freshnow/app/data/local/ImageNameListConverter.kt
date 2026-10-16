package com.freshnow.app.data.local

import androidx.room.TypeConverter

/**
 * 一条记录的照片名列表与库里那一列文本之间的转换。
 *
 * 分隔符取换行：照片名是「三十二个十六进制字符 + .jpg」（见 ScanImageStore 的内容散列），
 * 不含换行，所以分隔符与内容不会撞。
 *
 * 不另开一张照片表：同步的冲突判据（LWW）是「一行一个版本」，把照片拆成子表就等于把一行拆成
 * 多行多版本，墓碑、合并、破平全要重想。名字列表装在一列里，那些机制一个字都不用改。
 */
internal class ImageNameListConverter {

    @TypeConverter
    fun fromList(names: List<String>): String = names.joinToString(SEPARATOR)

    @TypeConverter
    fun toList(raw: String): List<String> = raw.split(SEPARATOR).filter { it.isNotEmpty() }

    private companion object {
        const val SEPARATOR = "\n"
    }
}
