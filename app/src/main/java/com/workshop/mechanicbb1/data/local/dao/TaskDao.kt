package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workshop.mechanicbb1.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO جدول `tasks` — مسئول خواندن/نوشتن کارها.
 *
 * مرتب‌سازی پیش‌فرض همه‌ی کوئری‌ها:
 *   1. urgency:  خیلی زیاد → زیاد → عادی
 *   2. priority: بالاتر → پایین‌تر
 *   3. id:       جدیدتر → قدیمی‌تر
 */
@Dao
interface TaskDao {

    // =========================================================================
    // بخش ۱: عمومی
    // =========================================================================

    /** همه‌ی کارهای حذف‌نشده — دیباگ و fallback */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY priority DESC, id DESC")
    fun getAll(): Flow<List<TaskEntity>>

    /** یه کار با ID (suspend — یک‌باره) */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    /** یه کار با ID (Flow — observe) */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<TaskEntity?>

    /** چند کار با IDها */
    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TaskEntity>

    /** همه‌ی کارهای حذف‌نشده (suspend — sync/محاسبات) */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<TaskEntity>

    // =========================================================================
    // بخش ۲: تب «کارهای امروز» (تیم) — فقط status=2
    // =========================================================================

    /**
     * کارهای «امروز» برای کل تیم:
     *   - status = '2' (در حال انجام)
     *   - scheduledDate خالی (بدون برنامه) یا امروز/گذشته
     *
     * کارهای آینده یا 21/3/41/5 نمیان.
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

    // =========================================================================
    // بخش ۳: تب «ارجاع نشده» — status=1
    // =========================================================================

    /** کارهای «ارجاع نشده» (status=1) */
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

    // =========================================================================
    // بخش ۴: بایگانی — status=5
    // =========================================================================

    /** کارهای بایگانی‌شده */
    @Query("SELECT * FROM tasks WHERE status = '5' AND isDeleted = 0 ORDER BY id DESC")
    fun getArchived(): Flow<List<TaskEntity>>

    // =========================================================================
    // بخش ۵: HomeActivity — کارهای مسئول (QuickLog card)
    // =========================================================================

