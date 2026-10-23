package com.freshnow.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 迁移只在用户升级那一刻跑一次，写错了一年也不会有第二个用户来撞见——而它撞上的是「记录全没了」。
 * 所以用 Room 的迁移测试助手：先建一个真的旧版库、塞进记录，再跑迁移，最后按新版表结构校验一遍。
 * 与 ScanRecordDaoTest 同样用测试专用的库名，不碰设备上应用自己的库。
 */
@RunWith(AndroidJUnit4::class)
class ScanRecordMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FreshNowDatabase::class.java
    )

    /** 没有 syncId 的记录在同步里等于不存在 */
    @Test
    fun migrate3To4_keepsRecordsAndGivesThemAnIdentity() {
        helper.createDatabase(TEST_DATABASE, 3).use { database ->
            database.execSQL(
                "INSERT INTO `scan_records` " +
                    "(`productName`, `productionDate`, `expiryDate`, `shelfLife`, `imageName`, `savedAt`) " +
                    "VALUES ('纯牛奶', '2026-01-01', '2026-12-31', '12个月', 'old.jpg', 111)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, MIGRATION_3_4)

        migrated.query(
            "SELECT `productName`, `imageName`, `syncId`, `updatedAt`, `updatedBy`, `deletedAt` " +
                "FROM `scan_records`"
        ).use { cursor ->
            assertTrue("老记录不能被迁移弄丢", cursor.moveToFirst())
            assertEquals("纯牛奶", cursor.getString(0))
            assertEquals("照片文件名要原样留着", "old.jpg", cursor.getString(1))
            assertTrue("必须拿到一个身份", cursor.getString(2).isNotEmpty())
            assertEquals("修改时刻回填成保存时刻", 111L, cursor.getLong(3))
            assertEquals("那时还没有设备号，留空", "", cursor.getString(4))
            assertEquals("不是墓碑", 0L, cursor.getLong(5))
        }
    }

    /**
     * 每条记录的身份必须各不相同：固定值会被唯一索引拦下，不建索引则两条记录共用一个身份、互相覆盖。
     */
    @Test
    fun migrate3To4_givesEachRecordItsOwnIdentity() {
        helper.createDatabase(TEST_DATABASE, 3).use { database ->
            repeat(3) { index ->
                database.execSQL(
                    "INSERT INTO `scan_records` " +
                        "(`productName`, `productionDate`, `expiryDate`, `shelfLife`, `imageName`, `savedAt`) " +
                        "VALUES ('记录$index', '', '', '', '', $index)"
                )
            }
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, MIGRATION_3_4)

        migrated.query("SELECT COUNT(*), COUNT(DISTINCT `syncId`) FROM `scan_records`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals("每条记录各有一个身份", 3, cursor.getInt(1))
        }
    }

    /** 对端表要一并建出来，否则升级后第一次打开同步页就崩在「找不到表」上 */
    @Test
    fun migrate3To4_createsThePeersTable() {
        helper.createDatabase(TEST_DATABASE, 3).use { }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, MIGRATION_3_4)

        migrated.query(
            "SELECT `name` FROM `sqlite_master` WHERE `type` = 'table' AND `name` = 'sync_peers'"
        ).use { cursor ->
            assertTrue("sync_peers 必须存在", cursor.moveToFirst())
        }
    }

    /**
     * 老记录必须原样活下来，位置这一列读出来是空串——它们那时没有这一项。
     *
     * 本次迁移没有重建表（只 ALTER TABLE 加一列），所以这一遍校验同时也在回答那个问题：
     * 带 DEFAULT 的子句会不会让 Room 拿实体对不上。对比它是唯一说得上数的，注释不算。
     */
    @Test
    fun migrate4To5_keepsRecordsAndLeavesLocationEmpty() {
        helper.createDatabase(TEST_DATABASE, 4).use { database ->
            database.execSQL(
                "INSERT INTO `scan_records` " +
                    "(`productName`, `productionDate`, `expiryDate`, `shelfLife`, `imageName`, " +
                    "`savedAt`, `syncId`, `updatedAt`, `updatedBy`, `deletedAt`) " +
                    "VALUES ('纯牛奶', '2026-01-01', '2026-12-31', '12个月', 'old.jpg', " +
                    "111, 'sid-1', 111, 'dev-a', 0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 5, true, MIGRATION_4_5)

        migrated.query("SELECT `productName`, `location`, `imageName` FROM `scan_records`").use { cursor ->
            assertTrue("老记录不能被迁移弄丢", cursor.moveToFirst())
            assertEquals("纯牛奶", cursor.getString(0))
            assertEquals("那时没有位置这一项，读出来是空串", "", cursor.getString(1))
            assertEquals("其余字段不受影响", "old.jpg", cursor.getString(2))
        }
    }

    /**
     * 老记录那一张图要变成列表里的第一张：它是这条记录的代表图，丢了界面就少一张照片。
     *
     * 这一次重建了表（列既改名又改类型），所以结构校验同时回答那个问题：新表结构与实体对不对得上。
     */
    @Test
    fun migrate5To6_movesTheSingleImageIntoTheList() {
        helper.createDatabase(TEST_DATABASE, 5).use { database ->
            database.execSQL(
                "INSERT INTO `scan_records` " +
                    "(`productName`, `productionDate`, `expiryDate`, `shelfLife`, `location`, `imageName`, " +
                    "`savedAt`, `syncId`, `updatedAt`, `updatedBy`, `deletedAt`) " +
                    "VALUES ('纯牛奶', '2026-01-01', '2026-12-31', '12个月', '冰箱', 'old.jpg', " +
                    "111, 'sid-5-1', 111, 'dev-a', 0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 6, true, MIGRATION_5_6)

        migrated.query("SELECT `productName`, `location`, `imageNames` FROM `scan_records`").use { cursor ->
            assertTrue("老记录不能被迁移弄丢", cursor.moveToFirst())
            assertEquals("纯牛奶", cursor.getString(0))
            assertEquals("位置要原样留着", "冰箱", cursor.getString(1))
            assertEquals("那张图要成为列表里的第一张", "old.jpg", cursor.getString(2))
        }
    }

    /** 本来就没图的记录要落成空文本——那是空列表，不是一根空名字 */
    @Test
    fun migrate5To6_leavesRecordsWithoutImageEmpty() {
        helper.createDatabase(TEST_DATABASE, 5).use { database ->
            database.execSQL(
                "INSERT INTO `scan_records` " +
                    "(`productName`, `productionDate`, `expiryDate`, `shelfLife`, `location`, `imageName`, " +
                    "`savedAt`, `syncId`, `updatedAt`, `updatedBy`, `deletedAt`) " +
                    "VALUES ('没有图', '', '', '', '', '', 222, 'sid-5-2', 222, 'dev-a', 0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 6, true, MIGRATION_5_6)

        migrated.query("SELECT `imageNames` FROM `scan_records`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("没有图就是空列表", "", cursor.getString(0))
        }
    }

    private companion object {
        /**
         * 测试专用的库名。共用一个名字时，上一个用例留下的文件会让 createDatabase 直接抛异常，
         * 而报错内容（「库已存在」）和真正要验的东西毫无关系。
         */
        const val TEST_DATABASE = "migration_test.db"
    }}
