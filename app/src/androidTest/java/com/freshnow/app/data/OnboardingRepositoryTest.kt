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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OnboardingRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStoreFile = File(context.cacheDir, "test_onboarding.preferences_pb")

    // 用临时文件建 DataStore：拿设备上真实的那份做测试，跑一次就把「还没看过引导」写掉了，
    // 下次手动打开应用会直接进主页（见 AiSettingsRepositoryTest 里同样的取舍）
    private val repository = OnboardingRepository(
        PreferenceDataStoreFactory.create(scope = scope) { dataStoreFile }
    )

    @After
    fun cleanUp() {
        scope.cancel()
        dataStoreFile.delete()
        File(dataStoreFile.absolutePath + ".tmp").delete()
    }

    /** 装完还没走过引导：默认必须是「没看过」，否则欢迎页永远不会出现 */
    @Test
    fun freshInstall_hasNotSeenWelcome() = runBlocking {
        assertFalse(repository.welcomeSeen.first())
    }

    @Test
    fun markWelcomeSeen_thenRead_returnsTrue() = runBlocking {
        repository.markWelcomeSeen()

        assertTrue(repository.welcomeSeen.first())
    }
}
