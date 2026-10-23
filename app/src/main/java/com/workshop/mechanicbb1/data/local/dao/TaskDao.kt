package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY priority DESC, id DESC")
    fun getAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<TaskEntity?>

    // ========== کوئری‌های فیلتر برای کارتابل ==========

    /** تب «اقدام نشده» → status = "1" */
    @Query("SELECT * FROM tasks WHERE status = '1' AND isDeleted = 0 ORDER BY priority DESC, id DESC")
    fun getUnassigned(): Flow<List<TaskEntity>>

    /** تب «در حال انجام» → status شروع می‌شه با "2" */
    @Query("SELECT * FROM tasks WHERE status LIKE '2%' AND isDeleted = 0 ORDER BY priority DESC, id DESC")
    fun getInProgress(): Flow<List<TaskEntity>>

    /** تب «کارتابل من» برای کارمند → assignedTo شامل user و responsible = user */
    @Query("""
        SELECT * FROM tasks 
        WHERE isDeleted = 0 
          AND status != '5'
          AND responsible = :userId
          AND (',' || assignedTo || ',') LIKE '%,' || :userId || ',%'
        ORDER BY
            CASE WHEN responsible = :userId THEN 0 ELSE 1 END,
            priority DESC,
            id DESC
    """)
    fun getMyCartableForEmployee(userId: String): Flow<List<TaskEntity>>

    /** تب «کارتابل من» برای سرشیفت/مدیر → کارهای مرتبط یا کارهای تمام‌شده */
    @Query("""
        SELECT * FROM tasks 
        WHERE isDeleted = 0 
          AND status != '5'
          AND (
              responsible = :userId 
              OR (',' || assignedTo || ',') LIKE '%,' || :userId || ',%'
              OR status = '41'
              OR hasUnseenReport = 1
          )
        ORDER BY priority DESC, id DESC
    """)
    fun getMyCartableForSupervisor(userId: String): Flow<List<TaskEntity>>

    /** کارهایی که کاربر مسئولشونه و در حال انجامه (برای HomeActivity) */
    @Query("SELECT * FROM tasks WHERE responsible = :userId AND status = '2' AND isDeleted = 0 ORDER BY priority DESC, id DESC")
    fun getResponsibleTasks(userId: String): Flow<List<TaskEntity>>

    // ========== نوشتن ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)


    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): TaskEntity?

    /** ← بایگانی: بدون تغییر (id DESC) */
    @Query("SELECT * FROM tasks WHERE status = '5' AND isDeleted = 0 ORDER BY id DESC")
    fun getArchived(): Flow<List<TaskEntity>>
}