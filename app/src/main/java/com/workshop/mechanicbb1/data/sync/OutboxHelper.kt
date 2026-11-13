package com.workshop.mechanicbb1.data.sync

import TaskModel
import android.content.Context
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity
import com.workshop.mechanicbb1.data.local.entity.TaskEstimateEntity
import com.workshop.mechanicbb1.data.local.entity.TaskLogWorkOrderProgressEntity
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * کمکی برای ساخت رکوردهای Outbox
 */
object OutboxHelper {

    // ============================================
    // TaskLog
    // ============================================

    suspend fun enqueueAddTaskLog(
        context: Context,
        log: TaskLogModel,
        workOrderProgresses: List<TaskLogWorkOrderProgressEntity> = emptyList()
    ) {
        val payload = JSONObject().apply {
            put("id", log.id)
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

            // ✅ جدید
            if (workOrderProgresses.isNotEmpty()) {
                val progressesArray = org.json.JSONArray()
                workOrderProgresses.forEach { p ->
                    val pObj = JSONObject().apply {
                        put("workOrderId", p.workOrderId)
                        put("progress", p.progress)
                        put("hasDetails", p.hasDetails)
                        put("detailsNotes", p.detailsNotes)
                        put("nextAction", p.nextAction)
                    }
                    progressesArray.put(pObj)
                }
                put("workOrderProgresses", progressesArray)
            }
        }

        val entity = OutboxEntity(
            action = "addTaskLog",
            payload = payload.toString(),
            targetId = log.taskId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

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
            put("priority", task.priority)
            put("urgency", task.urgency)
            if (task.scheduledDate != null) {
                put("scheduled_date", task.scheduledDate)
            } else {
                put("scheduled_date", JSONObject.NULL)
            }
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

    // ============================================
    // WorkOrder
    // ============================================

    /**
     * ثبت یه action "createWorkOrder" توی Outbox
     */
    suspend fun enqueueCreateWorkOrder(context: Context, workOrder: WorkOrderModel) {
        val payload = JSONObject().apply {
            put("id", workOrder.id)
            put("request_number", workOrder.requestNumber)
            put("title", workOrder.title)
            put("request_class", workOrder.requestClass ?: JSONObject.NULL)
            put("request_date", workOrder.requestDate)
            put("requester", workOrder.requester)
            put("approver", workOrder.approver.ifEmpty { JSONObject.NULL })   // ← جدید
            put("unit", workOrder.unit)
            put("sub_unit", workOrder.subUnit)
            put("equipment_name", workOrder.equipmentName)
            put("description", workOrder.description)
            put("external_status", workOrder.externalStatus ?: "1")   // ← جدید
            put("progress", workOrder.progress)                       // ← جدید
        }

        val entity = OutboxEntity(
            action = "createWorkOrder",
            payload = payload.toString(),
            targetId = workOrder.id,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    /**
     * ثبت یه action "updateWorkOrder" توی Outbox
     */
    suspend fun enqueueUpdateWorkOrder(context: Context, workOrder: WorkOrderModel) {
        val payload = JSONObject().apply {
            put("workOrderId", workOrder.id)
            put("request_number", workOrder.requestNumber)   // ← جدید
            put("title", workOrder.title)
            put("request_class", workOrder.requestClass ?: JSONObject.NULL)
            put("request_date", workOrder.requestDate)
            put("requester", workOrder.requester)
            put("approver", workOrder.approver.ifEmpty { JSONObject.NULL })
            put("unit", workOrder.unit)
            put("sub_unit", workOrder.subUnit)
            put("equipment_name", workOrder.equipmentName)
            put("description", workOrder.description)
            put("external_status", workOrder.externalStatus ?: "1")
            put("progress", workOrder.progress)
        }

        val entity = OutboxEntity(
            action = "updateWorkOrder",
            payload = payload.toString(),
            targetId = workOrder.id,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    /**
     * ثبت یه action "deleteWorkOrder" توی Outbox
     */
    suspend fun enqueueDeleteWorkOrder(context: Context, workOrderId: String) {
        val payload = JSONObject().apply {
            put("workOrderId", workOrderId)
        }

        val entity = OutboxEntity(
            action = "deleteWorkOrder",
            payload = payload.toString(),
            targetId = workOrderId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueCreateTask(
        context: Context,
        task: TaskModel,
        workOrderIds: List<String>
    ) {
        val payload = JSONObject().apply {
            put("id", task.id)
            put("title", task.title)
            put("description", task.description)
            put("creator", task.creator)
            put("status", task.status)
            put("priority", task.priority)
            put("urgency", task.urgency)
            if (task.scheduledDate != null) {
                put("scheduled_date", task.scheduledDate)
            } else {
                put("scheduled_date", JSONObject.NULL)
            }
            put("workOrderIds", org.json.JSONArray(workOrderIds))
        }

        val entity = OutboxEntity(
            action = "createTask",
            payload = payload.toString(),
            targetId = task.id,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueUpdateTaskWorkOrders(
        context: Context,
        taskId: String,
        workOrderIds: List<String>
    ) {
        val payload = JSONObject().apply {
            put("taskId", taskId)
            put("workOrderIds", org.json.JSONArray(workOrderIds))
        }

        val entity = OutboxEntity(
            action = "updateTaskWorkOrders",
            payload = payload.toString(),
            targetId = taskId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    // ============================================
    // TaskEstimate
    // ============================================

    suspend fun enqueueAddEstimate(context: Context, estimate: TaskEstimateEntity) {
        val payload = JSONObject().apply {
            put("id", estimate.id)
            put("taskId", estimate.taskId)
            put("task_id", estimate.taskId)
            put("estimatorId", estimate.estimatorId)
            put("estimator_id", estimate.estimatorId)
            put("estimatorName", estimate.estimatorName)
            put("estimator_name", estimate.estimatorName)
            put("estimatedAt", estimate.estimatedAt)
            put("estimated_at", estimate.estimatedAt)
            put("manpower", estimate.manpower)
            put("parts", estimate.parts)
            put("estimatedHours", estimate.estimatedHours)
            put("estimated_hours", estimate.estimatedHours)
            put("workType", estimate.workType)
            put("work_type", estimate.workType)
            put("heatLevel", estimate.heatLevel)
            put("heat_level", estimate.heatLevel)
            put("pollutionLevel", estimate.pollutionLevel)
            put("pollution_level", estimate.pollutionLevel)
            put("physicalDifficulty", estimate.physicalDifficulty)
            put("physical_difficulty", estimate.physicalDifficulty)
            put("technicalComplexity", estimate.technicalComplexity)
            put("technical_complexity", estimate.technicalComplexity)
            put("notes", estimate.notes)
        }

        val entity = OutboxEntity(
            action = "addEstimate",
            payload = payload.toString(),
            targetId = estimate.id,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueDeleteEstimate(context: Context, estimateId: String) {
        val payload = JSONObject().apply {
            put("estimateId", estimateId)
            put("estimate_id", estimateId)
        }

        val entity = OutboxEntity(
            action = "deleteEstimate",
            payload = payload.toString(),
            targetId = estimateId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }

    suspend fun enqueueDeleteTaskWorkOrderMapping(context: Context, mappingId: String) {
        val payload = JSONObject().apply {
            put("mappingId", mappingId)
        }

        val entity = OutboxEntity(
            action = "deleteTaskWorkOrderMapping",
            payload = payload.toString(),
            targetId = mappingId,
            expectedVersion = -1,
            status = "pending"
        )

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).outboxDao().insert(entity)
        }
    }
}