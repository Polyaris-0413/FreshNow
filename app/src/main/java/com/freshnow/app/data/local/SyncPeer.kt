package com.freshnow.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一台已配对的对端设备。
 *
 * 配对是一次性的动作，之后每次同步都要靠这张表里的密钥认出对方，所以它必须落库——
 * 只放在内存里的话，应用一重启就要重新输配对码，而配对码要两台设备当面配合才能输入，
 * 那等于每次同步都要凑齐两台设备。
 *
 * [secret] 是配对那一刻由两台设备各自生成、再经配对码派生的密钥加密交换来的长期密钥，
 * 之后所有请求都靠它认证与解密，配对码本身用完即弃（6 位数字扛不住离线爆破，
 * 只在配对窗口那几分钟里当一次性的信任凭据）。
 */
@Entity(tableName = "sync_peers")
data class SyncPeer(
    /** 对端自己生成的设备号。随机值，两台设备不会撞号 */
    @PrimaryKey val deviceId: String,
    /** 对端显示名，配对时抄下来，用于设备列表 */
    val deviceName: String,
    /** 长期密钥，Base64。明文只在这台设备与那台设备之间存在 */
    val secret: String,
    /**
     * 最后一次连上的地址（host:port）。存在这里是为了省掉一轮发现：发现要走组播，
     * 而组播在部分机型上要额外持锁、在部分路由器上直接被挡，能直连时就别去碰它
     */
    val lastAddress: String,
    val pairedAt: Long
)
