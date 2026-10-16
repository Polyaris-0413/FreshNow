package com.freshnow.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScanImageStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // 用临时目录，不往设备的图片目录里写测试文件
    private val directory = File(context.cacheDir, "test_scan_images")
    private val store = ScanImageStore(directory)

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun write_thenFind_returnsSameBytes() {
        runBlocking {
            val frame = byteArrayOf(0x01, 0x02, 0x03, 0x04)

            val name = store.write(frame)
            assertNotNull(name)

            val file = store.find(name.orEmpty())
            assertNotNull(file)
            assertArrayEquals(frame, file?.readBytes())
        }
    }

    @Test
    fun find_blankOrUnknownName_returnsNull() {
        assertNull(store.find(""))
        assertNull(store.find("not-exist.jpg"))
    }

    /**
     * 名字会被拼进路径。对端与旧数据都可能给出怪名字，而 `../` 拼出来的路径已经跑到照片目录
     * 之外——那是「取一张不存在的图」变成「读别人的文件」的分界线。合法的名字只有内容散列那一种形状。
     */
    @Test
    fun find_rejectsNamesThatAreNotContentHashes() {
        listOf("../secret.jpg", "a/b.jpg", "..\\secret.jpg", "abc.jpg", "note.txt").forEach { name ->
            assertNull("这样的名字不能被当成照片：$name", store.find(name))
        }
    }

    /** 形状合法但文件真的不在：照样是 null，不是抛异常 */
    @Test
    fun find_wellFormedNameWithoutFile_returnsNull() {
        assertNull(store.find("0123456789abcdef0123456789abcdef.jpg"))
    }

    @Test
    fun delete_removesFileFromDisk() {
        runBlocking {
            val name = store.write(byteArrayOf(0x01, 0x02, 0x03, 0x04)).orEmpty()
            assertNotNull(store.find(name))

            store.delete(name)

            assertNull(store.find(name))
        }
    }

    /** 文件已经不在、名字为空时删除不算失败：目标状态已经达成，没有可补救的东西 */
    @Test
    fun delete_unknownOrBlankName_doesNotThrow() {
        runBlocking {
            store.delete("not-exist.jpg")
            store.delete("")
        }
    }
}
