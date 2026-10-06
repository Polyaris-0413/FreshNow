package com.freshnow.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSettingsRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = AiSettingsRepository(context)

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
