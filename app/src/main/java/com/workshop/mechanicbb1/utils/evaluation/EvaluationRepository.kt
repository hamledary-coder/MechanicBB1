package com.workshop.mechanicbb1.utils.evaluation

import TaskModel
import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.sync.ReportMapper
import com.workshop.mechanicbb1.data.sync.TaskMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * مخزن داده‌های ماژول ارزیابی — آفلاین‌فرست
 */
class EvaluationRepository(private val context: Context) {

    private val TAG = "EvaluationRepository"
    private val db = AppDatabase.getInstance(context)

    fun getAllTaskLogs(callback: (List<TaskLogModel>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entities = db.reportDao().getAllOnce()
                val logs = entities.map { ReportMapper.toModel(it) }
                withContext(Dispatchers.Main) {
                    callback(logs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    fun getAllTasks(callback: (List<TaskModel>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entities = db.taskDao().getAllOnce()
                val tasks = entities.map { TaskMapper.toModel(it) }
                withContext(Dispatchers.Main) {
                    callback(tasks)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    fun getTasks(taskIds: List<String>, callback: (List<TaskModel>) -> Unit) {
        if (taskIds.isEmpty()) {
            callback(emptyList())
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entities = db.taskDao().getByIds(taskIds)
                val tasks = entities.map { TaskMapper.toModel(it) }
                withContext(Dispatchers.Main) {
                    callback(tasks)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    fun getTaskLogs(taskId: String, callback: (List<TaskLogModel>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entities = db.reportDao().getByTaskOnce(taskId)
                val logs = entities.map { ReportMapper.toModel(it) }
                withContext(Dispatchers.Main) {
                    callback(logs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }
}