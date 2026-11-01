package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workshop.mechanicbb1.data.local.entity.TaskLogWorkOrderProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskLogWorkOrderProgressDao {

    @Query("SELECT * FROM task_log_workorder_progress WHERE taskLogId = :taskLogId")
    fun getByLog(taskLogId: String): Flow<List<TaskLogWorkOrderProgressEntity>>

    @Query("SELECT * FROM task_log_workorder_progress WHERE taskLogId = :taskLogId")
    suspend fun getByLogOnce(taskLogId: String): List<TaskLogWorkOrderProgressEntity>

    @Query("SELECT * FROM task_log_workorder_progress WHERE workOrderId = :workOrderId ORDER BY createdAt DESC")
    fun getByWorkOrder(workOrderId: String): Flow<List<TaskLogWorkOrderProgressEntity>>

    @Query("SELECT * FROM task_log_workorder_progress WHERE workOrderId = :workOrderId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForWorkOrder(workOrderId: String): TaskLogWorkOrderProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: TaskLogWorkOrderProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progresses: List<TaskLogWorkOrderProgressEntity>)

    @Query("DELETE FROM task_log_workorder_progress WHERE taskLogId = :taskLogId")
    suspend fun deleteByLog(taskLogId: String)

    @Query("DELETE FROM task_log_workorder_progress")
    suspend fun clearAll()
}