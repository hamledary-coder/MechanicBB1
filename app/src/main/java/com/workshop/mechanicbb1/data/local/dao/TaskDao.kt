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

    // ========== عمومی ==========
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY priority DESC, id DESC")
    fun getAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<TaskEntity>

    // ================================================================
    // تب ۱: «کارهای من» — ۳ بخش
    // ================================================================

    /** بخش ۱: در حال انجام — responsible = من */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '2'
          AND responsible = :userId
        ORDER BY
            CASE urgency
                WHEN 'خیلی زیاد' THEN 1
                WHEN 'زیاد' THEN 2
                ELSE 3
            END ASC,
            priority DESC,
            id DESC
    """)
    fun getMyInProgress(userId: String): Flow<List<TaskEntity>>

    /** بخش ۲: متوقف — responsible = من */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '3'
          AND responsible = :userId
        ORDER BY
            CASE urgency
                WHEN 'خیلی زیاد' THEN 1
                WHEN 'زیاد' THEN 2
                ELSE 3
            END ASC,
            priority DESC,
            id DESC
    """)
    fun getMyBlocked(userId: String): Flow<List<TaskEntity>>

    /** بخش ۳: به اتمام — responsible = من */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '41'
          AND responsible = :userId
        ORDER BY
            CASE urgency
                WHEN 'خیلی زیاد' THEN 1
                WHEN 'زیاد' THEN 2
                ELSE 3
            END ASC,
            priority DESC,
            id DESC
    """)
    fun getMyCompleted(userId: String): Flow<List<TaskEntity>>

    // ================================================================
// متوقف‌های تیم (برای سرشیفت/مدیر)
// ================================================================
    @Query("""
    SELECT * FROM tasks
    WHERE isDeleted = 0
      AND status = '3'
    ORDER BY
        CASE urgency
            WHEN 'خیلی زیاد' THEN 1
            WHEN 'زیاد' THEN 2
            ELSE 3
        END ASC,
        priority DESC,
        id DESC
""")
    fun getAllBlocked(): Flow<List<TaskEntity>>

    // ================================================================
// اتمام‌های تیم (برای سرشیفت/مدیر)
// ================================================================
    @Query("""
    SELECT * FROM tasks
    WHERE isDeleted = 0
      AND status = '41'
    ORDER BY
        CASE urgency
            WHEN 'خیلی زیاد' THEN 1
            WHEN 'زیاد' THEN 2
            ELSE 3
        END ASC,
        priority DESC,
        id DESC
""")
    fun getAllCompleted(): Flow<List<TaskEntity>>

    // ================================================================
    // تب ۲: «کارهای امروز» — تیم
    // ================================================================
    /**
     * تب ۲: «کارهای امروز»
     *
     * منطق (دقیق):
     * - همه‌ی کارهایی که status = '2' (در حال انجام)
     * - و scheduledDate شون:
     *   - null (بدون برنامه‌ریزی) → میان
     *   - یا <= امروز (گذشته یا امروز) → میان
     *
     * کارهای status=2 که scheduledDate=آینده دارن → نمیان
     * کارهای status=1, 3, 41, 5 → همیشه حذف می‌شن
     */
    @Query("""
    SELECT * FROM tasks
    WHERE isDeleted = 0
      AND status = '2'
      AND (scheduledDate IS NULL 
           OR scheduledDate = 'null' 
           OR scheduledDate = 'NULL' 
           OR scheduledDate <= :today)
    ORDER BY
        CASE urgency
            WHEN 'خیلی زیاد' THEN 1
            WHEN 'زیاد' THEN 2
            ELSE 3
        END ASC,
        priority DESC,
        id DESC
""")
    fun getTodayTasks(today: String): Flow<List<TaskEntity>>
    // ================================================================
    // تب ۳: «ارجاع نشده» — همه‌ی status=1 (آسان‌گیرانه)
    // ================================================================
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '1'
        ORDER BY
            CASE urgency
                WHEN 'خیلی زیاد' THEN 1
                WHEN 'زیاد' THEN 2
                ELSE 3
            END ASC,
            priority DESC,
            id DESC
    """)
    fun getQueueTasks(): Flow<List<TaskEntity>>

    // ================================================================
    // بایگانی
    // ================================================================
    @Query("SELECT * FROM tasks WHERE status = '5' AND isDeleted = 0 ORDER BY id DESC")
    fun getArchived(): Flow<List<TaskEntity>>

    // ================================================================
    // HomeActivity
    // ================================================================
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND responsible = :userId
          AND status = '2'
        ORDER BY priority DESC, id DESC
    """)
    fun getResponsibleTasks(userId: String): Flow<List<TaskEntity>>

    // ================================================================
    // نوشتن
    // ================================================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Query("SELECT MAX(CAST(id AS INTEGER)) FROM tasks WHERE id NOT LIKE 'tmp-%'")
    suspend fun getMaxNumericId(): Int?
}