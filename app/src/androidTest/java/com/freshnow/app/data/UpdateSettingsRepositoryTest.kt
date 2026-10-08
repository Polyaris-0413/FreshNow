package com.freshnow.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 「本次更新不再提醒」记的是哪一个版本。
 *
 * 用临时文件建 DataStore：拿设备上真实的设置做测试会把用户已忽略的版本覆盖掉
 * （与 AiSettingsRepositoryTest 同一个理由）。
 */
@RunWith(AndroidJUnit4::class)
class UpdateSettingsRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStoreFile = File(context.cacheDir, "test_update_settings.preferences_pb")

    private val repository = UpdateSettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { dataStoreFile }
    )

    @After
    fun cleanUp() {
        scope.cancel()
        dataStoreFile.delete()
        File(dataStoreFile.absolutePath + ".tmp").delete()
    }

    /** 没点过「关闭」时是空的：空值等于「哪个版本都没被忽略」，启动检查照常提示 */
    @Test
    fun noIgnoredVersionByDefault() = runBlocking {
        assertNull(repository.ignoredVersion.first())
    }

    @Test
    fun ignoredVersionIsRemembered() = runBlocking {
        repository.setIgnoredVersion("v1.0.1")

        assertEquals("v1.0.1", repository.ignoredVersion.first())
    }

    /** 记的是整串版本号而不是「不再提示」这个开关：下次发了新版本就换成新版本号，旧的自然不再忽略 */
    @Test
    fun laterDismissalReplacesTheEarlierOne() = runBlocking {
        repository.setIgnoredVersion("v1.0.1")
        repository.setIgnoredVersion("v1.0.2")

        assertEquals("v1.0.2", repository.ignoredVersion.first())
    }
}
