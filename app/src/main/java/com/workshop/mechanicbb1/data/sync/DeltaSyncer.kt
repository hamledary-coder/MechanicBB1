package com.workshop.mechanicbb1.data.sync

import TaskModel
import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object DeltaSyncer {

    private const val TAG = "DeltaSyncer"
    private const val PREF_NAME = "delta_sync"
    private const val PREF_KEY_LAST_SYNC = "last_sync_time"
    private const val DEFAULT_SINCE = "1970-01-01 00:00:00"

    fun syncDelta(context: Context, onComplete: ((SyncResult) -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastSync = prefs.getString(PREF_KEY_LAST_SYNC, DEFAULT_SINCE) ?: DEFAULT_SINCE

        Log.d(TAG, "Starting delta sync since: $lastSync")

        val url = "${Config.BASE_URL}?action=getDelta&since=${java.net.URLEncoder.encode(lastSync, "UTF-8")}"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)

                    val tasksArray = json.optJSONArray("tasks") ?: JSONArray()
                    val reportsArray = json.optJSONArray("reports") ?: JSONArray()
                    val deletedArray = json.optJSONArray("deleted") ?: JSONArray()
                    val workOrdersArray = json.optJSONArray("work_orders") ?: JSONArray()
                    val mappingsArray = json.optJSONArray("task_work_order_mappings") ?: JSONArray()
                    val serverTime = json.optString("server_time", lastSync)
+
                    Log.d(TAG, "Delta: ${tasksArray.length()} tasks, ${reportsArray.length()} reports, " +
                            "${workOrdersArray.length()} work_orders, ${mappingsArray.length()} mappings, " +
                            "${deletedArray.length()} deleted")

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)

                            // ۱. کارها
                            val taskEntities = mutableListOf<TaskEntity>()
                            for (i in 0 until tasksArray.length()) {
                                val obj = tasksArray.getJSONObject(i)
                                val task = parseTask(obj)
                                val existing = db.taskDao().getById(task.id)
                                if (existing?.isDeleted == true) {
                                    Log.d(TAG, "Skipping deleted task: ${task.id}")
                                    continue
                                }
                                taskEntities.add(TaskMapper.toEntity(task))
                            }
                            if (taskEntities.isNotEmpty()) {
                                db.taskDao().insertAll(taskEntities)
                            }

                            // ۲. گزارش‌ها
                            val reportEntities = mutableListOf<com.workshop.mechanicbb1.data.local.entity.ReportEntity>()
                            for (i in 0 until reportsArray.length()) {
                                val obj = reportsArray.getJSONObject(i)
                                val report = parseReport(obj)
                                val existing = db.reportDao().getById(report.id)
                                if (existing == null || existing.syncStatus != "pending") {
                                    reportEntities.add(ReportMapper.toEntity(report, "synced"))
                                }
                            }
                            if (reportEntities.isNotEmpty()) {
                                db.reportDao().insertAll(reportEntities)
                            }

                            // ✅ ۳. WorkOrderها
                            val workOrderEntities = mutableListOf<WorkOrderEntity>()
                            for (i in 0 until workOrdersArray.length()) {
                                val obj = workOrdersArray.getJSONObject(i)
                                workOrderEntities.add(WorkOrderSyncer.parseEntity(obj))
                            }
                            if (workOrderEntities.isNotEmpty()) {
                                db.workOrderDao().insertAll(workOrderEntities)
                                Log.d(TAG, "Saved ${workOrderEntities.size} work_orders")
                            }

                            // ✅ ۴. Mappingها
                            val mappingEntities = mutableListOf<TaskWorkOrderMappingEntity>()
                            for (i in 0 until mappingsArray.length()) {
                                val obj = mappingsArray.getJSONObject(i)
                                mappingEntities.add(
                                    TaskWorkOrderMappingEntity(
                                        id = obj.optString("id", ""),
                                        taskId = obj.optString("task_id", ""),
                                        workOrderId = obj.optString("work_order_id", ""),
                                        createdAt = parseDateTime(obj.optString("created_at", ""))
                                    )
                                )
                            }
                            if (mappingEntities.isNotEmpty()) {
                                db.taskWorkOrderMappingDao().insertAll(mappingEntities)
                                Log.d(TAG, "Saved ${mappingEntities.size} mappings")
                            }

                            // ۵. حذف‌شده‌ها
                            val deletedTaskIds = mutableListOf<String>()
                            val deletedReportIds = mutableListOf<String>()
                            val deletedMappingIds = mutableListOf<String>()

                            for (i in 0 until deletedArray.length()) {
                                val obj = deletedArray.getJSONObject(i)
                                val tableName = obj.optString("table_name", "")
                                val recordId = obj.optString("record_id", "")

                                when (tableName) {
                                    "tasks" -> deletedTaskIds.add(recordId)
                                    "task_logs" -> deletedReportIds.add(recordId)
                                    "task_work_order_mapping" -> deletedMappingIds.add(recordId)   // ✅ جدید
                                }
                            }

                            if (deletedTaskIds.isNotEmpty()) {
                                db.taskDao().deleteByIds(deletedTaskIds)
                                Log.d(TAG, "Deleted ${deletedTaskIds.size} tasks")
                            }
                            if (deletedReportIds.isNotEmpty()) {
                                db.reportDao().deleteSyncedByIds(deletedReportIds)
                                Log.d(TAG, "Deleted ${deletedReportIds.size} reports")
                            }
                            if (deletedMappingIds.isNotEmpty()) {
                                db.taskWorkOrderMappingDao().deleteByIds(deletedMappingIds)   // ✅ جدید
                                Log.d(TAG, "Deleted ${deletedMappingIds.size} mappings")
                            }

                            // ۶. ذخیره server_time
                            prefs.edit()
                                .putString(PREF_KEY_LAST_SYNC, serverTime)
                                .apply()

                            Log.d(TAG, "Delta sync complete. New lastSync: $serverTime")

                            onComplete?.invoke(
                                SyncResult(
                                    tasksCount = taskEntities.size,
                                    reportsCount = reportEntities.size,
                                    deletedCount = deletedTaskIds.size + deletedReportIds.size
                                )
                            )

                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving delta: ${e.message}", e)
                            onComplete?.invoke(SyncResult(0, 0, 0))
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing delta: ${e.message}", e)
                    onComplete?.invoke(SyncResult(0, 0, 0))
                }
            },
            { error ->
                Log.e(TAG, "Network error: ${error.message}")
                onComplete?.invoke(SyncResult(0, 0, 0))
            }
        )

        VolleySingleton.getInstance(context).add(request)
    }

    fun resetLastSync(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_KEY_LAST_SYNC)
            .apply()
    }

    private fun parseTask(obj: JSONObject): TaskModel {
        return TaskModel(
            id = obj.optString("id", ""),
            createDate = obj.optString("createDate", ""),
            title = obj.optString("title", ""),
            description = obj.optString("description", ""),
            creator = obj.optString("creator", ""),
            status = obj.optString("status", ""),
            assignedTo = obj.optString("assignedTo", ""),
            responsible = obj.optString("responsible", ""),
            priority = obj.optInt("priority", 50),
            urgency = obj.optString("urgency", ""),
            referredBy = obj.optString("referred_by", ""),
            hasUnseenReport = obj.optInt("has_unseen_report", 0) == 1,
            scheduledDate = parseNullableString(obj, "scheduled_date")   // ✅
        )
    }

    private fun parseNullableString(obj: JSONObject, key: String): String? {
        if (obj.isNull(key)) return null
        val value = obj.optString(key, "")
        return value.takeIf { it.isNotEmpty() && it != "null" && it != "NULL" }
    }

    private fun parseReport(obj: JSONObject): TaskLogModel {
        val seenBy = mutableListOf<String>()
        val seenByStr = obj.optString("seen_by", "[]")
        if (seenByStr.isNotEmpty() && seenByStr != "[]") {
            try {
                val seenByArray = JSONArray(seenByStr)
                for (j in 0 until seenByArray.length()) {
                    seenBy.add(seenByArray.getString(j))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing seen_by: ${e.message}")
            }
        }

        return TaskLogModel(
            id = obj.getString("id"),
            taskId = obj.getString("task_id"),
            userId = obj.getString("user_id"),
            userName = obj.optString("user_name", ""),
            date = obj.optString("date", ""),
            startTime = obj.optString("start_time", ""),
            endTime = obj.optString("end_time", ""),
            actionDescription = obj.optString("action_description", ""),
            assignedUsers = obj.optString("assigned_users", ""),
            newStatus = obj.optString("new_status", ""),
            attachments = "",
            notes = obj.optString("notes", ""),
            duration = "",
            comments = obj.optString("comments", "[]"),
            heatLevel = obj.optInt("heat_level", 30),
            pollutionLevel = obj.optInt("pollution_level", 0),
            workType = obj.optString("work_type", "fixed_equipment"),
            taskUrgency = obj.optString("task_urgency", "عادی"),
            seenBy = seenBy,
            physicalDifficulty = obj.optInt("physical_difficulty", 0),
            technicalComplexity = obj.optInt("technical_complexity", 0)
        )
    }

    private fun parseDateTime(dateStr: String): Long {
        if (dateStr.isEmpty()) return System.currentTimeMillis()
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    data class SyncResult(
        val tasksCount: Int,
        val reportsCount: Int,
        val deletedCount: Int
    )
}