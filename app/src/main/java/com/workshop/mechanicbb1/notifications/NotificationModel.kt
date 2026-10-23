package com.workshop.mechanicbb1.notifications

import com.workshop.mechanicbb1.utils.UserCache

data class NotificationModel(
    val id: String,
    val userId: String,
    val taskId: String,
    val type: String,
    val actorId: String,  // ← برای daily_reminder، تعداد کارهاست
    val isSeen: Boolean,
    val createdAt: String
) {
    fun getDisplayTitle(): String {
        return when (type) {
            "task_assignment" -> "کار جدید"
            "new_report" -> "گزارش جدید"
            "daily_reminder" -> "یادآوری ثبت گزارش"
            else -> "اعلان جدید"
        }
    }

    fun getDisplayMessage(taskTitle: String, assigneeNames: String): String {
        val actorName = UserCache.getName(actorId)
        return when (type) {
            "task_assignment" -> {
                val groupText = if (assigneeNames.isNotEmpty()) {
                    " گروه: $assigneeNames"
                } else {
                    ""
                }
                "مسئولیت کار «$taskTitle» به شما واگذار گردید.$groupText"
            }
            "new_report" -> "$actorName گزارش جدیدی برای کار «$taskTitle» ثبت کرد"
            "daily_reminder" -> {
                val count = actorId.toIntOrNull() ?: 0
                if (count > 1) {
                    "یادآوری: $count کار در انتظار گزارش دارید"
                } else {
                    "یادآوری: لطفاً گزارش کار «$taskTitle» را ثبت کنید"
                }
            }
            else -> "اعلان جدیدی دارید"
        }
    }
}