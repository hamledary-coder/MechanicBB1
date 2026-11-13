package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_estimates")
data class TaskEstimateEntity(
    @PrimaryKey val id: String,           // "est_{taskId}_{N}"

    val taskId: String,
    val estimatorId: String = "",
    val estimatorName: String = "",
    val estimatedAt: String = "",         // تاریخ شمسی

    // ===== منابع =====
    val manpower: Int = 1,                // تعداد نفرات
    val parts: Int = 1,                   // تعداد قسمت‌ها
    val estimatedHours: Int = 1,          // مدت هر قسمت (ساعت)

    // ===== فاکتورهای ارزیابی (پیش‌بینی) =====
    val workType: String = "1",           // "1"=تجهیزات ثابت | "2"=دوار | "3"=بررسی
    val heatLevel: Int = 30,              // 30-55
    val pollutionLevel: Int = 0,          // 0-100
    val physicalDifficulty: Int = 0,      // 0-5
    val technicalComplexity: Int = 0,     // 0-5

    val notes: String = "",

    // ===== sync =====
    val syncStatus: String = "synced",    // "synced" | "pending" | "failed"
    val localCreatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L
)