package com.freshnow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// TODO(发版前必须处理): exportSchema 改回 true，并把生成的 schemas/ 目录提交进版本控制。
// 现在设成 false 只是为了让开发期改表不产生 schema 文件。若拖到发版之后才发现，
// 改表结构就只剩两条路：破坏性迁移（清空用户已存的扫描记录），或凭记忆手写迁移语句。
@Database(entities = [ScanRecord::class], version = 1, exportSchema = false)
abstract class FreshNowDatabase : RoomDatabase() {

    abstract fun scanRecordDao(): ScanRecordDao

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
                ).build().also { instance = it }
            }
    }
}
