package com.freshnow.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一次配对的时间窗。
 *
 * 配对码是一把共享秘密，谁先看到谁就能配上，所以它不能长期有效：只在用户打开配对页之后存在，
 * 配对成功或超时即作废。在线猜码的成本由两条一起挡住：每次尝试都要拿配对码派生出密钥去解密请求
 * （PBKDF2 十万次迭代，见 [SyncCrypto.derivePairingKey]），解不开就记一次失败（[recordFailure]），
 * 三次即关窗。于是窗口两分钟、三次机会，猜中概率是百万分之三。
 *
 * 码用 [StateFlow] 而不是一个普通字段：关窗有两个来源（用户关、对方配对成功、超时），
 * 而界面要跟着变。让界面去轮询「还开着吗」的话，界面上会留着码、服务端已经不认它，
 * 用户手里那串数字看起来完全正常，却怎么都配不上。
 */
internal class PairingSession {

    private val _code = MutableStateFlow<String?>(null)

    /** 当前该显示的码，null 表示窗口没开 */
    val code: StateFlow<String?> = _code.asStateFlow()

    val isOpen: Boolean
        get() = _code.value != null

    val currentCode: String?
        get() = _code.value

    /**
     * 开窗并给出新码。重复调用会作废上一个码——用户中途退出配对页再进来，屏幕上显示的是新码，
     * 旧码继续有效就等于把窗口延长了一倍。
     */
    @Synchronized
    fun open(): String {
        val fresh = SyncCrypto.newPairingCode()
        _code.value = fresh
        attemptsLeft = MAX_ATTEMPTS
        return fresh
    }

    @Synchronized
    fun close() {
        _code.value = null
        attemptsLeft = 0
    }

    /**
     * 记一次失败的尝试。载荷解不开时也走这里：那同样是一次「拿着码来试」的行为。
     */
    @Synchronized
    fun recordFailure() {
        if (_code.value == null) return
        attemptsLeft--
        if (attemptsLeft <= 0) close()
    }

    private var attemptsLeft = 0

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
