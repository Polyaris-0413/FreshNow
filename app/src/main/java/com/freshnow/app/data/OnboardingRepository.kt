package com.freshnow.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

/**
 * 「这次安装走到哪一步了」。
 *
 * 与 [AiSettings]、[BehaviorSettings] 分开存：那两份记的是用户的选择，这份记的是引导有没有走过。
 * 合在一起的话，将来「恢复 AI 配置」之类的操作会连带把引导标记清掉，用户下次启动又被请去走一遍。
 *
 * 落盘的 key 不带版本号：将来若要重放引导，换一个 key 名即可，已经走过的用户不会被卷进来。
 */
class OnboardingRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.onboardingDataStore)

    /** 欢迎页是否已经走过（跳过也算走过） */
    val welcomeSeen: Flow<Boolean> = dataStore.data.map { it[KEY_WELCOME_SEEN] ?: false }

    suspend fun markWelcomeSeen() {
        dataStore.edit { preferences ->
            preferences[KEY_WELCOME_SEEN] = true
        }
    }

    private companion object {
        val KEY_WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
    }
}
