package com.workshop.mechanicbb1.data.sync

import TaskModel
import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray


/**
 * مسئول sync کارها از سرور به Room
 * فقط خواندن — چیزی به سرور نمی‌فرسته
 */
object TaskSyncer {

    private const val TAG = "TaskSyncer"

    /**
     * کارها رو از سرور می‌گیره و توی Room می‌ریزه
     * @param onComplete با (تعداد taskهای sync شده) صدا زده می‌شه
     */
    fun syncTasksFromServer(
        context: Context,
        userId: String = "",
        onComplete: ((Int) -> Unit)? = null
    ) {
        DeltaSyncer.syncDelta(context) { result ->
            Log.d(TAG, "Delta sync: ${result.tasksCount} tasks, ${result.reportsCount} reports, ${result.deletedCount} deleted")

            // ← کاربران هم sync شن
            UserSyncer.syncUsersFromServer(context) { userCount ->
                Log.d(TAG, "Synced $userCount users")
                onComplete?.invoke(result.tasksCount)
            }
        }
    }

    /**
     * چند کار خاص رو از سرور می‌گیره و تو Room ذخیره می‌کنه
     * @param taskIds لیست ID کارهایی که باید sync بشن
     * @param onComplete با (تعداد taskهای sync شده) صدا زده می‌شه
     */
    fun syncSpecificTasks(
        context: Context,
        taskIds: List<String>,
        onComplete: ((Int) -> Unit)? = null
    ) {
        if (taskIds.isEmpty()) {
            onComplete?.invoke(0)
            return
        }

        val idsParam = taskIds.joinToString(",")
        val url = "${Config.BASE_URL}?action=getTasks&taskIds=$idsParam"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonArray = JSONArray(response)
                    val tasks = mutableListOf<TaskModel>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        tasks.add(parseTask(obj))
                    }

                    // ذخیره تو Room (تو یه Coroutine جداگانه)
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getInstance(context)

                        // ← جدید
                        val filteredEntities = tasks.mapNotNull { task ->
                            // قبل:
// val existing = db.taskDao().getByIdOnce(task.id)

// بعد:
                            val existing = db.taskDao().getById(task.id)
                            if (existing?.isDeleted == true) null
                            else TaskMapper.toEntity(task)
                        }

                        db.taskDao().insertAll(filteredEntities)
                        onComplete?.invoke(filteredEntities.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing specific tasks: ${e.message}")
                    onComplete?.invoke(0)
                }
            },
            { error ->
                Log.e(TAG, "Error fetching specific tasks: ${error.message}")
                onComplete?.invoke(0)
            }
        )

        VolleySingleton.getInstance(context).add(request)
    }

    /**
     * تبدیل JSONObject سرور به TaskModel
     * دقیقاً همون کد fetchTasks توی WorkListFragment
     */
    private fun parseTask(obj: org.json.JSONObject): TaskModel {
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

    private fun parseNullableString(obj: org.json.JSONObject, key: String): String? {
        if (obj.isNull(key)) return null
        val value = obj.optString(key, "")
        return value.takeIf { it.isNotEmpty() && it != "null" && it != "NULL" }
    }
}