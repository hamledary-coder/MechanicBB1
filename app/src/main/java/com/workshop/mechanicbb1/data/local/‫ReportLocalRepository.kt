package com.workshop.mechanicbb1.data.local

import android.content.Context
import com.workshop.mechanicbb1.data.local.entity.ReportEntity
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.sync.ReportMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * لایه‌ی بین UI و Room برای گزارش‌ها
 */
class ReportLocalRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    /**
     * خروجی Flow برای یه کار خاص (برای TaskDetailActivity)
     */
    fun getReportsForTask(taskId: String): Flow<List<TaskLogModel>> {
        return db.reportDao().getByTask(taskId).map { entities ->
            entities.map { ReportMapper.toModel(it) }
        }
    }

    /**
     * گرفتن یه گزارش خاص
     */
    suspend fun getReportById(id: String): TaskLogModel? {
        return db.reportDao().getById(id)?.let { ReportMapper.toModel(it) }
    }

    /**
     * گرفتن گزارش‌های pending (برای Worker)
     */
    suspend fun getPendingReports(): List<ReportEntity> {
        return db.reportDao().getPendingReports()
    }

    /**
     * ذخیره‌ی گزارش (از UI، آفلاین)
     */
    suspend fun saveReportLocally(report: TaskLogModel) {
        val entity = ReportMapper.toEntity(report, syncStatus = "pending")
        db.reportDao().insert(entity)
    }

    /**
     * حذف آفلاین گزارش
     * @return true اگه گزارش synced بود (نیاز به Outbox)، false اگه pending بود
     */
    suspend fun deleteReportLocally(reportId: String): Boolean {
        val entity = db.reportDao().getById(reportId) ?: return false
        val wasSynced = entity.syncStatus == "synced"

        // حذف از Room
        db.reportDao().deleteById(reportId)

        // اگه pending بود، Outbox های مربوطه رو هم پاک کن
        if (!wasSynced) {
            db.outboxDao().deleteByTargetId(reportId)
        }

        return wasSynced
    }

    /**
     * ذخیره‌ی گزارش‌ها از سرور (بعد از sync)
     */
    suspend fun saveReportsFromServer(reports: List<TaskLogModel>) {
        val entities = reports.map { ReportMapper.toEntity(it, syncStatus = "synced") }
        db.reportDao().insertAll(entities)
    }

    /**
     * آپدیت وضعیت sync بعد از ارسال موفق
     */
    suspend fun markAsSynced(reportId: String) {
        val entity = db.reportDao().getById(reportId) ?: return
        db.reportDao().update(
            entity.copy(
                syncStatus = "synced",
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * آپدیت وضعیت sync بعد از خطا
     */
    suspend fun markAsFailed(reportId: String, error: String) {
        val entity = db.reportDao().getById(reportId) ?: return
        db.reportDao().update(
            entity.copy(
                syncStatus = "failed",
                lastSyncedAt = 0L
            )
        )
    }

    /**
     * محاسبه‌ی ID بعدی برای یه کار
     * مثال: اگه آخرین گزارش "123-2" باشه، برمی‌گردونه "123-3"
     */
    suspend fun generateNextReportId(taskId: String): String {
        val existing = db.reportDao().getByTaskOnce(taskId)

        if (existing.isEmpty()) {
            return "$taskId-1"
        }

        // بزرگترین شماره رو پیدا کن
        val maxNumber = existing.mapNotNull { report ->
            val parts = report.id.split("-")
            if (parts.size >= 2) parts.last().toIntOrNull() else null
        }.maxOrNull() ?: 0

        return "$taskId-${maxNumber + 1}"
    }

    /**
     * آپدیت ارزیابی یه گزارش (آفلاین)
     */
    suspend fun updateReportEvaluation(
        reportId: String,
        notes: String,
        heatLevel: Int,
        pollutionLevel: Int,
        workType: String,
        physicalDifficulty: Int,
        technicalComplexity: Int
    ) {
        val entity = db.reportDao().getById(reportId) ?: return
        db.reportDao().update(
            entity.copy(
                notes = notes,
                heatLevel = heatLevel,
                pollutionLevel = pollutionLevel,
                workType = workType,
                physicalDifficulty = physicalDifficulty,
                technicalComplexity = technicalComplexity
            )
        )
    }
}