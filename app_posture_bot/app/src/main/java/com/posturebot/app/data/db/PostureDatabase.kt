package com.posturebot.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PostureSample::class, Session::class],
    version = 1,
    exportSchema = false
)
abstract class PostureDatabase : RoomDatabase() {
    abstract fun postureDao(): PostureDao
}

object PostureDbProvider {
    fun create(context: Context): PostureDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            PostureDatabase::class.java,
            "posture_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}
