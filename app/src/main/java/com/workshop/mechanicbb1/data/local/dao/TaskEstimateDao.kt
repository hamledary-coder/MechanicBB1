package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workshop.mechanicbb1.data.local.entity.TaskEstimateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskEstimateDao {

    @Query("SELECT * FROM task_estimates WHERE taskId = :taskId ORDER BY localCreatedAt DESC")
    fun getByTask(taskId: String): Flow<List<TaskEstimateEntity>>

    @Query("SELECT * FROM task_estimates WHERE taskId = :taskId ORDER BY localCreatedAt DESC LIMIT 1")
    suspend fun getLatestForTask(taskId: String): TaskEstimateEntity?

    @Query("SELECT * FROM task_estimates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEstimateEntity?

    @Query("SELECT * FROM task_estimates WHERE syncStatus = 'pending'")
    suspend fun getPendingEstimates(): List<TaskEstimateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(estimate: TaskEstimateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(estimates: List<TaskEstimateEntity>)

    @Update
    suspend fun update(estimate: TaskEstimateEntity)

    @Query("DELETE FROM task_estimates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM task_estimates WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: String)

    @Query("DELETE FROM task_estimates")
    suspend fun clearAll()
}