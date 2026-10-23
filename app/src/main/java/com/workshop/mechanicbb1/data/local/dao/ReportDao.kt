package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workshop.mechanicbb1.data.local.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Query("SELECT * FROM reports WHERE taskId = :taskId ORDER BY date DESC, startTime DESC")
    fun getByTask(taskId: String): Flow<List<ReportEntity>>

    /** ← جدید: نسخه‌ی suspend برای محاسبه ID */
    @Query("SELECT * FROM reports WHERE taskId = :taskId")
    suspend fun getByTaskOnce(taskId: String): List<ReportEntity>

    @Query("SELECT * FROM reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReportEntity?

    @Query("SELECT * FROM reports WHERE syncStatus = 'pending'")
    suspend fun getPendingReports(): List<ReportEntity>

    @Query("SELECT * FROM reports WHERE taskId = :taskId AND syncStatus = 'pending'")
    suspend fun getPendingForTask(taskId: String): List<ReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<ReportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity)

    @Update
    suspend fun update(report: ReportEntity)

    @Query("DELETE FROM reports WHERE taskId = :taskId AND syncStatus = 'synced'")
    suspend fun clearSyncedForTask(taskId: String)

    @Query("DELETE FROM reports")
    suspend fun clearAll()

    @Query("DELETE FROM reports WHERE id IN (:ids) AND syncStatus = 'synced'")
    suspend fun deleteSyncedByIds(ids: List<String>)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reports WHERE taskId = :taskId")
    suspend fun deleteAllByTaskId(taskId: String)


    @Query("SELECT * FROM reports")
    suspend fun getAllOnce(): List<ReportEntity>
    @Query("SELECT * FROM reports WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, startTime DESC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<ReportEntity>>
}