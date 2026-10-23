package com.freshnow.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4：加进局域网同步要用的四列与对端表。
 * scan_records 是**重建**的，不是 ALTER TABLE ADD COLUMN：带 DEFAULT 的非空列会让 Room 比对表结构与
 * 实体时报「迁移后结构不一致」，重建才能让表结构完全由实体说了算。
 * 老记录的 syncId 迁移时现生成（随机 128 位）；updatedAt 回填 savedAt；updatedBy 回填空串表示「来历不明」，
 * 第一次被编辑时才带上真设备号（见 ScanRecordRepository）。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scan_records_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`productName` TEXT NOT NULL, " +
                "`productionDate` TEXT NOT NULL, " +
                "`expiryDate` TEXT NOT NULL, " +
                "`shelfLife` TEXT NOT NULL, " +
                "`imageName` TEXT NOT NULL, " +
                "`savedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`updatedBy` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `scan_records_new` (" +
                "`id`, `productName`, `productionDate`, `expiryDate`, `shelfLife`, `imageName`, " +
                "`savedAt`, `syncId`, `updatedAt`, `updatedBy`, `deletedAt`) " +
                "SELECT `id`, `productName`, `productionDate`, `expiryDate`, `shelfLife`, `imageName`, " +
                "`savedAt`, lower(hex(randomblob(16))), `savedAt`, '', 0 FROM `scan_records`"
        )
        db.execSQL("DROP TABLE `scan_records`")
        db.execSQL("ALTER TABLE `scan_records_new` RENAME TO `scan_records`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_scan_records_syncId` " +
                "ON `scan_records` (`syncId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_peers` (" +
                "`deviceId` TEXT NOT NULL, " +
                "`deviceName` TEXT NOT NULL, " +
                "`secret` TEXT NOT NULL, " +
                "`lastAddress` TEXT NOT NULL, " +
                "`pairedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`deviceId`))"
        )
    }
}

/**
 * v4 → v5：加「位置」一列。
 * 这一次用 ALTER TABLE，不重建表——加列本身不需要重建。表结构与实体对不对得上，以迁移测试为准
 * （见 [ScanRecordMigrationTest.migrate4To5_keepsRecordsAndLeavesLocationEmpty]）。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `scan_records` ADD COLUMN `location` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v5 → v6：照片从一根名字变成一串名字。
 * 与 3 → 4 一样重建表：列既改名又改类型，重建才能让表结构完全由实体说了算。
 * 老记录那张图搬成列表里的第一张（它本来就是这条记录的代表图）。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scan_records_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`productName` TEXT NOT NULL, " +
                "`productionDate` TEXT NOT NULL, " +
                "`expiryDate` TEXT NOT NULL, " +
                "`shelfLife` TEXT NOT NULL, " +
                "`location` TEXT NOT NULL, " +
                "`imageNames` TEXT NOT NULL, " +
                "`savedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`updatedBy` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `scan_records_new` (" +
                "`id`, `productName`, `productionDate`, `expiryDate`, `shelfLife`, `location`, " +
                "`imageNames`, `savedAt`, `syncId`, `updatedAt`, `updatedBy`, `deletedAt`) " +
                "SELECT `id`, `productName`, `productionDate`, `expiryDate`, `shelfLife`, `location`, " +
                "`imageName`, `savedAt`, `syncId`, `updatedAt`, `updatedBy`, `deletedAt` FROM `scan_records`"
        )
        db.execSQL("DROP TABLE `scan_records`")
        db.execSQL("ALTER TABLE `scan_records_new` RENAME TO `scan_records`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_scan_records_syncId` " +
                "ON `scan_records` (`syncId`)"
        )
    }
}