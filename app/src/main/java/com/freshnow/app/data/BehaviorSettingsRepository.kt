package com.freshnow.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        BehaviorSettings(manualEntry = preferences[KEY_MANUAL_ENTRY] ?: false)
    }

    suspend fun save(settings: BehaviorSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_MANUAL_ENTRY] = settings.manualEntry
        }
    }

    private companion object {
        val KEY_MANUAL_ENTRY = booleanPreferencesKey("manual_entry")
    }
}
