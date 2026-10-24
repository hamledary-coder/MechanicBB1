package com.workshop.mechanicbb1.data.model

import java.io.Serializable

data class WorkOrderModel(
    val id: String = "",
    val requestNumber: String = "",
    val title: String = "",
    val requestClass: String? = null,      // "14", "12", "12P", null
    val requestDate: String = "",
    val requester: String = "",
    val unit: String = "",
    val subUnit: String = "",
    val equipmentName: String = "",
    val description: String = "",
    val externalStatus: String? = null,
    val source: String = "manual",
    val progress: Int = 0,                 // 0-100
    val createdAt: String = "",
    val updatedAt: String = ""
) : Serializable {

    // متن کلاس
    val classText: String
        get() = when (requestClass) {
            "14" -> "کلاس ۱۴ (غیرمترقبه)"
            "12" -> "کلاس ۱۲ (برنامه‌ای)"
            "12P" -> "پیشگیرانه (۱۲-P)"
            else -> "نامشخص"
        }

    // رنگ کلاس
    val classColor: Int
        get() = when (requestClass) {
            "14" -> 0xFFD32F2F.toInt()   // قرمز
            "12" -> 0xFFFF9800.toInt()   // نارنجی
            "12P" -> 0xFF4CAF50.toInt()  // سبز
            else -> 0xFF9E9E9E.toInt()   // خاکستری
        }

    // رنگ درصد پیشرفت
    val progressColor: Int
        get() = when {
            progress >= 100 -> 0xFF4CAF50.toInt()  // سبز
            progress >= 50 -> 0xFF2196F3.toInt()   // آبی
            progress > 0 -> 0xFFFF9800.toInt()     // نارنجی
            else -> 0xFF9E9E9E.toInt()             // خاکستری
        }

    // متن درصد
    val progressText: String
        get() = "$progress٪"
}