package com.workshop.mechanicbb1.data.local.repository

import android.content.Context
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.TaskEstimateEntity
import com.workshop.mechanicbb1.data.sync.EstimateMapper
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class TaskEstimateLocalRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    // ============================================================
    // خواندن
    // ============================================================

    /** همه‌ی برآوردهای یه Task (Flow) */
    fun getEstimatesForTask(taskId: String): Flow<List<TaskEstimateEntity>> {
        return db.taskEstimateDao().getByTask(taskId)
    }

    /** آخرین برآورد یه Task (Flow) — برای UI */
    fun getLatestForTaskFlow(taskId: String): Flow<TaskEstimateEntity?> {
        return db.taskEstimateDao().getLatestForTaskFlow(taskId)
    }

    /** آخرین برآورد یه Task (suspend) — برای sync */
    suspend fun getLatestForTask(taskId: String): TaskEstimateEntity? {
        return db.taskEstimateDao().getLatestForTask(taskId)
    }

    /** یه برآورد خاص */
    suspend fun getById(id: String): TaskEstimateEntity? {
        return db.taskEstimateDao().getById(id)
    }

    /** همه‌ی برآوردهای یه Task (suspend) — برای temp ID update */
    suspend fun getByTaskOnce(taskId: String): List<TaskEstimateEntity> {
        return db.taskEstimateDao().getByTaskOnce(taskId)
    }

    // ============================================================
    // نوشتن
    // ============================================================

    /**
     * ثبت برآورد جدید (آفلاین-فرست)
     * @return برآورد ساخته‌شده
     */
    suspend fun addEstimate(
        taskId: String,
        manpower: Int,
        parts: Int,
        estimatedHours: Int,
        workType: String,
        heatLevel: Int,
        pollutionLevel: Int,
        physicalDifficulty: Int,
        technicalComplexity: Int,
        notes: String,
        estimatorId: String,
        estimatorName: String
    ): TaskEstimateEntity {

        val newId = generateEstimateId(taskId)

        val estimate = TaskEstimateEntity(
            id = newId,
            taskId = taskId,
            estimatorId = estimatorId,
            estimatorName = estimatorName,
            estimatedAt = DateUtils.gregorianToShamsi(),
            manpower = manpower,
            parts = parts,
            estimatedHours = estimatedHours,
            workType = workType,
            heatLevel = heatLevel,
            pollutionLevel = pollutionLevel,
            physicalDifficulty = physicalDifficulty,
            technicalComplexity = technicalComplexity,
            notes = notes,
            syncStatus = "pending",
            localCreatedAt = System.currentTimeMillis(),
            lastSyncedAt = 0L
        )

        // ۱. Room
        db.taskEstimateDao().insert(estimate)

        // ۲. Outbox
        OutboxHelper.enqueueAddEstimate(context, estimate)

        // ۳. Worker
        ReportSyncWorker.enqueue(context)

        return estimate
    }

    /**
     * حذف برآورد
     * @return true اگه synced بود (نیاز به Outbox)، false اگه pending بود
     */
    suspend fun deleteEstimate(estimateId: String): Boolean {
        val entity = db.taskEstimateDao().getById(estimateId) ?: return false
        val wasSynced = entity.syncStatus == "synced"

        // ۱. حذف از Room
        db.taskEstimateDao().deleteById(estimateId)

        // ۲. اگه pending بود، Outboxهای مربوطه رو هم پاک کن
        if (!wasSynced) {
            db.outboxDao().deleteByTargetId(estimateId)
        }

        return wasSynced
    }

    // ============================================================
    // Sync helpers
    // ============================================================

    /** آپدیت وضعیت sync به synced (بعد از ارسال موفق) */
    suspend fun markAsSynced(estimateId: String) {
        val entity = db.taskEstimateDao().getById(estimateId) ?: return
        db.taskEstimateDao().update(
            entity.copy(
                syncStatus = "synced",
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    /** ذخیره‌ی برآوردهای دریافتی از سرور (Delta) */
    suspend fun saveFromServer(estimates: List<TaskEstimateEntity>) {
        db.taskEstimateDao().insertAll(estimates)
    }

    /** آپدیت temp ID بعد از createTask (برای برآوردهای آفلاین) */
    suspend fun updateTaskIdAfterCreate(oldTaskId: String, newTaskId: String) {
        val estimates = db.taskEstimateDao().getByTaskOnce(oldTaskId)
        if (estimates.isEmpty()) return

        estimates.forEach { e ->
            db.taskEstimateDao().deleteById(e.id)
            db.taskEstimateDao().insert(
                e.copy(
                    id = "est_${newTaskId}_${e.id.substringAfterLast("_")}",
                    taskId = newTaskId
                )
            )
        }
    }

    // ============================================================
    // Helper
    // ============================================================

    /**
     * تولید id بعدی برای یه Task
     * مثال: اگه آخرین id "est_123_2" باشه → "est_123_3"
     */
    private suspend fun generateEstimateId(taskId: String): String {
        val prefix = "est_${taskId}_"
        val maxSeq = db.taskEstimateDao().getMaxSequenceForTask(taskId, prefix) ?: 0
        return "${prefix}${maxSeq + 1}"
    }
}