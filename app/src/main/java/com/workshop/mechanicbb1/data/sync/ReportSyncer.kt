package com.workshop.mechanicbb1.data.sync

import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.AppDatabase
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
 * مسئول sync گزارش‌ها از سرور به Room
 * فقط خواندن — چیزی به سرور نمی‌فرسته
 */
object ReportSyncer {

    private const val TAG = "ReportSyncer"

    /**
     * sync گزارش‌ها از سرور به Room
     * @param statusFilter 'active' | 'archived' | 'all'
     * @param onComplete با (تعداد گزارش‌های sync شده) صدا زده می‌شه
     */
    fun syncReportsFromServer(
        context: Context,
        statusFilter: String = "active",
        onComplete: ((Int) -> Unit)? = null
    ) {
        val url = "${Config.BASE_URL}?action=getAllTaskLogs&statusFilter=$statusFilter"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonArray = JSONArray(response)
                    val reports = mutableListOf<TaskLogModel>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val report = parseReport(obj)
                        reports.add(report)
                    }

                    Log.d(TAG, "Fetched ${reports.size} reports from server (filter=$statusFilter)")

                    // ذخیره در Room
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val entities = reports.map {
                                ReportMapper.toEntity(it, syncStatus = "synced")
                            }
                            db.reportDao().insertAll(entities)
                            Log.d(TAG, "Saved ${entities.size} reports to Room")

                            onComplete?.invoke(entities.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving to Room: ${e.message}")
                            onComplete?.invoke(0)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing: ${e.message}")
                    onComplete?.invoke(0)
                }
            },
            { error ->
                Log.e(TAG, "Network error: ${error.message}")
                onComplete?.invoke(0)
            }
        )

        VolleySingleton.getInstance(context).add(request)
    }

    /**
     * تبدیل JSONObject سرور به TaskLogModel
     */
    private fun parseReport(obj: JSONObject): TaskLogModel {
        // parse seen_by (JSON array → List<String>)
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
}