package com.freshnow.app.data.sync

import com.freshnow.app.data.local.ScanRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 两台设备之间来往的内容。
 *
 * 交换的是**整份状态**，不是一串增量操作：每家几百条记录，一条几百字节，一次往返也就几十到
 * 几百 KB，局域网里是毫秒级的事。换成增量则要维护「每台设备同步到哪了」的水位，而水位在时钟
 * 不同步、设备可能离线很久的情况下很容易算错——算错的代价是丢更新，且要到很久以后才会显形。
 * 全量交换是幂等的：同一条记录收到几次都合出同一个结果，出错了再同步一次就好。
 * 记录规模涨到万级时这个取舍要重新算（那时全量的往返时间和内存都开始明显）。
 */
@Serializable
internal data class SyncPayload(
    val deviceId: String,
    val deviceName: String,
    val records: List<SyncRecordDto>
)

/**
 * 一条记录在同步里的样子。
 *
 * 刻意不带本机的 `id`：那是各设备自己的自增主键，同一台设备上的两条记录在另一台上可能正好
 * 对上号，传过去只会让对端把两条不同的记录当成同一条。身份只有 [syncId] 一个。
 *
 * [imageName] 取对端设备上的文件名，接收方据此判断「这张图我有没有」——两边都按内容散列命名，
 * 所以同一张照片的名字在同一对设备上必然相同。对端换了照片名字就跟着变，接收方看到名字对不上，
 * 就知道本地那份缓存已经过时，该重新取了。
 */
@Serializable
internal data class SyncRecordDto(
    val syncId: String,
    val productName: String,
    val productionDate: String,
    val expiryDate: String,
    val shelfLife: String,
    val imageName: String,
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
    imageName = imageName,
    savedAt = savedAt,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    deletedAt = deletedAt
)

/**
 * 把对端来的记录变成本地的一条新记录，`id` 交回给 Room 去发。
 *
 * [imageName] 直接采用对端给的名字而不是清空：两边按内容算名字，名字相同就说明本地已经有
 * 这张图（内容寻址的好处），[com.freshnow.app.data.ScanImageStore.find] 一查就命中；
 * 名字不同则本地查不到文件，界面显示占位图，等到真正要看这张图时再去对端取。
 */
internal fun SyncRecordDto.toRecord(): ScanRecord = ScanRecord(
    id = 0,
    productName = productName,
    productionDate = productionDate,
    expiryDate = expiryDate,
    shelfLife = shelfLife,
    imageName = imageName,
    savedAt = savedAt,
    syncId = syncId,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    deletedAt = deletedAt
)
