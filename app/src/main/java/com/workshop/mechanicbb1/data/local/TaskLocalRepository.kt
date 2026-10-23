package com.workshop.mechanicbb1.data.local

import TaskModel
import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.TaskMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * لایه‌ی بین WorkListFragment و Room
 * فقط خوندن — نوشتن رو TaskSyncer انجام می‌ده
 */
class TaskLocalRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    /**
     * خروجی برای تب‌های مختلف کارتابل
     */
    fun getTasksForTab(
        tabName: String,
        userId: String,
        userRole: String
    ): Flow<List<TaskModel>> {

        val flow = when (tabName.trim()) {
            "اقدام نشده" -> db.taskDao().getUnassigned()

            "در حال انجام" -> db.taskDao().getInProgress()

            "کارتابل من" -> {
                when (userRole) {
                    Config.RoleCode.EMPLOYEE ->
                        db.taskDao().getMyCartableForEmployee(userId)
                    Config.RoleCode.SUPERVISOR,
                    Config.RoleCode.MANAGER ->
                        db.taskDao().getMyCartableForSupervisor(userId)
                    else ->
                        db.taskDao().getMyCartableForEmployee(userId)
                }
            }

            else -> db.taskDao().getUnassigned()
        }

        // تبدیل entity به model
        return flow.map { entities ->
            entities.map { TaskMapper.toModel(it) }
        }
    }

    /**
     * خروجی Flow برای یه کار خاص (برای TaskDetailActivity)
     */
    fun getTaskById(taskId: String): Flow<TaskModel?> {
        return db.taskDao().getByIdFlow(taskId).map { entity ->
            entity?.let { TaskMapper.toModel(it) }
        }
    }

    /**
     * برای HomeActivity — چک می‌کنه کاربر مسئول کاری هست یا نه
     */
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
        Log.d("DEBUG_DELETE", "1. deleteTaskLocally called: $taskId")

        val task = db.taskDao().getById(taskId) ?: run {
            Log.e("DEBUG_DELETE", "2. task not found!")
            return
        }

        Log.d("DEBUG_DELETE", "2. task found. isDeleted before = ${task.isDeleted}")

        db.taskDao().update(task.copy(isDeleted = true))

        Log.d("DEBUG_DELETE", "3. updated. isDeleted should be true now")

        // چک فوری
        val check = db.taskDao().getById(taskId)
        Log.d("DEBUG_DELETE", "4. right after update: isDeleted = ${check?.isDeleted}")

        db.reportDao().deleteAllByTaskId(taskId)
        db.outboxDao().deleteByTargetId(taskId)

        Log.d("DEBUG_DELETE", "5. deleteTaskLocally done")
    }

    suspend fun updateTaskFromEdit(updatedTask: TaskModel) {
        val existing = db.taskDao().getById(updatedTask.id) ?: return
        db.taskDao().update(
            existing.copy(
                title = updatedTask.title,
                description = updatedTask.description,
                unit = updatedTask.unit,
                subUnit = updatedTask.sub_unit,
                declarationMethod = updatedTask.declaration_method,
                requester = updatedTask.requester,
                requestDate = updatedTask.request_date,
                priority = updatedTask.priority,
                urgency = updatedTask.urgency,
                initialReview = updatedTask.initial_review,
                systemRequestNumber = updatedTask.system_request_number
            )
        )
    }

    fun getArchivedTasks(): Flow<List<TaskModel>> {
        return db.taskDao().getArchived().map { entities ->
            entities.map { TaskMapper.toModel(it) }
        }
    }

}