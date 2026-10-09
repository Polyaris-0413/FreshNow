package com.freshnow.app.data

import android.content.Context
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.ScanRecordDao
import com.freshnow.app.data.sync.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 一条记录的照片该怎么处理。
 *
 * 三种情形放在一个类型里，是为了让「改字段」与「换照片」走同一次写入：分成两条接口的话，
 * 调用方得自己决定先改哪个，而这里有个不能颠倒的顺序（见 [ScanRecordRepository.update]）。
 */
sealed interface ImageChange {
    /** 照片原样保留 */
    data object Keep : ImageChange

    /** 移除照片，记录回到「没有图片」的状态 */
    data object Remove : ImageChange

    /** 换成 [bytes]。字节已是正方形 JPEG（见 ScanImageCodec），这里只负责落盘 */
    data class Replace(val bytes: ByteArray) : ImageChange
}

/**
 * 扫描记录与它们的照片。
 *
 * 两个存储入口都能从外部传：记录与照片的写入顺序（先写新图、再改行）是要单独验的，
 * 而那一验要是碰设备上应用自己的库与图片目录，测试就变成了在改用户的记录（见 ScanImageStore 同样的理由）。
 *
 * 所有写入都要经这里，因为每一条都要盖上同步要用的「谁在什么时候改的」（[stamp]）：
 * 散在各个 ViewModel 里盖的话，漏掉任何一处都会让那次修改在同步里永远传不出去。
 */