    /**
     * کارهای مسئول برای کارت QuickLog در HomeActivity.
     * شامل status=2 و 21 — کارهایی که مسئول می‌تونه گزارش بده.
     */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND responsible = :userId
          AND status IN ('2', '21')
        ORDER BY priority DESC, id DESC
    """)
    fun getResponsibleTasks(userId: String): Flow<List<TaskEntity>>

    // =========================================================================
    // بخش ۶: «کارهای من» برای کارمند (assignedTo شامل من)
    // =========================================================================
    //
    // منطق: کارمند کاری رو می‌بینه که «توی گروهش» هست.
    //        یعنی assignedTo (رشته‌ی "3,5,7") شامل userId باشه.
    //
    // ✅ چرا `',' || REPLACE(assignedTo, ' ', '') || ',' LIKE '%,' || :userId || ',%'`؟
    //    - REPLACE فاصله‌ها رو حذف می‌کنه ("3, 5" → "3,5")
    //    - || رشته‌ها رو به هم می‌چسبونه
    //    - ',' اول و آخر → جلوگیری از match اشتباه ("13" نباید با "3" match شه)
    // =========================================================================

    /** (کارمند) در حال انجام — status IN (2, 21, 31) */
    @Query("""
    SELECT * FROM tasks
    WHERE isDeleted = 0
      AND status IN ('2', '21', '31')
      AND (',' || REPLACE(assignedTo, ' ', '') || ',' LIKE '%,' || :userId || ',%')
    ORDER BY priority DESC, id DESC
""")
    fun getEmployeeInProgress(userId: String): Flow<List<TaskEntity>>

    /** (کارمند) متوقف — status=3 */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '3'
          AND (',' || REPLACE(assignedTo, ' ', '') || ',' LIKE '%,' || :userId || ',%')
        ORDER BY priority DESC, id DESC
    """)
    fun getEmployeeBlocked(userId: String): Flow<List<TaskEntity>>

    /** (کارمند) اتمام — status=41 */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '41'
          AND (',' || REPLACE(assignedTo, ' ', '') || ',' LIKE '%,' || :userId || ',%')
        ORDER BY priority DESC, id DESC
    """)
    fun getEmployeeCompleted(userId: String): Flow<List<TaskEntity>>

    /** (کارمند) منتظر گزارش — status IN (21,31) */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status IN ('21', '31')
          AND (',' || REPLACE(assignedTo, ' ', '') || ',' LIKE '%,' || :userId || ',%')
        ORDER BY priority DESC, id DESC
    """)
    fun getEmployeeAwaitingReport(userId: String): Flow<List<TaskEntity>>

    // =========================================================================
    // بخش ۷: «کارهای من» برای سرشیفت/مدیر (تیم + خودم)
    // =========================================================================

    /** (سرشیفت) در حال انجام خودم — status IN (2, 21, 31)، responsible=من */
    @Query("""
    SELECT * FROM tasks
    WHERE isDeleted = 0
      AND status IN ('2', '21', '31')
      AND responsible = :userId
    ORDER BY priority DESC, id DESC
""")
    fun getSupervisorMyInProgress(userId: String): Flow<List<TaskEntity>>

    /** (سرشیفت) متوقف‌های تیم — status=3 */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '3'
        ORDER BY priority DESC, id DESC
    """)
    fun getTeamBlocked(): Flow<List<TaskEntity>>

    /** (سرشیفت) اتمام‌های تیم (منتظر تأیید) — status=41 */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = '41'
        ORDER BY priority DESC, id DESC
    """)
    fun getTeamCompleted(): Flow<List<TaskEntity>>

    /** (سرشیفت) منتظر گزارش‌های تیم — status IN (21,31) */
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status IN ('21', '31')
        ORDER BY priority DESC, id DESC
    """)
    fun getTeamAwaitingReport(): Flow<List<TaskEntity>>

    // =========================================================================
    // بخش ۸: نوشتن (Insert/Update/Delete)
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    /** آپدیت فقط status — سریع‌تر از update کامل */
    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    // =========================================================================
    // بخش ۹: Helpers برای تولید temp ID
    // =========================================================================

    /** بزرگ‌ترین ID عددی (برای تولید temp ID بعد از createTask) */
    @Query("SELECT MAX(CAST(id AS INTEGER)) FROM tasks WHERE id NOT LIKE 'tmp-%' AND isDeleted = 0")
    suspend fun getMaxNumericId(): Int?

    /** بزرگ‌ترین شماره‌ی temp برای این کاربر */
    @Query("""
        SELECT MAX(
            CAST(SUBSTR(id, LENGTH('tmp-' || :userId || '-') + 1) AS INTEGER)
        ) FROM tasks
        WHERE id LIKE 'tmp-' || :userId || '-%'
    """)
    suspend fun getMaxTempNumericId(userId: String): Int?

    // =========================================================================
    // بخش ۱۰: Legacy (کوئری‌های قدیمی — برای سازگاری)
    // =========================================================================
    //
    // ⚠️ این کوئری‌ها هنوز جای دیگه استفاده می‌شن و حذف نشدن.
    //    اگه مطمئن شدی هیچ‌جا استفاده نمی‌شن، می‌تونی حذف کنی.
    // =========================================================================

    /**
     * بخش ۱ قدیمی: در حال انجام — status IN (2, 21)، responsible=من.
     * @deprecated به جاش از `getSupervisorMyInProgress` (فقط 2) یا
     *             `getEmployeeInProgress` (فقط 2، assignedTo) استفاده کن.
     */
    @Deprecated("Use getSupervisorMyInProgress or getEmployeeInProgress")
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status IN ('2', '21')
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

    /**
     * بخش ۲ قدیمی: متوقف — status IN (3, 31)، responsible=من.
     * @deprecated به جاش از `getEmployeeBlocked` (3، assignedTo) یا
     *             `getTeamBlocked` (3، همه) استفاده کن.
     */
    @Deprecated("Use getEmployeeBlocked or getTeamBlocked")
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status IN ('3', '31')
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

    /**
     * بخش ۳ قدیمی: به اتمام — status=41، responsible=من.
     * @deprecated به جاش از `getEmployeeCompleted` (assignedTo) استفاده کن.
     */
    @Deprecated("Use getEmployeeCompleted")
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

    /**
     * متوقف‌های تیم (قدیمی) — status IN (3, 31).
     * @deprecated به جاش از `getTeamBlocked` (فقط 3) استفاده کن.
     */
    @Deprecated("Use getTeamBlocked")
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status IN ('3', '31')
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

    /**
     * اتمام‌های تیم (قدیمی) — status=41.
     * @deprecated به جاش از `getTeamCompleted` استفاده کن (یکیه).
     */
    @Deprecated("Use getTeamCompleted")
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
}