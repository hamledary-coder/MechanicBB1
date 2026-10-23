package com.workshop.mechanicbb1.data.model

import android.graphics.Color

data class ReportGroup(
    val taskId: String,
    val taskTitle: String,
    val taskDescription: String,  // ← اضافه شد
    val taskUnit: String,
    val taskUrgency: String,
    val taskStatus: String,
    val taskResponsible: String,
    val taskAssignedTo: String,
    val logs: MutableList<TaskLogModel>
) {
    val totalLogs: Int get() = logs.size
    val latestLog: TaskLogModel get() = logs.firstOrNull() ?: throw IllegalStateException("No logs")

    val urgencyColor: Int
        get() = when (taskUrgency) {
            "خیلی زیاد" -> Color.parseColor("#D32F2F")
            "زیاد" -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#9E9E9E")
        }
}