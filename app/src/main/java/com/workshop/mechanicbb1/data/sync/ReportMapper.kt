package com.workshop.mechanicbb1.data.sync

import com.workshop.mechanicbb1.data.local.entity.ReportEntity
import com.workshop.mechanicbb1.data.model.TaskLogModel

object ReportMapper {

    fun toEntity(model: TaskLogModel, syncStatus: String = "synced"): ReportEntity {
        return ReportEntity(
            id = model.id,
            taskId = model.taskId,
            userId = model.userId,
            userName = model.userName,
            date = model.date,
            startTime = model.startTime,
            endTime = model.endTime,
            actionDescription = model.actionDescription,
            assignedUsers = model.assignedUsers,
            newStatus = model.newStatus,
            attachments = model.attachments,
            notes = model.notes,
            duration = model.duration,
            comments = model.comments,
            heatLevel = model.heatLevel,
            pollutionLevel = model.pollutionLevel,
            workType = model.workType,
            physicalDifficulty = model.physicalDifficulty,
            technicalComplexity = model.technicalComplexity,
            taskUrgency = model.taskUrgency,
            seenBy = model.seenBy.joinToString(","),
            syncStatus = syncStatus,
            localCreatedAt = System.currentTimeMillis(),
            lastSyncedAt = if (syncStatus == "synced") System.currentTimeMillis() else 0L
        )
    }

    fun toModel(entity: ReportEntity): TaskLogModel {
        return TaskLogModel(
            id = entity.id,
            taskId = entity.taskId,
            userId = entity.userId,
            userName = entity.userName,
            date = entity.date,
            startTime = entity.startTime,
            endTime = entity.endTime,
            actionDescription = entity.actionDescription,
            assignedUsers = entity.assignedUsers,
            newStatus = entity.newStatus,
            attachments = entity.attachments,
            notes = entity.notes,
            duration = entity.duration,
            comments = entity.comments,
            heatLevel = entity.heatLevel,
            pollutionLevel = entity.pollutionLevel,
            workType = entity.workType,
            physicalDifficulty = entity.physicalDifficulty,
            technicalComplexity = entity.technicalComplexity,
            taskUrgency = entity.taskUrgency,
            seenBy = if (entity.seenBy.isEmpty() || entity.seenBy == "[]") {
                emptyList()
            } else {
                entity.seenBy.split(",").filter { it.isNotBlank() }
            }
        )
    }
}