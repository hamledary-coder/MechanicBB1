package com.workshop.mechanicbb1.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.WorkOrderLocalRepository
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity
import com.workshop.mechanicbb1.data.remote.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.RequestBody.Companion.toRequestBody

@Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ReportSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ReportSyncWorker"
        private const val WORK_NAME = "ReportSyncWork"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReportSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }

    // ✅ data class برای response
    private data class SyncResult(
        val success: Boolean,
        val responseBody: String = ""
    )

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== doWork started ===")

        val db = AppDatabase.getInstance(applicationContext)
        val pending = db.outboxDao().getPending()

        Log.d(TAG, "Pending items: ${pending.size}")

        if (pending.isEmpty()) {
            return Result.success()
        }

        var hasFailure = false

        for (item in pending) {
            val result = sendToServer(item)

            if (result.success) {
                // موفق → done
                db.outboxDao().update(item.copy(status = "done"))

                // ✅ createTask → آپدیت id موقت
                if (item.action == "createTask") {
                    updateTempIdAfterCreate(db, item, result.responseBody)
                }

                // آپدیت گزارش به synced
                if (item.action == "addTaskLog") {
                    val reportId = extractReportId(item)
                    if (reportId != null) {
                        markReportSynced(db, reportId)
                    }
                }

                // آپدیت WorkOrder در Room
                when (item.action) {
                    "createWorkOrder", "updateWorkOrder" -> {
                        WorkOrderSyncer.syncSingleWorkOrder(
                            applicationContext,
                            item.targetId
                        ) { ok ->
                            Log.d(TAG, "WorkOrder ${item.targetId} synced: $ok")
                        }
                    }
                    "deleteWorkOrder" -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val repo = WorkOrderLocalRepository(applicationContext)
                                repo.deleteWorkOrder(item.targetId)
                                Log.d(TAG, "WorkOrder ${item.targetId} deleted from Room")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting WorkOrder: ${e.message}")
                            }
                        }
                    }
                }

                Log.d(TAG, "✅ ${item.action} - ${item.targetId} sent")
            } else {
                // ناموفق → retry
                hasFailure = true
                db.outboxDao().update(
                    item.copy(
                        status = "pending",
                        retryCount = item.retryCount + 1,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
                Log.e(TAG, "❌ ${item.action} - ${item.targetId} failed")
            }
        }

        // پاک کردن doneها
        db.outboxDao().clearDone()

        return if (hasFailure) Result.retry() else Result.success()
    }

    /**
     * ارسال یه آیتم به سرور
     */
    private fun sendToServer(item: OutboxEntity): SyncResult {
        return try {
            val (url, body) = when (item.action) {
                // ===== Delete (GET) =====
                "deleteTask" -> {
                    val taskId = JSONObject(item.payload).optString("taskId", "")
                    "${Config.BASE_URL}?action=delete&id=$taskId" to null
                }
                "deleteTaskLog" -> {
                    val logId = JSONObject(item.payload).optString("logId", "")
                    "${Config.BASE_URL}?action=deleteTaskLog&logId=$logId" to null
                }
                "deleteWorkOrder" -> {
                    val woId = JSONObject(item.payload).optString("workOrderId", "")
                    "${Config.BASE_URL}?action=deleteWorkOrder&workOrderId=$woId" to null
                }

                // ===== Create/Update (POST) =====
                else -> {
                    val postUrl = when (item.action) {
                        "addTaskLog" -> "${Config.BASE_URL}?action=addTaskLog"
                        "updateTaskLog" -> "${Config.BASE_URL}?action=updateTaskLog"
                        "updateTaskStatus" -> "${Config.BASE_URL}?action=updateTaskStatus"
                        "updateEvaluation" -> "${Config.BASE_URL}?action=updateEvaluation"
                        "updateTask" -> "${Config.BASE_URL}?action=update"
                        "createTask" -> "${Config.BASE_URL}?action=createTask"
                        "updateTaskWorkOrders" -> "${Config.BASE_URL}?action=updateTaskWorkOrders"
                        "updateTaskPriority" -> "${Config.BASE_URL}?action=updateTaskPriority"
                        "createWorkOrder" -> "${Config.BASE_URL}?action=createWorkOrder"
                        "updateWorkOrder" -> "${Config.BASE_URL}?action=updateWorkOrder"
                        else -> return SyncResult(false)
                    }
                    postUrl to item.payload.toRequestBody("application/json; charset=utf-8".toMediaType())
                }
            }

            val client = OkHttpClient()
            val request = if (body == null) {
                Request.Builder().url(url).get().build()
            } else {
                Request.Builder().url(url).post(body).build()
            }

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Response: ${response.code} - $responseBody")

            SyncResult(
                success = (response.isSuccessful && responseBody.contains("success")) || response.code == 409,
                responseBody = responseBody
            )

        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            SyncResult(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            SyncResult(false)
        }
    }

    /**
     * ✅ آپدیت id موقت (tmp-...) به id واقعی از سرور
     */
    private suspend fun updateTempIdAfterCreate(
        db: AppDatabase,
        item: OutboxEntity,
        responseBody: String
    ) {
        try {
            val payload = JSONObject(item.payload)
            val tempId = payload.optString("id", "")
            if (!tempId.startsWith("tmp-")) return

            val response = JSONObject(responseBody)
            val realId = response.optString("id", "")
            if (realId.isEmpty() || realId == tempId) return

            Log.d(TAG, "Updating temp id: $tempId → $realId")

            // ۱. TaskEntity
            val task = db.taskDao().getById(tempId)
            if (task != null) {
                db.taskDao().deleteByIds(listOf(tempId))
                db.taskDao().insert(task.copy(id = realId))
                Log.d(TAG, "Task updated: $tempId → $realId")
            }

            // ۲. MappingEntity
            val mappings = db.taskWorkOrderMappingDao().getByTaskOnce(tempId)
            if (mappings.isNotEmpty()) {
                db.taskWorkOrderMappingDao().deleteByTask(tempId)
                mappings.forEach { m ->
                    db.taskWorkOrderMappingDao().insert(
                        m.copy(
                            id = "m_${realId}_${m.workOrderId}",
                            taskId = realId
                        )
                    )
                }
                Log.d(TAG, "Mappings updated: ${mappings.size} items")
            }

            // ۳. ReportEntity (اگه هست)
            val reports = db.reportDao().getByTaskOnce(tempId)
            if (reports.isNotEmpty()) {
                reports.forEach { r ->
                    db.reportDao().deleteById(r.id)
                    db.reportDao().insert(
                        r.copy(
                            id = "${realId}-${r.id.substringAfterLast("-")}",
                            taskId = realId
                        )
                    )
                }
                Log.d(TAG, "Reports updated: ${reports.size} items")
            }

            // ۴. Outboxهای pending (اگه هستن)
            val pendingOutbox = db.outboxDao().getPending()
            pendingOutbox.forEach { o ->
                if (o.targetId == tempId || o.payload.contains(tempId)) {
                    db.outboxDao().update(
                        o.copy(
                            targetId = if (o.targetId == tempId) realId else o.targetId,
                            payload = o.payload.replace(tempId, realId)
                        )
                    )
                }
            }

            Log.d(TAG, "✅ Temp id updated: $tempId → $realId")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating temp id: ${e.message}", e)
        }
    }

    private fun extractReportId(item: OutboxEntity): String? {
        return try {
            val json = JSONObject(item.payload)
            val key = when (item.action) {
                "addTaskLog" -> "id"
                "updateEvaluation" -> "logId"
                else -> return null
            }
            json.optString(key, "").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun markReportSynced(
        db: AppDatabase,
        reportId: String
    ) {
        val report = db.reportDao().getById(reportId) ?: return
        db.reportDao().update(
            report.copy(
                syncStatus = "synced",
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }
}