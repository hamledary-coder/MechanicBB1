package com.workshop.mechanicbb1.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.repository.WorkOrderLocalRepository
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.notifications.NotificationDisplayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.RequestBody.Companion.toRequestBody
import android.annotation.SuppressLint

@Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ReportSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ReportSyncWorker"
        private const val WORK_NAME = "ReportSyncWork"
        private const val PERIODIC_WORK_NAME = "ReportSyncPeriodicWork"

        /** یک‌باره — برای بعد از هر تغییر (فوری) */
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

        /** ✅ دوره‌ای — برای سینک خودکار در پس‌زمینه (هر ۱۵ دقیقه) */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReportSyncWorker>(
                15, TimeUnit.MINUTES   // حداقل مجاز اندروید
            )
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
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,   // اگه قبلاً ثبت شده، دوباره ثبت نکن
                    request
                )
        }
    }

    // ✅ data class برای response
    private data class SyncResult(
        val success: Boolean,
        val responseBody: String = "",
        val httpCode: Int = 0
    )

    @SuppressLint("MissingPermission")
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
                // ✅ موفق → done
                db.outboxDao().update(item.copy(status = "done"))

                // ✅ createTask → آپدیت id موقت
                if (item.action == "createTask") {
                    updateTempIdAfterCreate(db, item, result.responseBody)
                }

                // ✅ آپدیت گزارش به synced
                if (item.action == "addTaskLog") {
                    val reportId = extractReportId(item)
                    if (reportId != null) {
                        markReportSynced(db, reportId)
                    }
                }

                // ✅ آپدیت WorkOrder در Room (همه‌ی قبلی‌ها حفظ)
                when (item.action) {
                    "createWorkOrder" -> {
                        try {
                            val response = JSONObject(result.responseBody)
                            val realWoId = response.optString("id", "")
                            val clientId = response.optString("clientId", "")
                            val normalized = response.optBoolean("normalized", false)

                            if (normalized && clientId.isNotEmpty() && realWoId != clientId) {
                                Log.d(TAG, "WorkOrder id normalized: $clientId → $realWoId")

                                // ۱. آپدیت mapping و progress (Room)
                                db.taskWorkOrderMappingDao().updateWorkOrderId(clientId, realWoId)
                                db.taskLogWorkOrderProgressDao().updateWorkOrderId(clientId, realWoId)

                                // ۲. حذف WorkOrder قدیمی (Room)
                                db.workOrderDao().deleteById(clientId)
                            }

                            // ۳. sync WorkOrder (جدید یا همون id) از سرور
                            WorkOrderSyncer.singleSyncForWorker(applicationContext, realWoId) { ok ->
                                Log.d(TAG, "WorkOrder synced: $ok")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Error: ${e.message}")
                        }
                    }
                    "updateWorkOrder" -> {
                        try {
                            val response = JSONObject(result.responseBody)
                            val finalWoId = response.optString("id", "")
                            val previousId = response.optString("previousId", "")
                            val normalized = response.optBoolean("normalized", false)

                            if (normalized && previousId.isNotEmpty() && finalWoId != previousId) {
                                Log.d(TAG, "WorkOrder id normalized: $previousId → $finalWoId")

                                // ۱. آپدیت mapping و progress (Room)
                                db.taskWorkOrderMappingDao().updateWorkOrderId(previousId, finalWoId)
                                db.taskLogWorkOrderProgressDao().updateWorkOrderId(previousId, finalWoId)

                                // ۲. حذف WorkOrder قدیمی (Room)
                                db.workOrderDao().deleteById(previousId)
                            }

                            // ۳. sync WorkOrder (جدید یا همون id) از سرور
                            WorkOrderSyncer.singleSyncForWorker(applicationContext, finalWoId) { ok ->
                                Log.d(TAG, "WorkOrder synced: $ok")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating WorkOrder: ${e.message}")
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

                // ✅ آپدیت برآورد به synced
                if (item.action == "addEstimate") {
                    markEstimateSynced(db, item.targetId)
                }

                Log.d(TAG, "✅ ${item.action} - ${item.targetId} sent")

            } else if (result.httpCode == 409) {
                // ✅ 409 → مدیریت تداخل
                Log.w(TAG, "⚠️ 409 Conflict for ${item.action} - ${item.targetId}")

                when (item.action) {
                    "createWorkOrder" -> {
                        handleWorkOrderConflict(db, item, result.responseBody)
                    }
                    "updateWorkOrder" -> {
                        handleWorkOrderUpdateConflict(db, item, result.responseBody)
                    }
                    else -> {
                        // بقیه: 409 = done (مثل قبل)
                        db.outboxDao().update(item.copy(status = "done"))
                    }
                }

            } else {
                // ✅ ناموفق → retry
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

        // ✅ پاک کردن doneها
        db.outboxDao().clearDone()

        return if (hasFailure) Result.retry() else Result.success()
    }

    /**
     * ✅ مدیریت تداخل (409) برای createWorkOrder
     */

    private suspend fun handleWorkOrderConflict(
        db: AppDatabase,
        item: OutboxEntity,
        responseBody: String
    ) {
        try {
            val response = JSONObject(responseBody)
            val existingId = response.optString("existingId", "")
            val clientId = response.optString("clientId", "")

            Log.d(TAG, "WorkOrder conflict: clientId=$clientId, existingId=$existingId")

            // ← حذف WorkOrder لوکال (چون تکراری)
            if (clientId.isNotEmpty()) {
                db.workOrderDao().deleteById(clientId)
            }

            // ← Outbox → failed (نه done، چون کاربر باید شماره رو عوض کنه)
            db.outboxDao().update(item.copy(status = "failed"))

            // ← Notification: «شماره تکراری، عوض کن»
            sendConflictNotification(
                applicationContext,
                "شماره درخواست تکراری",
                "شماره‌ی $existingId قبلاً ثبت شده. لطفاً شماره‌ی دیگه‌ای انتخاب کنید"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error handling conflict: ${e.message}")
            db.outboxDao().update(item.copy(status = "failed"))
        }
    }

    /**
     * ✅ مدیریت تداخل (409) برای updateWorkOrder
     */

    private suspend fun handleWorkOrderUpdateConflict(
        db: AppDatabase,
        item: OutboxEntity,
        responseBody: String
    ) {
        try {
            val response = JSONObject(responseBody)
            val existingId = response.optString("existingId", "")
            val workOrderId = response.optString("workOrderId", "")

            Log.d(TAG, "Update conflict: workOrderId=$workOrderId, existingId=$existingId")

            // ← WorkOrder لوکال (شماره‌ی قدیمی) → برگردون (sync از سرور)
            WorkOrderSyncer.singleSyncForWorker(applicationContext, workOrderId) { ok ->
                Log.d(TAG, "WorkOrder synced: $ok")
            }

            // ← Outbox → failed (نه done، چون کاربر باید شماره رو عوض کنه)
            db.outboxDao().update(item.copy(status = "failed"))

            // ← Notification: «شماره تکراری، عوض کن»
            sendConflictNotification(
                applicationContext,
                "شماره درخواست تکراری",
                "شماره‌ی جدید ($existingId) قبلاً ثبت شده. لطفاً شماره‌ی دیگه‌ای انتخاب کنید"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error handling update conflict: ${e.message}")
            db.outboxDao().update(item.copy(status = "failed"))
        }
    }

    /**
     * ✅ ارسال نوتیفیکیشن تداخل
     */

    private fun sendConflictNotification(context: Context, title: String, message: String) {
        try {
            NotificationDisplayManager.showSimpleNotification(context, title, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification: ${e.message}")
        }
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
                "deleteEstimate" -> {
                    val estimateId = JSONObject(item.payload).optString("estimateId", "")
                    "${Config.BASE_URL}?action=deleteEstimate&estimateId=$estimateId" to null
                }
                "deleteTaskWorkOrderMapping" -> {
                    val mappingId = JSONObject(item.payload).optString("mappingId", "")
                    "${Config.BASE_URL}?action=deleteTaskWorkOrderMapping&mappingId=$mappingId" to null
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
                        "addEstimate" -> "${Config.BASE_URL}?action=addEstimate"
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
                success = (response.isSuccessful && responseBody.contains("success")),
                responseBody = responseBody,
                httpCode = response.code
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

            // ۵. TaskEstimateEntity (اگه هست)
            val estimates = db.taskEstimateDao().getByTaskOnce(tempId)
            if (estimates.isNotEmpty()) {
                estimates.forEach { e ->
                    db.taskEstimateDao().deleteById(e.id)
                    db.taskEstimateDao().insert(
                        e.copy(
                            id = "est_${realId}_${e.id.substringAfterLast("_")}",
                            taskId = realId
                        )
                    )
                }
                Log.d(TAG, "Estimates updated: ${estimates.size} items")
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

    private suspend fun markEstimateSynced(
        db: AppDatabase,
        estimateId: String
    ) {
        val estimate = db.taskEstimateDao().getById(estimateId) ?: return
        db.taskEstimateDao().update(
            estimate.copy(
                syncStatus = "synced",
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }
}