package com.workshop.mechanicbb1.data.local

import TaskModel
import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.sync.TaskMapper
import com.workshop.mechanicbb1.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskLocalRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    fun getTasksForTab(tabName: String, userId: String, userRole: String): Flow<List<TaskModel>> {
        val flow = when (tabName.trim()) {
            "myTasks" -> db.taskDao().getMyInProgress(userId)
            "today" -> {
                val today = DateUtils.gregorianToShamsi()
                db.taskDao().getTodayTasks(today)
            }
            "queue" -> db.taskDao().getQueueTasks()
            else -> db.taskDao().getMyInProgress(userId)
        }
        return flow.map { entities -> entities.map { TaskMapper.toModel(it) } }
    }

    fun getMyInProgress(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getMyInProgress(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    fun getMyBlocked(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getMyBlocked(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    fun getMyCompleted(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getMyCompleted(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    fun getTodayTasks(): Flow<List<TaskModel>> {
        val today = DateUtils.gregorianToShamsi()
        return db.taskDao().getTodayTasks(today).map { it.map { e -> TaskMapper.toModel(e) } }
    }

    fun getQueueTasks(): Flow<List<TaskModel>> =
        db.taskDao().getQueueTasks().map { it.map { e -> TaskMapper.toModel(e) } }

    fun getTaskById(taskId: String): Flow<TaskModel?> {
        return db.taskDao().getByIdFlow(taskId).map { entity ->
            entity?.let { TaskMapper.toModel(it) }
        }
    }

    fun getResponsibleTasks(userId: String): Flow<List<TaskModel>> {
        return db.taskDao().getResponsibleTasks(userId).map { entities ->
            entities.map { TaskMapper.toModel(it) }
        }
    }

    suspend fun updateTaskStatus(taskId: String, newStatus: String) {
        val entity = db.taskDao().getById(taskId) ?: return
        db.taskDao().update(entity.copy(status = newStatus))
    }

    suspend fun deleteTaskLocally(taskId: String) {
        val task = db.taskDao().getById(taskId) ?: return

        // ۱. soft delete Task
        db.taskDao().update(task.copy(isDeleted = true))

        // ۲. حذف Reportها (TaskLog)
        val reports = db.reportDao().getByTaskOnce(taskId)
        reports.forEach { report ->
            // ۲.۱. حذف Progressهای هر گزارش
            db.taskLogWorkOrderProgressDao().deleteByLog(report.id)
        }
        db.reportDao().deleteAllByTaskId(taskId)

        // ۳. حذف Mappingها (Task ↔ WorkOrder)
        db.taskWorkOrderMappingDao().deleteByTask(taskId)

        // ۴. حذف Outboxهای pending (که به این Task اشاره دارن)
        db.outboxDao().deleteByTargetId(taskId)
    }

    suspend fun updateTaskFromEdit(updatedTask: TaskModel) {
        val existing = db.taskDao().getById(updatedTask.id) ?: return
        db.taskDao().update(
            existing.copy(
                title = updatedTask.title,
                description = updatedTask.description,
                priority = updatedTask.priority,
                urgency = updatedTask.urgency,
                scheduledDate = updatedTask.scheduledDate
            )
        )
    }

    fun getArchivedTasks(): Flow<List<TaskModel>> {
        return db.taskDao().getArchived().map { entities ->
            entities.map { TaskMapper.toModel(it) }
        }
    }

    fun getAllBlocked(): Flow<List<TaskModel>> =
        db.taskDao().getAllBlocked().map { it.map { e -> TaskMapper.toModel(e) } }

    fun getAllCompleted(): Flow<List<TaskModel>> =
        db.taskDao().getAllCompleted().map { it.map { e -> TaskMapper.toModel(e) } }

    fun getWorkOrdersForTask(taskId: String): Flow<List<WorkOrderModel>> {
        return db.taskWorkOrderMappingDao().getByTask(taskId).map { mappings ->
            mappings.mapNotNull { mapping ->
                db.workOrderDao().getById(mapping.workOrderId)?.let { entity ->
                    WorkOrderModel(
                        id = entity.id,
                        requestNumber = entity.requestNumber,
                        title = entity.title,
                        requestClass = entity.requestClass,
                        requestDate = entity.requestDate,
                        requester = entity.requester,
                        approver = entity.approver ?: "",
                        unit = entity.unit,
                        subUnit = entity.subUnit,
                        equipmentName = entity.equipmentName,
                        description = entity.description,
                        externalStatus = entity.externalStatus,
                        progress = entity.progress,
                        source = entity.source
                    )
                }
            }
        }
    }

    suspend fun insertTaskDirect(task: TaskEntity) {
        db.taskDao().insert(task)
    }

    suspend fun insertMappingDirect(mapping: TaskWorkOrderMappingEntity) {
        db.taskWorkOrderMappingDao().insert(mapping)
    }

    suspend fun generateTempTaskId(userId: String): String {
        val maxId = db.taskDao().getMaxNumericId() ?: 1000
        return "tmp-${userId}-${maxId + 1}"
    }
}