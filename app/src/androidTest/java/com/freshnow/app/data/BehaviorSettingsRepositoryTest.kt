package com.freshnow.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { dataStoreFile }
    private val repository = BehaviorSettingsRepository(dataStore)

    @After
    fun cleanUp() {
        scope.cancel()
        dataStoreFile.delete()
        File(dataStoreFile.absolutePath + ".tmp").delete()
    }

    /** 没写过任何东西时是「关 + 按创建时间」：手动录入是旁路，排序保持列表本来的顺序 */
    @Test
    fun default_isOffAndCreatedOrder() = runBlocking {
        assertEquals(
            BehaviorSettings(manualEntry = false, sortOrder = SortOrder.CREATED_AT),
            repository.behaviorSettings.first()
        )
    }

    @Test
    fun save_thenRead_returnsSavedValues() = runBlocking {
        val settings = BehaviorSettings(manualEntry = true, sortOrder = SortOrder.EXPIRY_DATE)

        repository.save(settings)

        assertEquals(settings, repository.behaviorSettings.first())
    }

    /** 认不出的排序值回落到默认：改过枚举名、手改过配置文件都不该让整份设置读不出来 */
    @Test
    fun unknownSortOrder_fallsBackToCreatedOrder() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("sort_order")] = "SOMETHING_ELSE" }

        assertEquals(SortOrder.CREATED_AT, repository.behaviorSettings.first().sortOrder)
    }
}
