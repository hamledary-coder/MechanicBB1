package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_estimates")
data class TaskEstimateEntity(
    @PrimaryKey val id: String,           // "est_{taskId}_{timestamp}"

    val taskId: String,
    val estimatorId: String = "",
    val estimatorName: String = "",
    val estimatedAt: String = "",         // تاریخ شمسی

    val manpower: Int = 0,
    val estimatedHours: Int = 0,
    val workType: String = "",            // "1"|"2"|"3"
    val notes: String = "",

    // sync
    val syncStatus: String = "synced",
    val localCreatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L
)