package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.workshop.mechanicbb1.data.local.entity.OutboxEntity

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox WHERE status = 'pending' ORDER BY createdAt ASC")
    suspend fun getPending(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE status IN ('pending', 'sending') ORDER BY createdAt ASC")
    suspend fun getActive(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    @Insert
    suspend fun insert(item: OutboxEntity): Long

    @Update
    suspend fun update(item: OutboxEntity)

    @Query("DELETE FROM outbox WHERE status = 'done'")
    suspend fun clearDone()

    @Query("DELETE FROM outbox")
    suspend fun clearAll()

    @Query("DELETE FROM outbox WHERE targetId = :targetId AND status = 'pending'")
    suspend fun deleteByTargetId(targetId: String)

    @Query("DELETE FROM outbox WHERE targetId IN (:targetIds) AND status = 'pending'")
    suspend fun deleteByTargetIds(targetIds: List<String>)
}