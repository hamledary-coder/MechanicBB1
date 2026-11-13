package com.workshop.mechanicbb1.data.local.repository

import TaskModel
import android.content.Context
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.TaskMapper
import com.workshop.mechanicbb1.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * لایه‌ی Repository بین UI و Room برای کارها (tasks).
 *
 * وظایف:
 *  - تبدیل TaskEntity ↔ TaskModel (با TaskMapper)
 *  - گروه‌بندی کوئری‌ها بر اساس نقش (کارمند / سرشیفت)
 *  - مدیریت حذف نرم (soft delete) با پاک‌سازی روابط
 *
 * نکته: متدهای قدیمی (getMyInProgress, getAllBlocked, ...) برای سازگاری نگه داشته شدن
 *        ولی برای کد جدید از متدهای Employee/Supervisor/Team استفاده کن.
 */
@Suppress("DEPRECATION")
class TaskLocalRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    // =========================================================================
    // بخش ۱: تب‌ها (برای WorkListFragment — همه‌ی نقش‌ها)
    // =========================================================================

    /**
     * کارهای یه تب خاص رو برمی‌گردونه.
     * @param tabName "myTasks" | "today" | "queue"
     * @param userId  فعلاً برای fallback استفاده می‌شه (myTasks قدیمی)
     * @param userRole نقش کاربر (فعلاً استفاده نمی‌شه، برای آینده)
     */
    fun getTasksForTab(tabName: String, userId: String, userRole: String): Flow<List<TaskModel>> {
        val flow = when (tabName.trim()) {
            "today" -> {
                val today = DateUtils.gregorianToShamsi()
                db.taskDao().getTodayTasks(today)
            }
            "queue" -> db.taskDao().getQueueTasks()
            else -> db.taskDao().getMyInProgress(userId)   // fallback (myTasks قدیمی)
        }
        return flow.map { entities -> entities.map { TaskMapper.toModel(it) } }
    }

    /** کارهای «امروز» (تب ۲) */
    fun getTodayTasks(): Flow<List<TaskModel>> {
        val today = DateUtils.gregorianToShamsi()
        return db.taskDao().getTodayTasks(today).map { it.map { e -> TaskMapper.toModel(e) } }
    }

    /** کارهای «ارجاع نشده» (تب ۳) */
    fun getQueueTasks(): Flow<List<TaskModel>> =
        db.taskDao().getQueueTasks().map { it.map { e -> TaskMapper.toModel(e) } }

    /** کارهای بایگانی */
    fun getArchivedTasks(): Flow<List<TaskModel>> =
        db.taskDao().getArchived().map { it.map { e -> TaskMapper.toModel(e) } }

    // =========================================================================
    // بخش ۲: یه کار خاص (TaskDetailActivity)
    // =========================================================================

    /** یه کار با ID — Flow */
    fun getTaskById(taskId: String): Flow<TaskModel?> {
        return db.taskDao().getByIdFlow(taskId).map { entity ->
            entity?.let { TaskMapper.toModel(it) }
        }
    }

    // =========================================================================
    // بخش ۳: HomeActivity — کارهای مسئول (QuickLog card)
    // =========================================================================

    /** کارهای مسئول (status=2, 21) — برای کارت QuickLog */
    fun getResponsibleTasks(userId: String): Flow<List<TaskModel>> {
        return db.taskDao().getResponsibleTasks(userId).map { entities ->
            entities.map { TaskMapper.toModel(it) }
        }
    }

    // =========================================================================
    // بخش ۴: «کارهای من» برای کارمند (assignedTo شامل من)
    // =========================================================================

    /** (کارمند) در حال انجام — status=2 */
    fun getEmployeeInProgress(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getEmployeeInProgress(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    /** (کارمند) متوقف — status=3 */
    fun getEmployeeBlocked(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getEmployeeBlocked(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    /** (کارمند) اتمام — status=41 */
    fun getEmployeeCompleted(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getEmployeeCompleted(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    /** (کارمند) منتظر گزارش — status=21, 31 */
    fun getEmployeeAwaitingReport(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getEmployeeAwaitingReport(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    // =========================================================================
    // بخش ۵: «کارهای من» برای سرشیفت/مدیر (تیم + خودم)
    // =========================================================================

    /** (سرشیفت) در حال انجام خودم — status=2 */
    fun getSupervisorMyInProgress(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getSupervisorMyInProgress(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    /** (سرشیفت) متوقف‌های تیم — status=3 */
    fun getTeamBlocked(): Flow<List<TaskModel>> =
        db.taskDao().getTeamBlocked().map { it.map { e -> TaskMapper.toModel(e) } }

    /** (سرشیفت) اتمام‌های تیم (منتظر تأیید) — status=41 */
    fun getTeamCompleted(): Flow<List<TaskModel>> =
        db.taskDao().getTeamCompleted().map { it.map { e -> TaskMapper.toModel(e) } }

    /** (سرشیفت) منتظر گزارش‌های تیم — status=21, 31 */
    fun getTeamAwaitingReport(): Flow<List<TaskModel>> =
        db.taskDao().getTeamAwaitingReport().map { it.map { e -> TaskMapper.toModel(e) } }

    // =========================================================================
    // بخش ۶: عملیات نوشتن (Update/Delete)
    // =========================================================================

    /** آپدیت فقط status — سریع */
    suspend fun updateTaskStatus(taskId: String, newStatus: String) {
        val entity = db.taskDao().getById(taskId) ?: return
        db.taskDao().update(entity.copy(status = newStatus))
    }

    /**
     * آپدیت کار از فرم ویرایش (title, description, priority, urgency, scheduledDate).
     * فقط این ۵ فیلد عوض می‌شن — بقیه دست‌نخورده.
     */
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

    /**
     * حذف نرم کار + پاک‌سازی روابط:
     *   1. soft delete کار
     *   2. حذف گزارش‌ها (TaskLog) و progressهاشون
     *   3. حذف mappingها (WorkOrder)
     *   4. حذف برآوردها
     *   5. حذف Outboxهای pending مربوط به این کار
     */
    suspend fun deleteTaskLocally(taskId: String) {
        val task = db.taskDao().getById(taskId) ?: return

        // ۱. soft delete Task
        db.taskDao().update(task.copy(isDeleted = true))

        // ۲. حذف Reportها + progressهاشون
        val reports = db.reportDao().getByTaskOnce(taskId)
        val reportIds = reports.map { it.id }

        // ← جدید: WorkOrderهای تأثیرپذیر رو جمع کن
        val affectedWorkOrders = mutableSetOf<String>()

        reports.forEach { report ->
            // ← جدید: progressها رو جمع کن
            db.taskLogWorkOrderProgressDao().getByLogOnce(report.id)
                .forEach { affectedWorkOrders.add(it.workOrderId) }

            db.taskLogWorkOrderProgressDao().deleteByLog(report.id)
        }
        db.reportDao().deleteAllByTaskId(taskId)

        // ۳. حذف Mappingها
        db.taskWorkOrderMappingDao().deleteByTask(taskId)

        // ۴. حذف برآوردها
        db.taskEstimateDao().deleteByTask(taskId)

        // ۵. حذف Outboxهای pending
        db.outboxDao().deleteByTargetId(taskId)
        if (reportIds.isNotEmpty()) {
            db.outboxDao().deleteByTargetIds(reportIds)
        }
        db.outboxDao().deleteByTargetIdPrefix("est_${taskId}_")

        // ← جدید: آپدیت work_orders.progress + Outbox
        affectedWorkOrders.forEach { workOrderId ->
            val latestRemaining = db.taskLogWorkOrderProgressDao().getLatestForWorkOrderByDate(workOrderId)
            val newProgress = latestRemaining?.progress ?: 0
            db.workOrderDao().updateProgress(workOrderId, newProgress)

            // ✅ Outbox → سرور (updateWorkOrder)
            val woEntity = db.workOrderDao().getById(workOrderId)
            if (woEntity != null) {
                val woModel = WorkOrderModel(
                    id = woEntity.id,
                    requestNumber = woEntity.requestNumber ?: "",
                    title = woEntity.title,
                    requestClass = woEntity.requestClass,
                    requestDate = woEntity.requestDate,
                    requester = woEntity.requester,
                    approver = woEntity.approver ?: "",
                    unit = woEntity.unit,
                    subUnit = woEntity.subUnit,
                    equipmentName = woEntity.equipmentName,
                    description = woEntity.description,
                    externalStatus = woEntity.externalStatus,
                    progress = newProgress,
                    source = woEntity.source
                )
                OutboxHelper.enqueueUpdateWorkOrder(context, woModel)
            }
        }
    }

    // =========================================================================
    // بخش ۷: WorkOrderهای یه کار (TaskDetailActivity)
    // =========================================================================

    /** WorkOrderهای یه کار (Flow — observe) */
    fun getWorkOrdersForTask(taskId: String): Flow<List<WorkOrderModel>> {
        return combine(
            db.taskWorkOrderMappingDao().getByTask(taskId),
            db.workOrderDao().getAll()
        ) { mappings, allWorkOrders ->
            mappings.mapNotNull { mapping ->
                allWorkOrders.find { it.id == mapping.workOrderId }?.toModel()
            }
        }
    }

    /** WorkOrderهای یه کار (suspend — یک‌باره) */
    suspend fun getWorkOrdersForTaskOnce(taskId: String): List<WorkOrderModel> {
        val mappings = db.taskWorkOrderMappingDao().getByTaskOnce(taskId)
        return mappings.mapNotNull { mapping ->
            db.workOrderDao().getById(mapping.workOrderId)?.toModel()
        }
    }

    // =========================================================================
    // بخش ۸: درج مستقیم (برای CreateTaskActivity — آفلاین)
    // =========================================================================

    /** درج مستقیم یه TaskEntity (بدون Outbox) */
    suspend fun insertTaskDirect(task: TaskEntity) {
        db.taskDao().insert(task)
    }

    /** درج مستقیم یه mapping (بدون Outbox) */
    suspend fun insertMappingDirect(mapping: TaskWorkOrderMappingEntity) {
        db.taskWorkOrderMappingDao().insert(mapping)
    }

    // =========================================================================
    // بخش ۹: تولید temp ID برای createTask
    // =========================================================================

    /**
     * تولید ID موقت برای کار جدید (قبل از sync با سرور).
     * فرمت: "tmp-{userId}-{counter}"
     */
    suspend fun generateTempTaskId(userId: String): String {
        val maxRealId = db.taskDao().getMaxNumericId() ?: 1000
        val pendingCreateCount = db.outboxDao().getPendingCountByAction("createTask")
        return "tmp-${userId}-${maxRealId + pendingCreateCount + 1}"
    }

    // =========================================================================
    // بخش ۱۰: Legacy (کوئری‌های قدیمی — برای سازگاری)
    // =========================================================================
    //
    // ⚠️ این متدها هنوز جای دیگه استفاده می‌شن و حذف نشدن.
    //    اگه مطمئن شدی هیچ‌جا استفاده نمی‌شن، می‌تونی حذف کنی.
    // =========================================================================

    /** @deprecated به جاش از `getSupervisorMyInProgress` یا `getEmployeeInProgress` استفاده کن. */
    @Deprecated("Use getSupervisorMyInProgress or getEmployeeInProgress")
    fun getMyInProgress(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getMyInProgress(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    /** @deprecated به جاش از `getEmployeeBlocked` یا `getTeamBlocked` استفاده کن. */
    @Deprecated("Use getEmployeeBlocked or getTeamBlocked")
    fun getMyBlocked(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getMyBlocked(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    /** @deprecated به جاش از `getEmployeeCompleted` استفاده کن. */
    @Deprecated("Use getEmployeeCompleted")
    fun getMyCompleted(userId: String): Flow<List<TaskModel>> =
        db.taskDao().getMyCompleted(userId).map { it.map { e -> TaskMapper.toModel(e) } }

    /** @deprecated به جاش از `getTeamBlocked` استفاده کن. */
    @Deprecated("Use getTeamBlocked")
    fun getAllBlocked(): Flow<List<TaskModel>> =
        db.taskDao().getAllBlocked().map { it.map { e -> TaskMapper.toModel(e) } }

    /** @deprecated به جاش از `getTeamCompleted` استفاده کن. */
    @Deprecated("Use getTeamCompleted")
    fun getAllCompleted(): Flow<List<TaskModel>> =
        db.taskDao().getAllCompleted().map { it.map { e -> TaskMapper.toModel(e) } }
}

// =========================================================================
// Extension: WorkOrderEntity → WorkOrderModel
// =========================================================================
//
// ✅ این extension باعث می‌شه کد `WorkOrderModel(...)` تکراری حذف بشه.
//    (قبلاً تو getWorkOrdersForTask و getWorkOrdersForTaskOnce تکرار شده بود)
// =========================================================================

private fun com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity.toModel(): WorkOrderModel {
    return WorkOrderModel(
        id = id,
        requestNumber = requestNumber ?: "",
        title = title,
        requestClass = requestClass,
        requestDate = requestDate,
        requester = requester,
        approver = approver ?: "",
        unit = unit,
        subUnit = subUnit,
        equipmentName = equipmentName,
        description = description,
        externalStatus = externalStatus,
        progress = progress,
        source = source
    )
}