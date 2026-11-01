package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,

    val createDate: String = "",
    val title: String = "",
    val description: String = "",
    val creator: String = "",
    val status: String = "",

    val assignedTo: String = "",
    val responsible: String = "",

    val priority: Int = 50,
    val urgency: String = "",
    val referredBy: String = "",

    val seenBy: String = "[]",
    val hasUnseenReport: Boolean = false,

    // ⬅️ NEW
    val scheduledDate: String? = null,

    // sync
    val version: Int = 1,
    val lastSyncedAt: Long = 0L,
    val isDeleted: Boolean = false
)