package com.freshnow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
