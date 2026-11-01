package com.workshop.mechanicbb1.data.sync

import TaskModel
import com.workshop.mechanicbb1.data.local.entity.TaskEntity

object TaskMapper {

    fun toEntity(model: TaskModel): TaskEntity {
        return TaskEntity(
            id = model.id,
            createDate = model.createDate,
            title = model.title,
            description = model.description,
            creator = model.creator,
            status = model.status,
            assignedTo = model.assignedTo,
            responsible = model.responsible,
            priority = model.priority,
            urgency = model.urgency,
            referredBy = model.referredBy,
            seenBy = model.seenBy.joinToString(","),
            hasUnseenReport = model.hasUnseenReport,
            // ✅ محافظت نهایی
            scheduledDate = model.scheduledDate?.takeIf {
                it.isNotEmpty() && it != "null" && it != "NULL"
            },
            version = 1,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    fun toModel(entity: TaskEntity): TaskModel {
        return TaskModel(
            id = entity.id,
            createDate = entity.createDate,
            title = entity.title,
            description = entity.description,
            creator = entity.creator,
            status = entity.status,
            assignedTo = entity.assignedTo,
            responsible = entity.responsible,
            priority = entity.priority,
            urgency = entity.urgency,
            referredBy = entity.referredBy,
            seenBy = if (entity.seenBy.isEmpty() || entity.seenBy == "[]") {
                emptyList()
            } else {
                entity.seenBy.split(",").filter { it.isNotBlank() }
            },
            hasUnseenReport = entity.hasUnseenReport,
            // ✅ محافظت نهایی: "null" (رشته) → null (واقعی)
            scheduledDate = entity.scheduledDate?.takeIf {
                it.isNotEmpty() && it != "null" && it != "NULL"
            }
        )
    }
}