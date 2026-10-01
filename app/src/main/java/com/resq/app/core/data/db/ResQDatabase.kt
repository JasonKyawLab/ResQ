package com.resq.app.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * version = 1 for v1. A future schema change adds a `Migration` and
 * bumps this number rather than falling back to destructive migration —
 * the receiver log is the one piece of local evidence a rescuer might
 * need to recover after an app update.
 */
@Database(entities = [SosLogEntity::class], version = 1, exportSchema = true)
abstract class ResQDatabase : RoomDatabase() {

    abstract fun sosLogDao(): SosLogDao

    companion object {
        @Volatile
        private var instance: ResQDatabase? = null

        fun getInstance(context: Context): ResQDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ResQDatabase::class.java,
                    "resq.db",
                ).build().also { instance = it }
            }
    }
}
