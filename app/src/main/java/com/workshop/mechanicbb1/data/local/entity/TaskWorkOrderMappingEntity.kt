package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_workorder_mappings")
data class TaskWorkOrderMappingEntity(
    @PrimaryKey val id: String,           // "m_{taskId}_{woId}"
    val taskId: String,
    val workOrderId: String,
    val createdAt: Long = System.currentTimeMillis()
)