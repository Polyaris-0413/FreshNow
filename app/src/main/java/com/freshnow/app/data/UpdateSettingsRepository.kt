package com.freshnow.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_settings")

/**
 * 检查更新的设置：用户点过「关闭」的那个版本。
 *
 * 只记一个版本号而不是一个「不再提示」开关：忽略是就事论事的（这一个版本不看），
 * 下次发了更新的版本仍要提示——开关会把往后的更新一起静音掉。
 *
 * [dataStore] 由外部传入，测试可以换成临时文件；直接拿设备上真实的设置做测试会覆盖用户的忽略记录。
 */
class UpdateSettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.updateSettingsDataStore)

    val ignoredVersion: Flow<String?> = dataStore.data.map { it[KEY_IGNORED_VERSION] }

    suspend fun setIgnoredVersion(version: String) {
        dataStore.edit { it[KEY_IGNORED_VERSION] = version }
    }

    private companion object {
        val KEY_IGNORED_VERSION = stringPreferencesKey("ignored_version")
    }
}
