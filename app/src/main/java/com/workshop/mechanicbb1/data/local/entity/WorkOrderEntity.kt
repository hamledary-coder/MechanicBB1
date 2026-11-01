package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_orders")
data class WorkOrderEntity(
    @PrimaryKey val id: String,
    val requestNumber: String,
    val title: String,
    val requestClass: String?,
    val requestDate: String,
    val requester: String,
    val approver: String?,
    val unit: String,
    val subUnit: String,
    val equipmentName: String,
    val description: String,
    val externalStatus: String?,
    val progress: Int = 0,
    val source: String,
    val lastSyncedAt: Long = System.currentTimeMillis()
)