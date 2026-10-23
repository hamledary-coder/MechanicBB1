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
            pendingInvites = model.pendingInvites,
            unit = model.unit,
            priority = model.priority,
            subUnit = model.sub_unit,
            declarationMethod = model.declaration_method,
            requester = model.requester,
            requestDate = model.request_date,
            urgency = model.urgency,
            initialReview = model.initial_review,
            systemRequestNumber = model.system_request_number,
            referredBy = model.referredBy,
            seenBy = model.seenBy.joinToString(","),
            hasUnseenReport = model.hasUnseenReport,
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
            pendingInvites = entity.pendingInvites,
            unit = entity.unit,
            priority = entity.priority,
            sub_unit = entity.subUnit,
            declaration_method = entity.declarationMethod,
            requester = entity.requester,
            request_date = entity.requestDate,
            urgency = entity.urgency,
            initial_review = entity.initialReview,
            system_request_number = entity.systemRequestNumber,
            referredBy = entity.referredBy,
            seenBy = if (entity.seenBy.isEmpty() || entity.seenBy == "[]") {
                emptyList()
            } else {
                entity.seenBy.split(",").filter { it.isNotBlank() }
            },
            hasUnseenReport = entity.hasUnseenReport
        )
    }
}