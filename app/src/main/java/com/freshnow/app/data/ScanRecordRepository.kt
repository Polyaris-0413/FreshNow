package com.freshnow.app.data

import android.content.Context
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.ScanRecordDao
import com.freshnow.app.data.sync.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 一条记录的照片该怎么处理。
 *
 * 说的是**目标状态**而不是一串动作：多图之后，「改一次照片」就是「留下哪几张、再加上哪几张」，
 * 分成加一张、删一张、换一张三个接口的话，调用方得自己决定先做哪个，而这里有个不能颠倒的顺序
 * （见 [ScanRecordRepository.update]）。
 */
sealed interface ImageChange {
    /** 照片原样保留 */
    data object Keep : ImageChange

    /**
     * 目标状态：留下的旧图名字（有序，取自这条记录当前的 [com.freshnow.app.data.local.ScanRecord.imageNames]）
     * 加上新加的图（按顺序接在后面）。字节已是正方形 JPEG（见 ScanImageCodec），这里只负责落盘。
     *
     * 去掉其中某一张 = 不把它放进 [kept]；一张都不要 = 两者都给空。
     */
    data class Update(val kept: List<String>, val added: List<ByteArray> = emptyList()) : ImageChange
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
        val photoReplaced = when (image) {
            // 先把行改掉再谈照片：反过来的话，改行失败就留下一个「行指着已被换掉的文件」
            ImageChange.Keep -> {
                dao.update(stamped)
                true
            }

            is ImageChange.Update -> writeImagesThenRow(image.kept, image.added, stamped.imageNames) { names ->
                dao.update(stamped.copy(imageNames = names))
            }
        }
        _localEdits.tryEmit(Unit)
        return photoReplaced
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
        val photoReplaced = when (image) {
            // 新记录本来就没有照片，Keep 与「一张都不要」在这里是同一件事
            ImageChange.Keep -> {
                dao.insert(fresh.copy(imageNames = emptyList()))
                true
            }

            is ImageChange.Update -> writeImagesThenRow(image.kept, image.added, emptyList()) { names ->
                dao.insert(fresh.copy(imageNames = names))
            }
        }
        _localEdits.tryEmit(Unit)
        return photoReplaced
    }

    /**
     * [frames] 是点保存那一刻的照片（扫描页抓的那一帧，加上收尾时补的几张）；哪一张落盘失败就不挂
     * 哪一张，界面用占位图代替。[location] 与它们同一次写入——收尾那一步填的位置也走这里。
     *
     * 返回新记录的 id：开关打开时扫描页要凭它进到这条记录的表单去补填。
     */
    suspend fun save(result: ScanResult, location: String, frames: List<ByteArray>): Long {
        val now = System.currentTimeMillis()
        val id = dao.insert(
            ScanRecord(
                productName = result.productName,
                productionDate = result.productionDate,
                expiryDate = result.expiryDate,
                shelfLife = result.shelfLife,
                location = location,
                imageNames = frames.mapNotNull { imageStore.write(it) },
                savedAt = now,
                syncId = UUID.randomUUID().toString(),
                updatedAt = now,
                updatedBy = identity.deviceId()
            )
        )
        _localEdits.tryEmit(Unit)
        return id
    }

    /** 记录的代表图（第一张），没有图片或文件已不在时返回 null */
    fun imageFile(record: ScanRecord): File? = imageStore.find(record.imageName)

    /** 记录名下所有还在磁盘上的照片，顺序与 [ScanRecord.imageNames] 一致 */
    fun imageFiles(record: ScanRecord): List<File> = record.imageNames.mapNotNull { imageStore.find(it) }

    /**
     * 界面上应该有图、但本地文件不在的记录。
     *
     * 名单来自同步：对端传来的记录带着它那边的图片名，而图本身不走交换（一张几百 KB，
     * 几百条就是几十 MB 的等待），于是本地先是一个空名字对应的占位图，等补图时再填上。
     */
    suspend fun recordsMissingImages(): List<ScanRecord> = withContext(Dispatchers.IO) {
        dao.findAll().filter { record ->
            record.deletedAt == 0L && record.imageNames.any { imageStore.find(it) == null }
        }
    }

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
        _localEdits.tryEmit(Unit)
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

    /** 某一张照片的字节，给同步往外发用；文件不在或读不出来返回 null */
    suspend fun imageBytes(name: String): ByteArray? = withContext(Dispatchers.IO) {
        imageStore.find(name)?.let { file -> runCatching { file.readBytes() }.getOrNull() }
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
        dao.appendImageName(syncId, name)
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
        // 名字来自全表：只查这一列的旧写法在 Room 里没法解出「一串名字」这种嵌套返回类型，
        // 而记录只有几百条、整份状态本来就要发给对端（见 SyncProtocol），多读几列不成为问题
        val referenced = dao.findAll().flatMap { it.imageNames }.toSet()
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
     * 与 [reconcileImages] 互斥也是必须的：照片先落盘、行后落库，中间那一刻这张图正是
     * 「没人引用」的，此时若来个清理就会把它当成孤儿删掉，随后行写进去指向一个不存在的文件。
     *
     * 一次写多张时逐张尽力，不是全或无：某一张失败（磁盘满、编码器拿不到内存）不该把已经写好的
     * 那些一起丢掉——「大部分照片存上了」比「一张都没有」更接近事实。但只要有一张没成功就如实
     * 返回 false，界面据此提示一句。
     *
     * 一张都没写成时（[added] 非空而 [written] 为空）照片列回退到 [unchanged]，也就是库里原来那串：
     * 那一张都没换上，等于这次换图没发生，旧照片该原样留着——界面提示的「照片保存失败，已保留原照片」
     * 说的就是这件事。文字字段不受影响，它们跟这次写入一起落库。
     *
     * 失败时**不删**刚写的图：照片名字按内容算，同名即同图，那张图可能正被另一条记录用着，
     * 删掉就把那条记录的照片弄丢了。
     */
    private suspend fun writeImagesThenRow(
        kept: List<String>,
        added: List<ByteArray>,
        unchanged: List<String>,
        writeRow: suspend (names: List<String>) -> Unit
    ): Boolean = imageLock.withLock {
        val written = mutableListOf<String>()
        var allWritten = true
        added.forEach { bytes ->
            val name = imageStore.write(bytes)
            if (name == null) allWritten = false else written += name
        }
        writeRow(if (written.isEmpty() && added.isNotEmpty()) unchanged else kept + written)
        allWritten
    }

    companion object {
        /** 墓碑保留期。短于两台设备可能间隔的最久同步周期，删除就会在对端「复活」 */
        private const val TOMBSTONE_RETENTION = 30L * 24 * 60 * 60 * 1000

        /** 见 [reconcileImages]。挂在伴生对象上才是进程级的 */
        private val imageLock = Mutex()

        /**
         * 「用户动了本地记录」。同步那边听它，一有改动就主动推一轮（见 SyncCoordinator）。
         *
         * 挂在伴生对象上而不是实例上，理由与 [imageLock] 同：每个 ViewModel 各建一个 Repository，
         * 而听的只有一个（同步协调器），实例级的流它听不到别人的写入。
         *
         * 只有用户来源的四个写入发（[update]、[insert]、[save]、[delete]），同步自己的回写
         * （[mergeAll]、[attachImage]）与收尾清理（[purge]、[reconcileImages]）都不发：
         * 回写要是也发，对端合完再推回来，两台设备会互相推个没完。
         */
        private val _localEdits = MutableSharedFlow<Unit>(
            // 缓冲一个、满了丢最旧的：要报的是「有改动」这件事，攒着同一条没有意义。
            // 没人听的时候（应用不在前台）丢掉正好，那时也没有会话可推
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        /** 见 [_localEdits] */
        val localEdits: SharedFlow<Unit> = _localEdits.asSharedFlow()
    }
}
