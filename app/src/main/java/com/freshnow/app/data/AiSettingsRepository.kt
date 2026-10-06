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

private val Context.aiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/**
 * [dataStore] 由外部传入，测试可以换成临时文件；直接拿设备上的真实配置做测试会把用户配置覆盖掉
 */
class AiSettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.aiSettingsDataStore)

    val aiSettings: Flow<AiSettings> = dataStore.data.map { preferences ->
        AiSettings(
            baseUrl = preferences[KEY_BASE_URL].orEmpty(),
            modelName = preferences[KEY_MODEL_NAME].orEmpty(),
            apiKey = preferences[KEY_API_KEY].orEmpty(),
            showReasoning = preferences[KEY_SHOW_REASONING] ?: false,
            extraRequestJson = preferences[KEY_EXTRA_REQUEST_JSON].orEmpty()
        )
    }

    suspend fun save(settings: AiSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_BASE_URL] = settings.baseUrl
            preferences[KEY_MODEL_NAME] = settings.modelName
            preferences[KEY_API_KEY] = settings.apiKey
            preferences[KEY_SHOW_REASONING] = settings.showReasoning
            preferences[KEY_EXTRA_REQUEST_JSON] = settings.extraRequestJson
        }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_SHOW_REASONING = booleanPreferencesKey("show_reasoning")
        val KEY_EXTRA_REQUEST_JSON = stringPreferencesKey("extra_request_json")
    }
}
