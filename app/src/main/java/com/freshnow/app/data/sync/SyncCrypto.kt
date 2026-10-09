package com.freshnow.app.data.sync

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 同步路上的加解密。
 *
 * 用配对码派生的密钥直接加密载荷，而不是走 TLS 加自签证书：自签证书要求两端各自核对对方的指纹，
 * 于是「配对」被拆成两半——一半是配对码，另一半是指纹——而指纹得靠另一条信道送过去，
 * 在只有局域网、两端都是陌生人的场景里并没有这样一条信道。配对码本身就是那条信道：
 * 用户看着一台设备的屏幕，在另一台上把码输进去。
 *
 * 载荷里装的不是密钥而是内容，所以这套方案不提供前向保密：长期密钥一旦泄露，录下来的旧密文
 * 也能解开。局域网里的家庭场景用不上那个性质，而它要的代价（每次会话换密钥、配对码退化成
 * 交换一次性的公钥）会把这个功能的复杂度抬高一档。
 */
internal object SyncCrypto {

    private const val SECRET_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 100_000
    private const val CIPHER = "AES/GCM/NoPadding"

    /**
     * 把配对码变成密钥。
     *
     * 迭代十万次是为了抬高在线猜码的成本：6 位数字只有一百万种可能，一次派生要花几十毫秒，
     * 猜穿一轮是十几个小时，而配对窗口只开两分钟。挡不住的是「拿到密文之后离线慢慢算」——
     * 那需要攻击者先录下一次配对流量，而配对窗口那么短、还要两台设备凑在一起，不值得为它
     * 把配对码加长到用户记不住的长度。
     *
     * [salt] 取被配对那台设备的设备号：同一个码在两次配对里派生出不同的密钥，上一次录下的
     * 密文对这一次毫无用处。
     */
    fun derivePairingKey(code: String, salt: String): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt.toByteArray(), PBKDF2_ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** 生成一把新密钥。配对时由发起方生成一次，之后两台设备共用它 */
    fun newSecret(): ByteArray = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }

    /**
     * 加密并认证，返回 nonce 拼上密文（GCM 的认证标签在密文末尾）。
     *
     * nonce 每次随机而不是用计数器：GCM 在同一把密钥下重用 nonce 会直接泄露明文，
     * 而计数器那种「只要不重启就不会重复」的保证，在手机上撑不过一次进程重启。
     */
    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    /**
     * 解密。密钥不对、密文被改过、内容与设备号对不上，都在这里抛出：GCM 的认证标签已经覆盖了
     * 这些，调用方按「这次请求不可信」一律回绝即可，不必再逐项核对。
     */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        require(sealed.size > NONCE_BYTES) { "密文长度不足" }
        val nonce = sealed.copyOfRange(0, NONCE_BYTES)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        return cipher.doFinal(sealed.copyOfRange(NONCE_BYTES, sealed.size))
    }

    /** 随机一个 6 位配对码。前导零要留着，否则「000123」会变成五位 */
    fun newPairingCode(): String =
        SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    /**
     * 配对码的比较用固定时间的实现：普通的字符串比较遇到第一个不同的字符就返回，
     * 失败次数多了，攻击者能从耗时差异里把码一位一位试出来。
     */
    fun codeMatches(expected: String, candidate: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(), candidate.toByteArray())
}
