package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // نوع عملیات: "addTaskLog" | "updateTaskLog" | "assignTask" | ...
    val action: String,

    // محتوای درخواست به صورت JSON string
    val payload: String,

    // برای conflict resolution (مثلاً روی کدوم task)
    val targetId: String = "",
    val expectedVersion: Int = -1,

    // وضعیت: "pending" | "sending" | "done" | "failed"
    val status: String = "pending",

    val retryCount: Int = 0,
    val lastError: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long = 0L
)