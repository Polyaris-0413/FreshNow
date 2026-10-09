package com.freshnow.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4：加进局域网同步要用的四列与对端表。
 *
 * scan_records 是**重建**的，不是 ALTER TABLE ADD COLUMN。SQLite 加一个非空列必须带 DEFAULT 子句，
 * 而那个 DEFAULT 会写进表结构本身，Room 打开库时拿它与实体比对（实体并没有声明默认值）就会报
 * 「迁移后结构不一致」，每次启动都失败。重建表能让表结构完全由实体说了算。
 *
 * 老记录的 syncId 在迁移时现生成：随机 128 位，两台设备各生成各的，撞不上。
 * updatedAt 回填成 savedAt——这些记录从建好之后没被改过，修改时刻就是保存时刻。
 * updatedBy 回填空串：那时还没有设备号，空串在 LWW 里会输给任何真设备号，正好表示「来历不明」，
 * 而本条第一次被编辑时就会带上真设备号（见 ScanRecordRepository）。
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
