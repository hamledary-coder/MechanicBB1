package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,

    val createDate: String = "",
    val title: String = "",
    val description: String = "",
    val creator: String = "",
    val status: String = "",

    val assignedTo: String = "",
    val responsible: String = "",

    val pendingInvites: String = "",
    val unit: String = "",
    val priority: Int = 50,

    val subUnit: String = "",
    val declarationMethod: String = "",
    val requester: String = "",
    val requestDate: String = "",
    val urgency: String = "",
    val initialReview: String = "",
    val systemRequestNumber: String = "",
    val referredBy: String = "",

    // به صورت JSON string ذخیره می‌شه (فعلاً)
    val seenBy: String = "[]",

    val hasUnseenReport: Boolean = false,

    // ====== فیلدهای مخصوص sync ======
    val version: Int = 1,
    val lastSyncedAt: Long = 0L,
    val isDeleted: Boolean = false
)