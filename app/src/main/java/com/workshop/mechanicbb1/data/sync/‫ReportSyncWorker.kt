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
            val success = sendToServer(item)

            if (success) {
                // موفق → done
                db.outboxDao().update(item.copy(status = "done"))

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
    private fun sendToServer(item: OutboxEntity): Boolean {
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
                        "updateTaskPriority" -> "${Config.BASE_URL}?action=updateTaskPriority"
                        "createWorkOrder" -> "${Config.BASE_URL}?action=createWorkOrder"
                        "updateWorkOrder" -> "${Config.BASE_URL}?action=updateWorkOrder"
                        else -> return false
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

            (response.isSuccessful && responseBody.contains("success")) || response.code == 409

        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            false
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