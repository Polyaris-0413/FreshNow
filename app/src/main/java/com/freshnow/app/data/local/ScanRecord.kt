package com.freshnow.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条扫描记录。只落库模型读到的原文——[expiryDate] 是标签**印刷**的过期日期，由生产日期与
 * 保质期推算出来的那个值不存，展示时用 ExpiryCalculator 现算，推算逻辑改进后旧记录也能受益
 *
 * 后四列只服务于局域网同步（见 data/sync/）。与记录同表而不是另开一张同步表：它们的生命周期与
 * 记录完全重合，分表只会让每次读写都多一次关联，而「同一行」这件事本来就该由一个主键说了算。
 *
 * [syncId] 是这条记录在**所有设备**上的身份。[id] 是自增主键，两台设备各建各的必然撞号，
 * 只有 [syncId] 认得出「这两条是同一条」。它在记录诞生时定下，之后编辑、换照片都不变。
 *
 * [updatedAt] 与 [updatedBy] 是冲突判据（LWW）：两端都改了同一条时时刻大的赢；时刻相同时比设备号。
 * 破平不能只看时刻——多设备下同一毫秒改同一条是会发生的，而两边的破平规则必须算出同一个赢家，
 * 否则一次冲突会在两台设备上得到相反的结果，越同步越分叉，所以 [updatedBy] 不能省成「总是本机」。
 *
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
    /** 扫描照片的文件名，空串表示这条记录没有图片 */
    val imageName: String,
    val savedAt: Long,
    /**
     * 跨设备身份。默认空串只为让构造点少写一行——**写入路径必须经 Repository 落一个真值**，
     * 空串在同一张表里只容得下一条（唯一索引），漏填会当场炸掉，而不是留下一条认不出的记录
     */
    val syncId: String = "",
    /** 最后一次修改的时刻。0 表示这条是加同步之前建的，迁移时回填成 [savedAt] */
    val updatedAt: Long = 0,
    /** 最后一次修改的设备号，[updatedAt] 撞车时用它破平。空串是迁移过来的老记录 */
    val updatedBy: String = "",
    /** 墓碑。非 0 即已删除，值就是删除时刻（同时也是参与 LWW 比较的时刻） */
    val deletedAt: Long = 0
)

/**
 * LWW 的判据：本条是不是比 [other] 新。时刻大的赢，时刻相同就比设备号。
 *
 * 相等返回 false（谁都不赢），调用方据此跳过这一条——重复合并同一版必须是无操作，
 * 否则每次同步都会把本行重写一遍，白白触发一次界面刷新。
 */
fun ScanRecord.beats(other: ScanRecord): Boolean =
    updatedAt > other.updatedAt || (updatedAt == other.updatedAt && updatedBy > other.updatedBy)
