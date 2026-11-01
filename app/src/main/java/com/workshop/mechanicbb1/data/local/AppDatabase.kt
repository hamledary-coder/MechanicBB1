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
import com.workshop.mechanicbb1.data.local.dao.TaskEstimateDao
import com.workshop.mechanicbb1.data.local.dao.TaskLogWorkOrderProgressDao
import com.workshop.mechanicbb1.data.local.dao.TaskWorkOrderMappingDao
import com.workshop.mechanicbb1.data.local.dao.UserDao
import com.workshop.mechanicbb1.data.local.dao.WorkOrderDao
import com.workshop.mechanicbb1.data.local.entity.CoefficientEntity
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity
import com.workshop.mechanicbb1.data.local.entity.ReportEntity
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import com.workshop.mechanicbb1.data.local.entity.TaskEstimateEntity
import com.workshop.mechanicbb1.data.local.entity.TaskLogWorkOrderProgressEntity
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import com.workshop.mechanicbb1.data.local.entity.UserEntity
import com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity

@Database(
    entities = [
        TaskEntity::class,
        TaskEstimateEntity::class,
        TaskWorkOrderMappingEntity::class,
        TaskLogWorkOrderProgressEntity::class,
        ReportEntity::class,
        UserEntity::class,
        OutboxEntity::class,
        CoefficientEntity::class,
        WorkOrderEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun taskEstimateDao(): TaskEstimateDao
    abstract fun taskWorkOrderMappingDao(): TaskWorkOrderMappingDao
    abstract fun taskLogWorkOrderProgressDao(): TaskLogWorkOrderProgressDao
    abstract fun reportDao(): ReportDao
    abstract fun userDao(): UserDao
    abstract fun outboxDao(): OutboxDao
    abstract fun coefficientDao(): CoefficientDao
    abstract fun workOrderDao(): WorkOrderDao

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
                        `approver` TEXT,
                        `unit` TEXT NOT NULL,
                        `subUnit` TEXT NOT NULL,
                        `equipmentName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `externalStatus` TEXT,
                        `progress` INTEGER NOT NULL DEFAULT 0,
                        `source` TEXT NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        // ============ Migration 4 → 5 ============
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `work_orders` DROP COLUMN `status`")
                } catch (e: Exception) { }
                try {
                    db.execSQL("ALTER TABLE `work_orders` ADD COLUMN `progress` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) { }
            }
        }

        // ============ Migration 5 → 6 ============
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // empty
            }
        }

        // ============ Migration 6 → 7 ============
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `work_orders` ADD COLUMN `approver` TEXT")
                } catch (e: Exception) { }
            }
        }

        // ============ Migration 7 → 8 ============
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ۱. Rebuild tasks
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tasks_new` (
                        `id` TEXT NOT NULL,
                        `createDate` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `creator` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `assignedTo` TEXT NOT NULL,
                        `responsible` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `urgency` TEXT NOT NULL,
                        `referredBy` TEXT NOT NULL,
                        `seenBy` TEXT NOT NULL,
                        `hasUnseenReport` INTEGER NOT NULL,
                        `scheduledDate` TEXT,
                        `version` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `tasks_new`
                    (id, createDate, title, description, creator, status,
                     assignedTo, responsible, priority, urgency, referredBy,
                     seenBy, hasUnseenReport, scheduledDate, version, lastSyncedAt, isDeleted)
                    SELECT
                     id, createDate, title, description, creator, status,
                     assignedTo, responsible, priority, urgency, referredBy,
                     seenBy, hasUnseenReport, NULL, version, lastSyncedAt, isDeleted
                    FROM `tasks`
                """.trimIndent())

                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_status` ON `tasks`(`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_responsible` ON `tasks`(`responsible`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_scheduledDate` ON `tasks`(`scheduledDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_priority` ON `tasks`(`priority`)")

                // ۲. task_estimates
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_estimates` (
                        `id` TEXT NOT NULL,
                        `taskId` TEXT NOT NULL,
                        `estimatorId` TEXT NOT NULL,
                        `estimatorName` TEXT NOT NULL,
                        `estimatedAt` TEXT NOT NULL,
                        `manpower` INTEGER NOT NULL,
                        `estimatedHours` INTEGER NOT NULL,
                        `workType` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `localCreatedAt` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_estimates_taskId` ON `task_estimates`(`taskId`)")

                // ۳. task_workorder_mappings
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_workorder_mappings` (
                        `id` TEXT NOT NULL,
                        `taskId` TEXT NOT NULL,
                        `workOrderId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_workorder_mappings_taskId` ON `task_workorder_mappings`(`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_workorder_mappings_workOrderId` ON `task_workorder_mappings`(`workOrderId`)")

                // ۴. task_log_workorder_progress
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_log_workorder_progress` (
                        `id` TEXT NOT NULL,
                        `taskLogId` TEXT NOT NULL,
                        `workOrderId` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL,
                        `nextAction` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_log_workorder_progress_taskLogId` ON `task_log_workorder_progress`(`taskLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_log_workorder_progress_workOrderId` ON `task_log_workorder_progress`(`workOrderId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mechanic_db"
                )
                    .addMigrations(
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}