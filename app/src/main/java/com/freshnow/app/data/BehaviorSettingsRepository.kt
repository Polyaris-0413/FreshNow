package com.freshnow.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.behaviorSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "behavior_settings")

/**
 * [dataStore] 由外部传入，测试可以换成临时文件；直接拿设备上的真实设置做测试会把用户的开关改掉。
 */
class BehaviorSettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.behaviorSettingsDataStore)

    val behaviorSettings: Flow<BehaviorSettings> = dataStore.data.map { preferences ->
        BehaviorSettings(
            manualEntry = preferences[KEY_MANUAL_ENTRY] ?: false,
            sortOrder = preferences[KEY_SORT_ORDER].toSortOrder()
        )
    }

    suspend fun save(settings: BehaviorSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_MANUAL_ENTRY] = settings.manualEntry
            preferences[KEY_SORT_ORDER] = settings.sortOrder.name
        }
    }

    /**
     * 存的是枚举名。认不出的值（改过名、手改过配置文件）回落到默认，而不是让整份设置读不出来——
     * 排序方式是偏好的小事，读不出来按默认排序就好。
     */
    private fun String?.toSortOrder(): SortOrder =
        SortOrder.entries.firstOrNull { it.name == this } ?: SortOrder.CREATED_AT

    private companion object {
        val KEY_MANUAL_ENTRY = booleanPreferencesKey("manual_entry")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
    }
}
