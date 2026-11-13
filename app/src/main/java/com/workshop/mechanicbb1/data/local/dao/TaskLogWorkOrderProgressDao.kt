package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workshop.mechanicbb1.data.local.entity.TaskLogWorkOrderProgressEntity
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskLogWorkOrderProgressDao {

    @Query("SELECT * FROM task_log_workorder_progress WHERE taskLogId = :taskLogId")
    fun getByLog(taskLogId: String): Flow<List<TaskLogWorkOrderProgressEntity>>

    @Query("SELECT * FROM task_log_workorder_progress WHERE taskLogId = :taskLogId")
    suspend fun getByLogOnce(taskLogId: String): List<TaskLogWorkOrderProgressEntity>

    @Query("SELECT * FROM task_log_workorder_progress WHERE workOrderId = :workOrderId ORDER BY createdAt DESC")
    fun getByWorkOrder(workOrderId: String): Flow<List<TaskLogWorkOrderProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: TaskLogWorkOrderProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progresses: List<TaskLogWorkOrderProgressEntity>)

    @Query("DELETE FROM task_log_workorder_progress WHERE taskLogId = :taskLogId")
    suspend fun deleteByLog(taskLogId: String)

    @Query("DELETE FROM task_log_workorder_progress")
    suspend fun clearAll()

    @Query("UPDATE task_log_workorder_progress SET workOrderId = :realId WHERE workOrderId = :tempId")
    suspend fun updateWorkOrderId(tempId: String, realId: String)

    @Query("""
    SELECT * FROM task_log_workorder_progress 
    WHERE workOrderId = :workOrderId 
    ORDER BY createdAt DESC, id DESC 
    LIMIT 1
""")
    suspend fun getLatestForWorkOrder(workOrderId: String): TaskLogWorkOrderProgressEntity?

    @Query("""
    SELECT p.* FROM task_log_workorder_progress p
    INNER JOIN reports r ON r.id = p.taskLogId
    WHERE p.workOrderId = :workOrderId
    ORDER BY r.date DESC, r.startTime DESC, p.id DESC
    LIMIT 1
""")
    suspend fun getLatestForWorkOrderByDate(workOrderId: String): TaskLogWorkOrderProgressEntity?

    @Query("DELETE FROM task_log_workorder_progress WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * آخرین progress برای یه WorkOrder، **قبل از** یه گزارش خاص (بر اساس تاریخ/ساعت انجام).
     * برای ویرایش گزارش استفاده می‌شه که خود گزارش جزو «قبلی‌ها» حساب نشه.
     */
    @Query("""
    SELECT p.* FROM task_log_workorder_progress p
    INNER JOIN reports r ON r.id = p.taskLogId
    WHERE p.workOrderId = :workOrderId
      AND p.taskLogId != :excludeLogId
    ORDER BY r.date DESC, r.startTime DESC, p.id DESC
    LIMIT 1
""")
    suspend fun getLatestForWorkOrderBeforeLog(
        workOrderId: String,
        excludeLogId: String
    ): TaskLogWorkOrderProgressEntity?

    @Query("DELETE FROM task_log_workorder_progress WHERE workOrderId = :workOrderId")
    suspend fun deleteByWorkOrder(workOrderId: String)
}