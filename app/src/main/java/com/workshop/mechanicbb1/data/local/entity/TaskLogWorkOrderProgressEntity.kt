package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_log_workorder_progress")
data class TaskLogWorkOrderProgressEntity(
    @PrimaryKey val id: String,
    val taskLogId: String,
    val workOrderId: String,
    val progress: Int = 0,
    val hasDetails: Boolean = false,       // ← جدید
    val detailsNotes: String = "",          // ← جدید
    val nextAction: String = "",
    val createdAt: Long = 0L
)