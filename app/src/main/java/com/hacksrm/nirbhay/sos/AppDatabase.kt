package com.hacksrm.nirbhay.sos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SosEventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sosEventDao(): SosEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nirbhay_db"
                ).build()
                INSTANCE = inst
                inst
            }
        }
    }
}

