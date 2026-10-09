package com.freshnow.app.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshnow.app.data.ImageChange
import com.freshnow.app.data.ScanImageStore
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.FreshNowDatabase
import com.freshnow.app.data.local.ScanRecord
import com.freshnow.app.data.local.SyncPeer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * 两台设备之间的整条路：配对、交换、合并、取图。
 *
 * 两台「设备」跑在同一个进程里，但各自有独立的库、照片目录、身份文件与服务端口，中间走真实的
 * HTTP 往返（127.0.0.1）——除了不经过网线，协议那一段与真机上完全一样。共用任何一处存储都会让
 * 测出来的东西不再是同步逻辑：要验的正是「两边各自看到的状态不一样时会怎样」。
 */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private lateinit var deviceA: TestDevice
    private lateinit var deviceB: TestDevice

    @Before
    fun openDevices() = runBlocking {
        deviceA = TestDevice("a").also { it.start() }
        deviceB = TestDevice("b").also { it.start() }
    }

    @After
    fun closeDevices() {
        deviceA.close()
        deviceB.close()
    }

    /** 另一台有的记录要能拿过来 */
    @Test
    fun sync_bringsTheOtherDevicesRecordsOver() = runBlocking {
        deviceA.addRecord("纯牛奶")
        deviceA.addRecord("鸡蛋")
        pair()

        syncInitiator(deviceB, with = deviceA)

        assertEquals(setOf("纯牛奶", "鸡蛋"), deviceB.visible().map { it.productName }.toSet())
    }

    /** 本机有的也要送过去：一次往返是双向的，不是「拉一次、推一次」 */
    @Test
    fun sync_carriesLocalRecordsOver() = runBlocking {
        deviceB.addRecord("全麦面包")
        pair()

        syncInitiator(deviceB, with = deviceA)

        assertEquals(setOf("全麦面包"), deviceA.visible().map { it.productName }.toSet())
        assertEquals(setOf("全麦面包"), deviceB.visible().map { it.productName }.toSet())
    }

    /** 连着同步两次不该多出东西来。合并是幂等的，否则用户每点一次「同步」列表就长一截 */
    @Test
    fun syncingTwice_changesNothingMore() = runBlocking {
        deviceA.addRecord("纯牛奶")
        pair()

        syncInitiator(deviceB, with = deviceA)
        syncInitiator(deviceB, with = deviceA)

        assertEquals(1, deviceB.visible().size)
        assertEquals(1, deviceA.visible().size)
    }

    /** 改一条已同步的记录，改动要跟着过去 */
    @Test
    fun sync_carriesAnEditOver() = runBlocking {
        deviceA.addRecord("纯牛奶")
        pair()
        syncInitiator(deviceB, with = deviceA)

        val onA = deviceA.visible().single()
        deviceA.records.update(onA.copy(productName = "低脂纯牛奶"), ImageChange.Keep)
        syncInitiator(deviceB, with = deviceA)

        assertEquals("低脂纯牛奶", deviceB.visible().single().productName)
    }

    /**
     * 删除要传过去。没有墓碑的话这条必然失败：行直接从库里消失，对端不知道有过这次删除，
     * 下一次同步会把它当成「你那边还没有」又推回来。
     */
    @Test
    fun sync_carriesADeletionOver() = runBlocking {
        deviceA.addRecord("纯牛奶")
        pair()
        syncInitiator(deviceB, with = deviceA)
        assertEquals(1, deviceB.visible().size)

        deviceA.records.delete(deviceA.visible().map { it.id })
        syncInitiator(deviceB, with = deviceA)

        assertTrue("删掉的记录不能自己长回来", deviceB.visible().isEmpty())
        assertTrue(deviceA.visible().isEmpty())
    }

    /**
     * 两端各改同一条时，最终必须收敛到同一个值。
     *
     * 这里只钉「收敛」，谁赢由 LWW 决定（同一毫秒撞车时还要比设备号），那不是端到端能确定的东西——
     * 具体判据在 ScanRecordDaoTest 里逐条钉。
     */
    @Test
    fun conflictingEdits_converge() = runBlocking {
        deviceA.addRecord("纯牛奶")
        pair()
        syncInitiator(deviceB, with = deviceA)

        deviceA.records.update(
            deviceA.visible().single().copy(productName = "A 改的"),
            ImageChange.Keep
        )
        deviceB.records.update(
            deviceB.visible().single().copy(productName = "B 改的"),
            ImageChange.Keep
        )

        syncInitiator(deviceB, with = deviceA)
        syncInitiator(deviceA, with = deviceB)

        assertEquals(
            "一次冲突之后两边不能各留一份",
            deviceA.visible().single().productName,
            deviceB.visible().single().productName
        )
    }

    /** 照片不跟着交换走：交换里只有名字，图要等真正要看的时候才取 */
    @Test
    fun photo_comesOverOnlyWhenAskedFor() = runBlocking {
        deviceA.addRecord("纯牛奶", photo = PHOTO)
        pair()

        syncInitiator(deviceB, with = deviceA)

        val onB = deviceB.visible().single()
        assertTrue("对端那条有图，名字要跟着过来", onB.imageName.isNotEmpty())
        assertNull("图还没取，本地不该凭空多出文件", deviceB.records.imageFile(onB))

        val peer = requireNotNull(deviceB.peers.find(deviceA.deviceId()))
        assertTrue(deviceB.engine.fetchImage(onB, peer, deviceA.address))

        val fetched = deviceB.visible().single()
        assertNotNull("取过之后要能读到这张图", deviceB.records.imageFile(fetched))
        assertEquals("两边存的应当是同一张图", onB.imageName, fetched.imageName)
    }

    /** 取图不该改动记录本身：用户没编辑过它，凭空盖一个修改时刻会把对端真正的新版本顶掉 */
    @Test
    fun fetchingAPhoto_doesNotCountAsAnEdit() = runBlocking {
        deviceA.addRecord("纯牛奶", photo = PHOTO)
        pair()
        syncInitiator(deviceB, with = deviceA)

        val before = deviceB.visible().single()
        val peer = requireNotNull(deviceB.peers.find(deviceA.deviceId()))
        deviceB.engine.fetchImage(before, peer, deviceA.address)

        val after = deviceB.visible().single()
        assertEquals(before.updatedAt, after.updatedAt)
        assertEquals(before.updatedBy, after.updatedBy)
    }

    @Test
    fun pairing_withAWrongCode_isRefused() = runBlocking {
        val code = deviceA.pairing.open()
        val wrong = if (code == "000000") "000001" else "000000"

        val response = deviceB.client.pair(
            deviceA.address,
            wrong,
            PairRequest(deviceB.deviceId(), deviceB.deviceName(), SyncCrypto.newSecret().toSecretText())
        )

        assertNull(response)
        assertTrue("码不对，发起方不该记下这段关系", deviceB.peers.all().isEmpty())
        assertTrue("被配对方也不该记下", deviceA.peers.all().isEmpty())
    }

    /** 猜错三次就把窗口关上，否则「窗口只开两分钟」对用户是真的、对猜的人不是 */
    @Test
    fun pairing_closesAfterTooManyWrongAttempts() = runBlocking {
        deviceA.pairing.open()
        val request = PairRequest(
            deviceB.deviceId(),
            deviceB.deviceName(),
            SyncCrypto.newSecret().toSecretText()
        )

        repeat(3) {
            deviceB.client.pair(deviceA.address, "999999", request)
        }

        assertFalse(deviceA.pairing.isOpen)
    }

    /** 配对成功之后窗口要关：一个已经被看见的码不该继续留在屏幕上有效 */
    @Test
    fun pairing_closesTheWindowOnceItSucceeds() = runBlocking {
        pair()

        assertFalse(deviceA.pairing.isOpen)
        assertEquals(1, deviceA.peers.all().size)
        assertEquals(1, deviceB.peers.all().size)
    }

    /**
     * 一方解除配对，另一方那边那条也得没。
     *
     * 否则用户会看到一台「已配对」但其实早就连不上的设备，而且每次同步都白白试它一遍。
     */
    @Test
    fun unpairing_tellsTheOtherSide() = runBlocking {
        pair()

        val onA = requireNotNull(deviceA.peers.find(deviceB.deviceId()))
        assertTrue(deviceA.client.unpair(deviceB.address, deviceA.deviceId(), onA))
        deviceA.peers.forget(deviceB.deviceId())

        assertNull("对方那边不该还挂着本机", deviceB.peers.find(deviceA.deviceId()))
        assertEquals(0, deviceB.peers.all().size)
    }

    /**
     * 通知没送到时的兼底：对方当时没开着应用，等它下次来同步时自己发现。
     *
     * 这里直接抹掉 A 那边的记录来模拟「解除成功但通知失败」——服务端查不到本机时
     * 会回一个带标记的 401，对方据此删掉自己那条已经失效的记录。
     */
    @Test
    fun aPeerDroppedOnTheOtherSide_isCleanedUpOnNextSync() = runBlocking {
        pair()

        deviceA.peers.forget(deviceB.deviceId())

        syncInitiator(deviceB, with = deviceA)

        assertNull("对方都不认了，本机这条留着只会每次白试", deviceB.peers.find(deviceA.deviceId()))
    }

    /**
     * 地址失效后可能连到别的设备上：那台也会说「我不认识你」，但那段话与本次配对关系无关。
     * 光看标记不够——必须确认说这话的正是本机要找的那台，否则地址一变就会误删一段还好好的配对。
     */
    @Test
    fun aStrangerSayingItDoesNotKnowUs_keepsThePairing() = runBlocking {
        pair()
        val stranger = TestDevice("stranger").also { it.start() }
        try {
            val onB = requireNotNull(deviceB.peers.find(deviceA.deviceId()))

            deviceB.engine.syncWith(onB, stranger.address)

            assertNotNull("连错设备不该动本机的配对关系", deviceB.peers.find(deviceA.deviceId()))
        } finally {
            stranger.close()
        }
    }

    /** 没配对就去同步，对端必须回绝 */
    @Test
    fun syncingWithoutPairing_isRefused() = runBlocking {
        deviceA.addRecord("纯牛奶")

        val stranger = SyncPeer(
            deviceId = UUID.randomUUID().toString(),
            deviceName = "陌生设备",
            secret = SyncCrypto.newSecret().toSecretText(),
            lastAddress = deviceA.address,
            pairedAt = 0L
        )

        assertNull(deviceB.engine.syncWith(stranger, deviceA.address))
        assertTrue("没配对就不该拿到对方的数据", deviceB.visible().isEmpty())
    }

    // ---- 下面都是把两台设备摆到一起的辅助 ----

    /** 让 B 用 A 显示出来的码配上 A */
    private suspend fun pair() {
        val code = deviceA.pairing.open()
        val response = requireNotNull(
            deviceB.client.pair(
                deviceA.address,
                code,
                PairRequest(
                    deviceId = deviceB.deviceId(),
                    deviceName = deviceB.deviceName(),
                    secret = SyncCrypto.newSecret().toSecretText()
                )
            )
        ) { "配对应当成功" }

        deviceB.peers.remember(
            SyncPeer(
                deviceId = response.deviceId,
                deviceName = response.deviceName,
                secret = response.secret,
                lastAddress = deviceA.address,
                pairedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun syncInitiator(initiator: TestDevice, with: TestDevice): SyncOutcome? {
        val peer = requireNotNull(initiator.peers.find(with.deviceId())) { "发起方应当已经和对方配对" }
        return initiator.engine.syncWith(peer, with.address)
    }

    private companion object {
        val PHOTO = byteArrayOf(0x11, 0x22, 0x33, 0x44)
    }
}

/**
 * 测试里的一台设备：库、照片目录、身份文件、服务端全都自成一套。
 */
private class TestDevice(label: String) {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, FreshNowDatabase::class.java).build()
    private val directory = File(context.cacheDir, "sync_test_$label-${UUID.randomUUID()}")
    private val identityFile =
        File(context.cacheDir, "sync_test_identity_$label-${UUID.randomUUID()}.preferences_pb")

    val identity = DeviceIdentity(PreferenceDataStoreFactory.create { identityFile })
    val records = ScanRecordRepository(
        context,
        ScanImageStore(directory),
        database.scanRecordDao(),
        identity
    )
    val peers = SyncPeerRepository(database.syncPeerDao())
    val pairing = PairingSession()
    val client = SyncClient()
    val engine = SyncEngine(records, peers, identity, client)

    private val server = SyncServer(records, peers, identity, pairing)
    private var port = 0

    val address: String get() = "127.0.0.1:$port"

    suspend fun start() {
        port = server.start()
    }

    suspend fun deviceId(): String = identity.deviceId()

    suspend fun deviceName(): String = identity.deviceName()

    suspend fun addRecord(name: String, photo: ByteArray? = null) {
        records.insert(
            ScanRecord(
                productName = name,
                productionDate = "2026-01-01",
                expiryDate = "2026-12-31",
                shelfLife = "12个月",
                imageName = "",
                savedAt = System.currentTimeMillis()
            ),
            photo?.let { ImageChange.Replace(it) } ?: ImageChange.Keep
        )
    }

    /** 界面上看得见的那些（不含墓碑） */
    suspend fun visible(): List<ScanRecord> = records.snapshot().filter { it.deletedAt == 0L }

    fun close() {
        server.stop()
        client.close()
        database.close()
        directory.deleteRecursively()
        identityFile.delete()
    }
}
