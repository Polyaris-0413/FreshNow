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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AiSettingsRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStoreFile = File(context.cacheDir, "test_ai_settings.preferences_pb")

    // 用临时文件建 DataStore：拿设备上真实的配置做测试，跑一次就会把用户的 AI 配置覆盖掉
    private val repository = AiSettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { dataStoreFile }
    )

    @After
    fun cleanUp() {
        scope.cancel()
        dataStoreFile.delete()
        File(dataStoreFile.absolutePath + ".tmp").delete()
    }

    @Test
    fun save_thenRead_returnsSavedValues() = runBlocking {
        val settings = AiSettings(
            baseUrl = "https://api.example.com/v1",
            modelName = "demo-model",
            apiKey = "sk-test-key"
        )

        repository.save(settings)

        assertEquals(settings, repository.aiSettings.first())
    }
}