class ScanRecordRepository(
    context: Context,
    private val imageStore: ScanImageStore = ScanImageStore(context),
    private val dao: ScanRecordDao = FreshNowDatabase.getInstance(context).scanRecordDao(),
    private val identity: DeviceIdentity = DeviceIdentity(context)
) {

    val records: Flow<List<ScanRecord>> = dao.observeAll()

    suspend fun find(id: Long): ScanRecord? = dao.findById(id)

    /**
     * 单条记录的订阅。详情页用它而不是查一次：编辑页改了字段后回到详情页，值要跟着变，
     * 判据落在数据上，不依赖页面何时被重新合成。
     */
    fun observe(id: Long): Flow<ScanRecord?> = dao.observeById(id)

    /**
     * 覆盖整行，按 [image] 处理照片。保存时间沿用传入记录里的值（调用方在原有记录上改字段即可），
     * 于是编辑不会把记录提到列表最前面。
     *
     * 返回 false 只表示照片没换成（新图没写成功，此时行还没动过）：旧照片原样保留，其余字段已经落盘，
     * 调用方只需就这一件事提示用户。写盘失败不拦下整次编辑，与 [ScanImageStore.write] 的取舍一致——
     * 没有可用画面时本来就有「这条记录没有图片」这个正常状态。
     */
    suspend fun update(record: ScanRecord, image: ImageChange): Boolean {
        val stamped = stamp(record)
        return when (image) {
            // 先把行改掉再谈照片：反过来的话，改行失败就留下一个「行指着已被换掉的文件」
            ImageChange.Keep -> {
                dao.update(stamped)
                true
            }

            ImageChange.Remove -> {
                dao.update(stamped.copy(imageName = ""))
                true
            }

            is ImageChange.Replace -> {
                val replaced = replaceImage(image.bytes) { newName ->
                    dao.update(stamped.copy(imageName = newName))
                }
                // 图没写成功：行照样按新字段落盘，照片保持原样
                if (!replaced) dao.update(stamped)
                replaced
            }
        }
    }

    /**
     * 新建一条记录（手动录入）。
     *
     * 返回 false 只表示照片没写成功：这条记录照样落库，只是没有图片（与扫描时「没有可用画面」
     * 是同一个状态）。写库整体失败会抛出，由调用方决定怎么办。
     */
    suspend fun insert(record: ScanRecord, image: ImageChange): Boolean {
        val fresh = stamp(record).let {
            // 身份只在这里发一次，之后编辑都带着它走
            if (it.syncId.isEmpty()) it.copy(syncId = UUID.randomUUID().toString()) else it
        }
        return when (image) {
            // 新记录本来就没有照片，Keep 与 Remove 在这里是同一件事
            ImageChange.Keep, ImageChange.Remove -> {
                dao.insert(fresh.copy(imageName = ""))
                true
            }

            is ImageChange.Replace -> {
                val replaced = replaceImage(image.bytes) { newName ->
                    dao.insert(fresh.copy(imageName = newName))
                }
                // 图没写成功：记录照样落库，只是没有图片
                if (!replaced) dao.insert(fresh.copy(imageName = ""))
                replaced
            }
        }
    }

    /**
     * [frame] 是点保存那一刻的最新画面；图片落盘失败时该记录就没有图片，界面显示占位图
     */
    suspend fun save(result: ScanResult, frame: ByteArray?) {
        val now = System.currentTimeMillis()
        dao.insert(
            ScanRecord(
                productName = result.productName,
                productionDate = result.productionDate,
                expiryDate = result.expiryDate,
                shelfLife = result.shelfLife,
                imageName = frame?.let { imageStore.write(it) }.orEmpty(),
                savedAt = now,
                syncId = UUID.randomUUID().toString(),
                updatedAt = now,
                updatedBy = identity.deviceId()
            )
        )
    }

    /** 记录对应的照片文件，没有图片或文件已不在时返回 null */
    fun imageFile(record: ScanRecord): File? = imageStore.find(record.imageName)

    /**
     * 删除记录。
     *
     * 不真删行，只打墓碑：行一旦没了，对端就不知道有过这次删除，下次同步会把这条记录推回来，
     * 用户看到的是「删掉的记录自己又长出来了」。墓碑留到 [TOMBSTONE_RETENTION] 之后由 [purge] 清掉；
     * 在这之前照片也留着，误删还有得救，占用的空间由那次清理一并收回。
     */
    suspend fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.markDeleted(ids.toList(), now, identity.deviceId())
    }

    // ---- 下面几个只给局域网同步用（见 data/sync/） ----

    /** 交换用：连墓碑在内的全部记录。墓碑必须发出去，理由见 [delete] */
    suspend fun snapshot(): List<ScanRecord> = dao.findAll()

    /**
     * 把对端来的一批记录并进本地，返回真正写进去几条。
     *
     * 没有 [ScanRecord.syncId] 的记录直接跳过：那种记录在本地认不出身份，插进来只会占掉
     * 唯一索引里那个空串的位置，让真正该合的记录合不进来。
     */
    suspend fun mergeAll(incoming: List<ScanRecord>): Int {
        var applied = 0
        for (record in incoming) {
            if (record.syncId.isEmpty()) continue
            if (dao.merge(record)) applied++
        }
        return applied
    }

    /** 记录的图片字节，给同步往外发用；没有图片或读不出来返回 null */
    suspend fun imageBytes(record: ScanRecord): ByteArray? = withContext(Dispatchers.IO) {
        imageStore.find(record.imageName)?.let { file -> runCatching { file.readBytes() }.getOrNull() }
    }

    suspend fun findBySyncId(syncId: String): ScanRecord? = dao.findBySyncId(syncId)

    /**
     * 把从对端取回的照片存下并挂到记录上。
     *
     * 刻意不盖 [ScanRecord.updatedAt]：这只是把本机缺的一张图缓存下来，用户并没有修改这条记录，
     * 盖了时间戳就等于凭空造出一次「本机改了这条」，会把对端真正的新版本顶掉。
     */
    suspend fun attachImage(syncId: String, bytes: ByteArray) {
        val name = imageStore.write(bytes) ?: return
        dao.updateImageName(syncId, name)
    }

    /**
     * 清掉过了保留期的墓碑，并收回没人引用的照片。
     *
     * 保留期的作用是给还没同步上的设备留出窗口：墓碑一删，对端下次交换时就看不到这次删除，
     * 会把那条记录当新增推回来。
     */
    suspend fun purge() {
        dao.deletePurgeable(System.currentTimeMillis() - TOMBSTONE_RETENTION)
        reconcileImages()
    }

    /**
     * 删掉磁盘上没有任何记录引用的照片。
     *
     * 与换照片互斥：照片先落盘、行后落库，中间那一刻这张图正是「没人引用」的，此时若来个清理
     * 就会把它当成孤儿删掉，随后行写进去指向一个不存在的文件。互斥锁消除了这个窗口，
     * 而按时间宽限（只删「够老的」孤儿）只是把窗口缩小，该撞上的还是会撞上。
     *
     * 锁在伴生对象上而不是实例上：每个 ViewModel 各建一个 Repository，实例锁拦不住另一个实例。
     */
    suspend fun reconcileImages() = imageLock.withLock {
        val referenced = dao.findAllImageNames().toSet()
        imageStore.listAll().filterNot { it in referenced }.forEach { imageStore.delete(it) }
    }

    /**
     * 盖上「谁在什么时候改的」。
     *
     * 时刻取 max(现在, 上一版 + 1)：同一毫秒内改两次时，若时刻与设备号都相同，LWW 会判第二次
     * 「不比第一次新」，对端于是永远停在第一版。这类丢更新极难复现（要同一毫秒、同一设备、同一条），
     * 但一旦发生就是两台设备永久不一致，所以宁可让时刻严格递增。
     */
    private suspend fun stamp(record: ScanRecord): ScanRecord {
        val deviceId = identity.deviceId()
        return record.copy(
            updatedAt = maxOf(System.currentTimeMillis(), record.updatedAt + 1),
            updatedBy = deviceId
        )
    }

    /**
     * 换照片：先落图、再落行。
     *
     * 顺序不能倒——先落行的话，图还没写成功，行里的名字已经指向一个不存在的文件，
     * 界面显示占位图而用户以为照片存上了。反过来（图先写、行后失败）只会留下一张没人引用的图，
     * 那由 [reconcileImages] 收走，用户看到的「没换成功」与事实相符。
     *
     * 失败时**不删**刚写的图：照片名字按内容算，同名即同图，那张图可能正被另一条记录用着，
     * 删掉就把那条记录的照片弄丢了。
     */
    private suspend fun replaceImage(
        bytes: ByteArray,
        writeRow: suspend (newName: String) -> Unit
    ): Boolean = imageLock.withLock {
        val newName = imageStore.write(bytes) ?: return@withLock false
        writeRow(newName)
        true
    }

    private companion object {
        /** 墓碑保留期。短于两台设备可能间隔的最久同步周期，删除就会在对端「复活」 */
        const val TOMBSTONE_RETENTION = 30L * 24 * 60 * 60 * 1000

        /** 见 [reconcileImages]。挂在伴生对象上才是进程级的 */
        val imageLock = Mutex()
    }
}
