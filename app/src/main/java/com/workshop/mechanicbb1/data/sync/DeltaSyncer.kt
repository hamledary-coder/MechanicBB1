package com.workshop.mechanicbb1.data.sync

import TaskModel
import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
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

/**
 * Delta Sync — فقط تغییرات از آخرین sync رو می‌گیره
 */
object DeltaSyncer {

    private const val TAG = "DeltaSyncer"
    private const val PREF_NAME = "delta_sync"
    private const val PREF_KEY_LAST_SYNC = "last_sync_time"
    private const val DEFAULT_SINCE = "1970-01-01 00:00:00"

    /**
     * سینک دلتا از سرور
     */
    fun syncDelta(context: Context, onComplete: ((SyncResult) -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastSync = prefs.getString(PREF_KEY_LAST_SYNC, DEFAULT_SINCE) ?: DEFAULT_SINCE

        Log.d(TAG, "Starting delta sync since: $lastSync")
        Log.d("DEBUG_SYNC", "syncDelta started, lastSync = $lastSync")

        val url = "${Config.BASE_URL}?action=getDelta&since=${java.net.URLEncoder.encode(lastSync, "UTF-8")}"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)

                    val tasksArray = json.optJSONArray("tasks") ?: JSONArray()
                    val reportsArray = json.optJSONArray("reports") ?: JSONArray()
                    val deletedArray = json.optJSONArray("deleted") ?: JSONArray()
                    val serverTime = json.optString("server_time", lastSync)

                    Log.d(TAG, "Delta: ${tasksArray.length()} tasks, ${reportsArray.length()} reports, ${deletedArray.length()} deleted")

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)

                            // ۱. کارها
                            val taskEntities = mutableListOf<TaskEntity>()
                            for (i in 0 until tasksArray.length()) {
                                val obj = tasksArray.getJSONObject(i)
                                val task = parseTask(obj)

                                // ← جدید
                                val existing = db.taskDao().getByIdOnce(task.id)
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
                                // ← مهم: اگه گزارش pending توی Room هست، overwrite نکن
                                val existing = db.reportDao().getById(report.id)
                                if (existing == null || existing.syncStatus != "pending") {
                                    reportEntities.add(ReportMapper.toEntity(report, "synced"))
                                }
                            }
                            if (reportEntities.isNotEmpty()) {
                                db.reportDao().insertAll(reportEntities)
                            }

                            // ۳. حذف‌شده‌ها
                            val deletedTaskIds = mutableListOf<String>()
                            val deletedReportIds = mutableListOf<String>()

                            for (i in 0 until deletedArray.length()) {
                                val obj = deletedArray.getJSONObject(i)
                                val tableName = obj.optString("table_name", "")
                                val recordId = obj.optString("record_id", "")

                                when (tableName) {
                                    "tasks" -> deletedTaskIds.add(recordId)
                                    "task_logs" -> deletedReportIds.add(recordId)
                                }
                            }

                            if (deletedTaskIds.isNotEmpty()) {
                                Log.d("DEBUG_SYNC", "deleted taskIds: $deletedTaskIds")
                                db.taskDao().deleteByIds(deletedTaskIds)
                                Log.d(TAG, "Deleted ${deletedTaskIds.size} tasks from Room")
                            }
                            if (deletedReportIds.isNotEmpty()) {
                                db.reportDao().deleteSyncedByIds(deletedReportIds)
                                Log.d(TAG, "Deleted ${deletedReportIds.size} reports from Room (only synced)")
                            }

                            // ۴. ذخیره‌ی server_time به عنوان lastSync جدید
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
                            Log.e(TAG, "Error saving delta: ${e.message}")
                            onComplete?.invoke(SyncResult(0, 0, 0))
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing delta: ${e.message}")
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

    /**
     * ریست lastSync (مثلاً برای full sync)
     */
    fun resetLastSync(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_KEY_LAST_SYNC)
            .apply()
    }

    private fun parseTask(obj: JSONObject): TaskModel {
        val hasUnseenReport = obj.optInt("has_unseen_report", 0) == 1
        return TaskModel(
            id = obj.getString("id"),
            createDate = obj.optString("createDate", ""),
            title = obj.optString("title", ""),
            description = obj.optString("description", ""),
            creator = obj.optString("creator", ""),
            status = obj.optString("status", ""),
            assignedTo = obj.optString("assignedTo", ""),
            responsible = obj.optString("responsible", ""),
            pendingInvites = obj.optString("pendingInvites", ""),
            unit = obj.optString("unit", ""),
            priority = obj.optInt("priority", 50),
            sub_unit = obj.optString("sub_unit", ""),
            declaration_method = obj.optString("declaration_method", ""),
            requester = obj.optString("requester", ""),
            request_date = obj.optString("request_date", ""),
            urgency = obj.optString("urgency", ""),
            initial_review = obj.optString("initial_review", ""),
            system_request_number = obj.optString("system_request_number", ""),
            referredBy = obj.optString("referred_by", ""),
            hasUnseenReport = hasUnseenReport
        )
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

    data class SyncResult(
        val tasksCount: Int,
        val reportsCount: Int,
        val deletedCount: Int
    )
}