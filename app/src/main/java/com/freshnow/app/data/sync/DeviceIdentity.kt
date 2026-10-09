package com.freshnow.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_identity")

/**
 * 本机在同步里的身份。
 *
 * 设备号必须落盘，且**一经定下不再变**：对端把它记在 sync_peers 的主键上，改一次就等于换了台设备，
 * 所有已配对的关系一起失效。
 *
 * 用随机值而不是 ANDROID_ID 一类的系统标识：同步只要求「在这几台设备之间认得出对方」，
 * 随机值就够，而系统标识各有各的坑（有的恢复出厂即变、有的要额外权限、有的本身还能被改），
 * 更不该为一件局域网内的小事去碰设备的隐私标识。
 *
 * [dataStore] 由外部传入，测试可以换成临时文件；直接拿设备上的真实身份做测试会改掉用户的配对关系。
 */
class DeviceIdentity(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.identityDataStore)

    /**
     * 读设备号，没有就当场生成一个。
     *
     * 生成写在 edit 里，而不是「先读、再判空、后写」：并发调用时后者会各自生成一个再互相覆盖，
     * 同一次启动里两条路径于是拿到两个不同的设备号，对端那边就变成了两台设备。
     */
    suspend fun deviceId(): String {
        // 设备号在进程里不会变，缓存起来：每次写库都要问一遍，而 DataStore 每次读都要起一个收集
        cached?.let { return it }
        val stored = dataStore.data.first()[KEY_DEVICE_ID]?.takeIf { it.isNotEmpty() }
        val resolved = stored ?: dataStore.edit { current ->
            if (current[KEY_DEVICE_ID].isNullOrEmpty()) {
                current[KEY_DEVICE_ID] = UUID.randomUUID().toString()
            }
        }[KEY_DEVICE_ID].orEmpty()
        check(resolved.isNotEmpty()) { "设备号写入后仍读不到" }
        cached = resolved
        return resolved
    }

    @Volatile
    private var cached: String? = null

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
