package com.workshop.mechanicbb1.data.sync

import TaskModel
import android.content.Context
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity
import com.workshop.mechanicbb1.data.model.TaskLogModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * کمکی برای ساخت رکوردهای Outbox
 */
object OutboxHelper {

    /**
     * ثبت یه action "addTaskLog" توی Outbox
     */
    suspend fun enqueueAddTaskLog(context: Context, log: TaskLogModel) {
        val payload = JSONObject().apply {
            put("id", log.id)              // ← ID کلاینت
            put("taskId", log.taskId)
            put("userId", log.userId)
            put("userName", log.userName)
            put("date", log.date)
            put("startTime", log.startTime)
            put("endTime", log.endTime)
            put("actionDescription", log.actionDescription)
            put("assignedUsers", log.assignedUsers)
            put("newStatus", log.newStatus)
            put("notes", log.notes)
        }

        val entity = OutboxEntity(
            action = "addTaskLog",
            payload = payload.toString(),
            targetId = log.taskId,
            expectedVersion = -1,   // برای addTaskLog معنی نداره
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    /**
     * ثبت یه action "updateTaskLog" توی Outbox
     */
    suspend fun enqueueUpdateTaskLog(context: Context, log: TaskLogModel) {
        val payload = JSONObject().apply {
            put("logId", log.id)
            put("date", log.date)
            put("startTime", log.startTime)
            put("endTime", log.endTime)
            put("actionDescription", log.actionDescription)
            put("assignedUsers", log.assignedUsers)
            put("newStatus", log.newStatus)
            put("notes", log.notes)
            put("heatLevel", log.heatLevel)
            put("pollutionLevel", log.pollutionLevel)
            put("workType", log.workType)
            put("physicalDifficulty", log.physicalDifficulty)
            put("technicalComplexity", log.technicalComplexity)
        }

        val entity = OutboxEntity(
            action = "updateTaskLog",
            payload = payload.toString(),
            targetId = log.id,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    /**
     * ثبت یه action "updateTaskStatus" توی Outbox
     */
    suspend fun enqueueUpdateTaskStatus(context: Context, taskId: String, status: String) {
        val payload = JSONObject().apply {
            put("taskId", taskId)
            put("status", status)
        }

        val entity = OutboxEntity(
            action = "updateTaskStatus",
            payload = payload.toString(),
            targetId = taskId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueUpdateEvaluation(
        context: Context,
        logId: String,
        notes: String,
        heatLevel: Int,
        pollutionLevel: Int,
        workType: String,
        physicalDifficulty: Int,
        technicalComplexity: Int
    ) {
        val payload = JSONObject().apply {
            put("logId", logId)
            put("notes", notes)
            put("heatLevel", heatLevel)
            put("pollutionLevel", pollutionLevel)
            put("workType", workType)
            put("physicalDifficulty", physicalDifficulty)
            put("technicalComplexity", technicalComplexity)
        }

        val entity = OutboxEntity(
            action = "updateEvaluation",
            payload = payload.toString(),
            targetId = logId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueDeleteTaskLog(context: Context, logId: String) {
        val payload = JSONObject().apply {
            put("logId", logId)
        }

        val entity = OutboxEntity(
            action = "deleteTaskLog",
            payload = payload.toString(),
            targetId = logId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueDeleteTask(context: Context, taskId: String) {
        val payload = JSONObject().apply {
            put("taskId", taskId)
        }

        val entity = OutboxEntity(
            action = "deleteTask",
            payload = payload.toString(),
            targetId = taskId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueUpdateTask(context: Context, task: TaskModel) {
        val payload = JSONObject().apply {
            put("id", task.id)
            put("title", task.title)
            put("description", task.description)
            put("unit", task.unit)
            put("sub_unit", task.sub_unit)
            put("declaration_method", task.declaration_method)
            put("requester", task.requester)
            put("request_date", task.request_date)
            put("priority", task.priority)
            put("urgency", task.urgency)
            put("initial_review", task.initial_review)
            put("system_request_number", task.system_request_number)
        }

        val entity = OutboxEntity(
            action = "updateTask",
            payload = payload.toString(),
            targetId = task.id,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }
}