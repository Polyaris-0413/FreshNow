package com.freshnow.app.ui.detail

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ScanImageStore
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.toSquareJpegBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 草稿里「用作首图」的那一张，保存之后仍在第一位。
 *
 * 这是整条链路的接缝：草稿是一个列表（挪一下就行），而落盘时新加的照片默认接在旧照片后面
 * （见 ImageChange.Update.leadingAdded）。少了这一项，界面上看着改了，重进记录又变回去——
 * 用户会以为这个动作根本没生效。
 *
 * 走真实的库与照片目录，但只碰本用例自己插进去的那条（按名字回收）。
 */
@RunWith(AndroidJUnit4::class)
class RecordEditPhotoOrderTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @After
    fun cleanUp() = runBlocking {
        val dao = FreshNowDatabase.getInstance(application).scanRecordDao()
        val ids = dao.observeAll().first().filter { it.productName == PRODUCT_NAME }.map { it.id }
        if (ids.isNotEmpty()) dao.hardDelete(ids)
    }

    /** 两张都是这次新加的：设首图后，落库的名字顺序要跟着倒过来 */
    @Test
    fun firstPhotoChoiceSurvivesSaving() {
        val viewModel = RecordEditViewModel(application)
        viewModel.startNew()
        awaitLoaded(viewModel)
        viewModel.onProductNameChange(PRODUCT_NAME)

        viewModel.onPhotoCaptured(jpegOf(RED))
        awaitPhotos(viewModel, 1)
        viewModel.onPhotoCaptured(jpegOf(BLUE))
        awaitPhotos(viewModel, 2)

        viewModel.onPhotoMovedToFront(1)

        assertArrayEquals(
            "第二张要挪到草稿第一位",
            jpegOf(BLUE),
            viewModel.uiState.value.photos[0].bytes
        )

        var imageSaved: Boolean? = null
        viewModel.save { imageSaved = it }
        awaitSaved { imageSaved }
        assertEquals("两张照片都该写成功", true, imageSaved)

        val saved = runBlocking {
            FreshNowDatabase.getInstance(application).scanRecordDao().observeAll().first()
                .single { it.productName == PRODUCT_NAME }
        }
        // 名字按内容算：同一张字节再写一次就是那个名字，于是这里能直接比顺序
        val store = ScanImageStore(application)
        val expected = runBlocking { listOf(store.write(jpegOf(BLUE)), store.write(jpegOf(RED))) }
        assertEquals(
            "存下来的第一个名字必须是用户选的那张",
            expected,
            saved.imageNames
        )
    }

    private fun jpegOf(color: Int): ByteArray = requireNotNull(
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(color) }
            .toSquareJpegBytes()
    )

    private fun awaitLoaded(viewModel: RecordEditViewModel) = runBlocking {
        withTimeout(5_000) {
            while (!viewModel.uiState.value.loaded) delay(10)
        }
    }

    private fun awaitPhotos(viewModel: RecordEditViewModel, count: Int) = runBlocking {
        withTimeout(5_000) {
            while (viewModel.uiState.value.photos.size < count) delay(10)
        }
    }

    private fun awaitSaved(imageSaved: () -> Boolean?) = runBlocking {
        withTimeout(5_000) {
            while (imageSaved() == null) delay(10)
        }
    }

    private companion object {
        const val PRODUCT_NAME = "首图用例-酸奶"
        const val RED = Color.RED
        const val BLUE = Color.BLUE
    }
}
