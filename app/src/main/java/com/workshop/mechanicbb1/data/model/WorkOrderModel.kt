package com.workshop.mechanicbb1.data.model

import java.io.Serializable

data class WorkOrderModel(
    val id: String = "",
    val requestNumber: String = "",
    val title: String = "",
    val requestClass: String? = null,
    val requestDate: String = "",
    val requester: String = "",
    val approver: String = "",
    val unit: String = "",
    val subUnit: String = "",
    val equipmentName: String = "",
    val description: String = "",
    val externalStatus: String? = null,
    val progress: Int = 0,
    val source: String = "manual",
    val createdAt: String = "",
    val updatedAt: String = ""
) : Serializable {

    // ============ کلاس ============
    val classText: String
        get() = when (requestClass) {
            "14" -> "کلاس ۱۴ (غیرمترقبه)"
            "12" -> "کلاس ۱۲ (برنامه‌ای)"
            "12P" -> "پیشگیرانه (۱۲-P)"
            "15" -> "کلاس ۱۵ (اقدام اصلاحی)"
            else -> "نامشخص"
        }

    val classColor: Int
        get() = when (requestClass) {
            "14" -> 0xFFD32F2F.toInt()
            "12" -> 0xFFFF9800.toInt()
            "12P" -> 0xFF4CAF50.toInt()
            "15" -> 0xFF9C27B0.toInt()
            else -> 0xFF9E9E9E.toInt()
        }

    // ============ مرحله (external_status) ============
    val stage: Int
        get() = externalStatus?.toIntOrNull() ?: 1

    val stageText: String
        get() = when (stage) {
            1 -> "ایجاد فرم"
            2 -> "تایید اپراتور"
            3 -> "تایید رییس واحد"
            4 -> "شماره‌گذاری برنامه‌ریز"
            5 -> "تایید رییس گروه"
            6 -> "ثبت اطلاعات تکمیلی"
            7 -> "تایید رییس گروه (۲)"
            8 -> "تایید نهایی اپراتور"
            9 -> "تایید نهایی رییس واحد"
            10 -> "بایگانی"
            else -> "نامشخص"
        }

    // ============ وضعیت (بر اساس external_status) ============
    val isArchived: Boolean
        get() = externalStatus == "10"

    val statusText: String
        get() = if (isArchived) "بایگانی" else "باز"

    val statusColor: Int
        get() = if (isArchived) 0xFF9E9E9E.toInt() else 0xFF2196F3.toInt()

    // ============ درصد پیشرفت ============
    val progressColor: Int
        get() = when {
            progress >= 100 -> 0xFF4CAF50.toInt()
            progress >= 50 -> 0xFF2196F3.toInt()
            progress > 0 -> 0xFFFF9800.toInt()
            else -> 0xFF9E9E9E.toInt()
        }

    val progressText: String
        get() = "$progress٪"
}