package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey
    val id: String,               // مثل "123-1"

    val taskId: String = "",
    val userId: String = "",
    val userName: String = "",

    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val actionDescription: String = "",
    val assignedUsers: String = "",
    val newStatus: String = "",
    val attachments: String = "",
    val notes: String = "",
    val duration: String = "",
    val comments: String = "[]",

    val heatLevel: Int = 30,
    val pollutionLevel: Int = 0,
    val workType: String = "fixed_equipment",
    val physicalDifficulty: Int = 0,
    val technicalComplexity: Int = 0,
    val taskUrgency: String = "عادی",

    // به صورت JSON string
    val seenBy: String = "[]",

    // ====== فیلدهای مخصوص sync ======
    val syncStatus: String = "synced",   // "synced" | "pending" | "failed"
    val localCreatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L
)