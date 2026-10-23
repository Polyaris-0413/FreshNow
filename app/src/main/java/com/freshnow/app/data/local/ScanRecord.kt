package com.freshnow.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条扫描记录。只落库模型读到的原文——[expiryDate] 是标签**印刷**的过期日期，推算值不存，
 * 展示时用 ExpiryCalculator 现算（推算逻辑改进后旧记录也能受益）。[location] 不是模型读到的，是用户写的。
 * 后四列只服务局域网同步（见 data/sync/）：与记录同表，因为生命周期完全重合，分表只会让每次读写多一次关联。
 * [syncId] 是这条记录在所有设备上的身份，诞生时定下，之后编辑、换照片都不变；[id] 是自增主键，
 * 两台设备必然撞号，只有 [syncId] 认得出「这两条是同一条」。
 * [updatedAt]/[updatedBy] 是 LWW 判据：时刻大的赢，时刻相同比设备号。[updatedBy] 不能省成「总是本机」——
 * 多设备下同一毫秒改同一条是会发生的，而两端的破平规则必须算出同一个赢家，否则越同步越分叉。
 * [deletedAt] 是墓碑：删除不真删行，否则对端不知道有过这次删除，下次同步会把这条推回来。
 * 非 0 即已删除，界面与同步都只看 0 的行。
 */
@Entity(
    tableName = "scan_records",
    // 唯一索引不只是查得快：合并若按 syncId 找漏了，插进来的第二条会在这里当场被拦下，
    // 而不是安静地在列表里长出一条重复记录
    indices = [Index(value = ["syncId"], unique = true)]
)
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val productionDate: String,
    val expiryDate: String,
    val shelfLife: String,
    /**
     * 东西放在哪儿。它是唯一一项不来自标签的字段：标签上不印这个，AI 也读不到（它看不到冰箱），
     * 只能由用户自己写，所以它不在 [com.freshnow.app.data.ScanResult] 那一套里。空串表示还没填。
     */
    val location: String = "",
    /**
     * 照片文件名，**顺序即展示顺序，第一位是代表图**（列表缩略图、详情页主图都用它）；空列表表示没有图片。
     * 名字是内容散列（见 ScanImageStore），同一张照片被两条记录引用时磁盘上只有一份，
     * 所以不能在某一处直接删文件——「没人引用」要由所有记录的名字集合推出来。
     */
    val imageNames: List<String> = emptyList(),
    val savedAt: Long,
    /**
     * 跨设备身份。默认空串只为让构造点少写一行：**写入路径必须经 Repository 落一个真值**，
     * 空串受唯一索引限制只容得下一条，漏填会当场炸掉，而不是留下一条认不出的记录。
     */
    val syncId: String = "",
    /** 最后一次修改的时刻。0 表示这条是加同步之前建的，迁移时回填成 [savedAt] */
    val updatedAt: Long = 0,
    /** 最后一次修改的设备号，[updatedAt] 撞车时用它破平。空串是迁移过来的老记录 */
    val updatedBy: String = "",
    /** 墓碑。非 0 即已删除，值就是删除时刻（同时也是参与 LWW 比较的时刻） */
    val deletedAt: Long = 0
) {

    /**
     * 只给还没改到多图语义的读取点用（同步载荷里给旧版本看的兼容键、「有没有图」判断）；
     * 多张的地方一律用 [imageNames]。写成成员是因为扩展属性每处使用都要 import，而它只是这条记录的一个视图。
     */
    val imageName: String get() = imageNames.firstOrNull().orEmpty()
}

/**
 * LWW 的判据。相等返回 false，调用方据此跳过——
 * 重复合并同一版必须是无操作，否则每次同步都会重写本行、白触发一次界面刷新。
 */
fun ScanRecord.beats(other: ScanRecord): Boolean =
    updatedAt > other.updatedAt || (updatedAt == other.updatedAt && updatedBy > other.updatedBy)
