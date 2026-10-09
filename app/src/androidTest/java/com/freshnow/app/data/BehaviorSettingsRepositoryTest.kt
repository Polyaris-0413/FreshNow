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
class BehaviorSettingsRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStoreFile = File(context.cacheDir, "test_behavior_settings.preferences_pb")

    // 用临时文件建 DataStore：拿设备上真实的设置做测试，跑一次就会把用户的开关改掉
    private val repository = BehaviorSettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { dataStoreFile }
    )

    @After
    fun cleanUp() {
        scope.cancel()
        dataStoreFile.delete()
        File(dataStoreFile.absolutePath + ".tmp").delete()
    }

    /** 没写过任何东西时是「关」：手动录入是旁路，默认不该把扫描这条主线顶掉 */
    @Test
    fun default_isOff() = runBlocking {
        assertEquals(BehaviorSettings(manualEntry = false), repository.behaviorSettings.first())
    }

    @Test
    fun save_thenRead_returnsSavedValues() = runBlocking {
        val settings = BehaviorSettings(manualEntry = true)

        repository.save(settings)

        assertEquals(settings, repository.behaviorSettings.first())
    }
}
