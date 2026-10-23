package com.freshnow.app.data.sync

import com.freshnow.app.data.local.ScanRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 两台设备之间来往的内容。
 * 交换的是**整份状态**，不是增量：几百条记录一次往返也就几十到几百 KB，局域网里是毫秒级。
 * 换增量要维护水位，而水位在时钟不同步、设备长期离线时容易算错，代价是丢更新。
 * 全量交换幂等：同一条记录收到几次都合出同一个结果。记录涨到万级时这个取舍要重新算。
 */
@Serializable
internal data class SyncPayload(
    val deviceId: String,
    val deviceName: String,
    val records: List<SyncRecordDto>
)

/**
 * 一条记录在同步里的样子。
 * 刻意不带本机的 `id`：那是各设备自己的自增主键，同一台设备上的两条记录在另一台上可能正好对上号，
 * 传过去只会让对端把两条不同的记录当成同一条；身份只有 [syncId] 一个。
 * [imageName] 取对端设备上的文件名：两边都按内容散列命名，所以同一张照片的名字在同一对设备上必然相同；
 * 对端换了照片名字就跟着变，接收方看到名字对不上，就知道本地那份缓存已经过时。
 * 这根键现在只服务旧版本：新版本看 [imageNames]，它只当「第一张」。不能去掉——旧版本解码缺了
 * `imageName` 的载荷会当场抛异常，那时丢的不是一张图，是那条记录整个同步不过来。
 */
@Serializable
internal data class SyncRecordDto(
    val syncId: String,
    val productName: String,
    val productionDate: String,
    val expiryDate: String,
    val shelfLife: String,
    /**
     * 与其余几项不同，它不来自标签，是用户自己写的（见 [ScanRecord.location]）。
     *
     * 带默认值不是省事：对端可能是还不知道有这一项的版本，它发来的载荷里根本没有这个键，
     * 而解码缺键的对象会当场抛异常——那时失掉的不是位置，是那条记录整个同步不过来。
     */
    val location: String = "",
    val imageName: String,
    /**
     * 全部照片名，顺序即展示顺序。
     *
     * 带默认值不是省事：对端可能还是只知道 [imageName] 的版本，它发来的载荷里根本没有这个键（与
     * [location] 同一个理由）。收到旧载荷时当成「只有一张」，见 [toRecord]。
     */
    val imageNames: List<String> = emptyList(),
    val savedAt: Long,
    val updatedAt: Long,
    val updatedBy: String,
    val deletedAt: Long
)

/** 配对请求：发起方把自己和设备号、以及它生成的长期密钥一并交给对方 */
@Serializable
internal data class PairRequest(
    val deviceId: String,
    val deviceName: String,
    /** 长期密钥，Base64。此刻它是用配对码派生的密钥加密的 */
    val secret: String
)

/** 配对回应：被配对方交回自己的身份，密钥共用发起方那一把 */
@Serializable
internal data class PairResponse(
    val deviceId: String,
    val deviceName: String,
    val secret: String
)

/** 交换的结果见 SyncEngine.SyncOutcome */
internal val syncJson: Json = Json {
    // 以后加字段时，旧版本收到新版本的载荷不该当场崩掉，认不出的字段忽略就是
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun ScanRecord.toDto(): SyncRecordDto = SyncRecordDto(
    syncId = syncId,
    productName = productName,
    productionDate = productionDate,
    expiryDate = expiryDate,
    shelfLife = shelfLife,
    location = location,
    imageName = imageName,
    imageNames = imageNames,
    savedAt = savedAt,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    deletedAt = deletedAt
)

/**
 * 把对端来的记录变成本地的一条新记录，`id` 交给 Room 去发。
 * [imageName] 直接采用对端给的名字：两边按内容算名字，相同就说明本地已有这张图（内容寻址）；
 * 不同则本地查不到文件，界面显示占位图，等要看这张图时再去对端取。
 * [imageNames] 为空而 [imageName] 非空说明对端是旧版本，当成单元素列表；两个都空就是没有图片。
 */
internal fun SyncRecordDto.toRecord(): ScanRecord = ScanRecord(
    id = 0,
    productName = productName,
    productionDate = productionDate,
    expiryDate = expiryDate,
    shelfLife = shelfLife,
    location = location,
    imageNames = imageNames.ifEmpty { listOfNotNull(imageName.takeIf { it.isNotEmpty() }) },
    savedAt = savedAt,
    syncId = syncId,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    deletedAt = deletedAt
)
