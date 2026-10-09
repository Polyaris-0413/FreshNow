package com.freshnow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.freshnow.app.BuildConfig

// 改表结构时必须补一条 Migration，并把 KSP 导出的 schemas/ 目录一并提交：
// 只有留下每个版本的表结构，才核对得出迁移前后是否一致、也才写得出正确的迁移语句。
// 开发期改表频繁，debug 构建允许破坏性迁移（见 getInstance）；
// release 构建在缺少迁移路径时直接抛异常中止，而不是悄悄清空用户已存的扫描记录。
@Database(entities = [ScanRecord::class, SyncPeer::class], version = 4, exportSchema = true)
abstract class FreshNowDatabase : RoomDatabase() {

    abstract fun scanRecordDao(): ScanRecordDao

    abstract fun syncPeerDao(): SyncPeerDao

    companion object {
        private const val DATABASE_NAME = "freshnow.db"

        @Volatile
        private var instance: FreshNowDatabase? = null

        fun getInstance(context: Context): FreshNowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FreshNowDatabase::class.java,
                    DATABASE_NAME
                )
                    .apply {
                        addMigrations(MIGRATION_3_4)
                        // 仅开发期：改表后丢表重建，省掉每改一列就写一条迁移。
                        // 有迁移路径时走迁移，这个开关只在没有路径时兜底。
                        // release 刻意不启用——用户升级时若没有迁移路径，宁可当场报错暴露问题，
                        // 也不能用清空扫描记录的方式「解决」
                        if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true)
                    }
                    .build()
                    .also { instance = it }
            }
    }
}
