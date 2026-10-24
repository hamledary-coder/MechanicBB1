package com.workshop.mechanicbb1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.workshop.mechanicbb1.data.local.dao.CoefficientDao
import com.workshop.mechanicbb1.data.local.dao.OutboxDao
import com.workshop.mechanicbb1.data.local.dao.ReportDao
import com.workshop.mechanicbb1.data.local.dao.TaskDao
import com.workshop.mechanicbb1.data.local.dao.UserDao
import com.workshop.mechanicbb1.data.local.dao.WorkOrderDao
import com.workshop.mechanicbb1.data.local.entity.CoefficientEntity
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity
import com.workshop.mechanicbb1.data.local.entity.ReportEntity
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import com.workshop.mechanicbb1.data.local.entity.UserEntity
import com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity

@Database(
    entities = [
        TaskEntity::class,
        ReportEntity::class,
        UserEntity::class,
        OutboxEntity::class,
        CoefficientEntity::class,
        WorkOrderEntity::class          // ← جدید
    ],
    version = 4,                        // ← از 3 به 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun reportDao(): ReportDao
    abstract fun userDao(): UserDao
    abstract fun outboxDao(): OutboxDao
    abstract fun coefficientDao(): CoefficientDao
    abstract fun workOrderDao(): WorkOrderDao   // ← جدید

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ============ Migration 3 → 4 ============
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `work_orders` (
                        `id` TEXT NOT NULL,
                        `requestNumber` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `requestClass` TEXT,
                        `requestDate` TEXT NOT NULL,
                        `requester` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `subUnit` TEXT NOT NULL,
                        `equipmentName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `externalStatus` TEXT,
                        `source` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mechanic_db"
                )
                    .addMigrations(MIGRATION_3_4)   // ← اضافه شد
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}