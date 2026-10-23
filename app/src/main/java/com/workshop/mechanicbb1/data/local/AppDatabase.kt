package com.workshop.mechanicbb1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.workshop.mechanicbb1.data.local.dao.CoefficientDao
import com.workshop.mechanicbb1.data.local.dao.OutboxDao
import com.workshop.mechanicbb1.data.local.dao.ReportDao
import com.workshop.mechanicbb1.data.local.dao.TaskDao
import com.workshop.mechanicbb1.data.local.dao.UserDao
import com.workshop.mechanicbb1.data.local.entity.CoefficientEntity
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity
import com.workshop.mechanicbb1.data.local.entity.ReportEntity
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import com.workshop.mechanicbb1.data.local.entity.UserEntity

@Database(
    entities = [
        TaskEntity::class,
        ReportEntity::class,
        UserEntity::class,
        OutboxEntity::class,
        CoefficientEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun reportDao(): ReportDao
    abstract fun userDao(): UserDao
    abstract fun outboxDao(): OutboxDao
    abstract fun coefficientDao(): CoefficientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mechanic_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}