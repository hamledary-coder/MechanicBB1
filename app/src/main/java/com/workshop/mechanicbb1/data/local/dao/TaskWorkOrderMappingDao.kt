package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskWorkOrderMappingDao {

    @Query("SELECT * FROM task_workorder_mappings WHERE taskId = :taskId")
    fun getByTask(taskId: String): Flow<List<TaskWorkOrderMappingEntity>>

    @Query("SELECT * FROM task_workorder_mappings WHERE taskId = :taskId")
    suspend fun getByTaskOnce(taskId: String): List<TaskWorkOrderMappingEntity>

    @Query("SELECT * FROM task_workorder_mappings WHERE workOrderId = :workOrderId")
    fun getByWorkOrder(workOrderId: String): Flow<List<TaskWorkOrderMappingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: TaskWorkOrderMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<TaskWorkOrderMappingEntity>)

    @Query("DELETE FROM task_workorder_mappings WHERE taskId = :taskId AND workOrderId = :workOrderId")
    suspend fun delete(taskId: String, workOrderId: String)

    @Query("DELETE FROM task_workorder_mappings WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: String)

    @Query("DELETE FROM task_workorder_mappings WHERE workOrderId = :workOrderId")
    suspend fun deleteByWorkOrder(workOrderId: String)

    @Query("DELETE FROM task_workorder_mappings")
    suspend fun clearAll()

    @Query("DELETE FROM task_workorder_mappings WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}