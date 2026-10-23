package com.workshop.mechanicbb1.data.model

data class TaskLogModel(
    val id: String,
    val taskId: String,
    val userId: String,
    val userName: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val actionDescription: String,
    val assignedUsers: String,
    val newStatus: String,
    val attachments: String,
    val notes: String,
    val duration: String = "",
    val comments: String = "[]",
    val heatLevel: Int = 30,
    val pollutionLevel: Int = 0,
    val workType: String = "fixed_equipment",
    val seenBy: List<String> = emptyList(),
    val physicalDifficulty: Int = 0,
    val technicalComplexity: Int = 0,
    val taskUrgency: String = "عادی"
)