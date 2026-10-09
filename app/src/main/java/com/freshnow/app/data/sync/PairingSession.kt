package com.freshnow.app.data.sync

/**
 * 一次配对的时间窗。
 *
 * 配对码是一把共享秘密，谁先看到谁就能配上，所以它不能长期有效：只在用户打开配对页之后
 * 存在，配对成功或超时即作废。次数限制与固定时间比较（[SyncCrypto.codeMatches]）一起，
 * 把「在线猜码」压到没有胜算——窗口两分钟、三次机会，猜中概率是百万分之三。
 */
internal class PairingSession {

    private var code: String? = null
    private var attemptsLeft = 0

    val isOpen: Boolean
        @Synchronized get() = code != null

    /** 当前该显示的码，没开窗时为 null */
    val currentCode: String?
        @Synchronized get() = code

    /**
     * 开窗并给出新码。重复调用会作废上一个码——用户中途退出配对页再进来，屏幕上显示的是新码，
     * 旧码继续有效就等于把窗口延长了一倍。
     */
    @Synchronized
    fun open(): String {
        val fresh = SyncCrypto.newPairingCode()
        code = fresh
        attemptsLeft = MAX_ATTEMPTS
        return fresh
    }

    @Synchronized
    fun close() {
        code = null
        attemptsLeft = 0
    }

    /**
     * 核对一次尝试。对就返回 true；错则扣掉一次机会，机会用尽当场关窗——
     * 不关的话，攻击者可以在这个窗口里一直猜下去，而用户看到码还以为它只在两分钟内有效。
     */
    @Synchronized
    fun verify(candidate: String): Boolean {
        val expected = code ?: return false
        if (SyncCrypto.codeMatches(expected, candidate)) return true
        recordFailure()
        return false
    }

    /**
     * 记一次失败的尝试。载荷解不开时也走这里：那同样是一次「拿着码来试」的行为。
     */
    @Synchronized
    fun recordFailure() {
        if (code == null) return
        if (--attemptsLeft <= 0) close()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
