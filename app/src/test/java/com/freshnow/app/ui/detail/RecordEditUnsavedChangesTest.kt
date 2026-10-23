package com.freshnow.app.ui.detail

import com.freshnow.app.ui.component.EditPhoto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这一页有没有东西会丢」的判据：返回时只有 true 才弹确认，没有改动就直接退。
 *
 * 与扫描页那条同一条规矩（有内容才问），但基准不同：扫描页比的是「空不空」，
 * 编辑页比的是「和进页时那条记录比，改没改」——把改动改回原样不该再拦人。
 */
class RecordEditUnsavedChangesTest {

    /** 还没读到库：此刻「有没有改动」无从谈起，不该拦住返回 */
    @Test
    fun notLoaded_yet_isNotDirty() {
        val state = RecordEditUiState(loaded = false, productName = NAME)

        assertFalse(state.isDirtyAgainst(null))
    }

    /** 记录已经不存在：没有可丢的东西 */
    @Test
    fun missingRecord_isNotDirty() {
        val state = RecordEditUiState(loaded = true, found = false)

        assertFalse(state.isDirtyAgainst(existingBaseline()))
    }

    @Test
    fun emptyNewDraft_isNotDirty() {
        val state = RecordEditUiState(loaded = true, found = true, isNew = true)

        assertFalse(state.isDirtyAgainst(null))
    }

    @Test
    fun newDraftWithOneField_isDirty() {
        val state = RecordEditUiState(loaded = true, found = true, isNew = true, productName = NAME)

        assertTrue(state.isDirtyAgainst(null))
    }

    /** 手动录入只加了照片：照片也是这次输入的一部分，保存会带上它 */
    @Test
    fun newDraftWithOnlyPhoto_isDirty() {
        val state = RecordEditUiState(
            loaded = true,
            found = true,
            isNew = true,
            photos = listOf(EditPhoto(id = 0, bytes = byteArrayOf(1)))
        )

        assertTrue(state.isDirtyAgainst(null))
    }

    /** 已有记录一个字没动：直接退，不该拿一句「尚未保存」拦人 */
    @Test
    fun untouchedExistingRecord_isNotDirty() {
        val state = existingDraft()

        assertFalse(state.isDirtyAgainst(existingBaseline()))
    }

    @Test
    fun changedField_isDirty() {
        val state = existingDraft(productName = "$NAME（改）")

        assertTrue(state.isDirtyAgainst(existingBaseline()))
    }

    @Test
    fun changedLocation_isDirty() {
        val state = existingDraft(location = "冷藏")

        assertTrue(state.isDirtyAgainst(existingBaseline()))
    }

    /**
     * 同一个日期的另一种写法：落库只有一种写法（见 ScanValueFormat），
     * 所以「改了一种写法又存回去」在库里什么都不会变，不该弹窗
     */
    @Test
    fun sameValueInAnotherWording_isNotDirty() {
        val state = existingDraft(expiryDate = "2026/12/1")

        assertFalse(state.isDirtyAgainst(existingBaseline()))
    }

    @Test
    fun addedPhoto_isDirty() {
        val state = existingDraft(
            photos = existingPhotos() + EditPhoto(id = 2, bytes = byteArrayOf(1))
        )

        assertTrue(state.isDirtyAgainst(existingBaseline()))
    }

    @Test
    fun removedPhoto_isDirty() {
        val state = existingDraft(photos = existingPhotos().take(1))

        assertTrue(state.isDirtyAgainst(existingBaseline()))
    }

    /** 顺序即展示顺序、第一位是代表图，重排也是一次改动 */
    @Test
    fun reorderedPhotos_isDirty() {
        val state = existingDraft(photos = existingPhotos().reversed())

        assertTrue(state.isDirtyAgainst(existingBaseline()))
    }

    private fun existingDraft(
        productName: String = NAME,
        location: String = "常温",
        expiryDate: String = "2026-12-01",
        photos: List<EditPhoto> = existingPhotos()
    ) = RecordEditUiState(
        loaded = true,
        found = true,
        productName = productName,
        location = location,
        productionDate = "2026-01-01",
        expiryDate = expiryDate,
        shelfLife = "11个月",
        photos = photos
    )

    private fun existingBaseline() = RecordBaseline(
        fields = StoredFields(
            productName = NAME,
            location = "常温",
            productionDate = "2026-01-01",
            // 库里存的就是规整后的写法（见 ScanValueFormat.shelfLife），基准也跟着它
            expiryDate = "2026-12-01",
            shelfLife = "11个月"
        ),
        imageNames = listOf("a.jpg", "b.jpg")
    )

    private fun existingPhotos() = listOf(
        EditPhoto(id = 0, name = "a.jpg"),
        EditPhoto(id = 1, name = "b.jpg")
    )

    private companion object {
        const val NAME = "纯牛奶"
    }
}
