package com.workshop.mechanicbb1.data.sync

import android.util.Log
import com.workshop.mechanicbb1.data.local.entity.TaskEstimateEntity
import org.json.JSONObject

object EstimateMapper {

    private const val TAG = "EstimateMapper"

    /**
     * JSON (از سرور) → Entity (برای Room)
     */
    fun fromJson(json: JSONObject, syncStatus: String = "synced"): TaskEstimateEntity {
        return TaskEstimateEntity(
            id = json.optString("id", ""),
            taskId = json.optString("task_id", ""),
            estimatorId = json.optString("estimator_id", ""),
            estimatorName = json.optString("estimator_name", ""),
            estimatedAt = json.optString("estimated_at", ""),
            manpower = json.optInt("manpower", 1),
            parts = json.optInt("parts", 1),
            estimatedHours = json.optInt("estimated_hours", 1),
            workType = json.optString("work_type", "1"),
            heatLevel = json.optInt("heat_level", 30),
            pollutionLevel = json.optInt("pollution_level", 0),
            physicalDifficulty = json.optInt("physical_difficulty", 0),
            technicalComplexity = json.optInt("technical_complexity", 0),
            notes = json.optString("notes", ""),
            syncStatus = syncStatus,
            localCreatedAt = System.currentTimeMillis(),
            lastSyncedAt = if (syncStatus == "synced") System.currentTimeMillis() else 0L
        )
    }

    /**
     * Entity → JSON (برای Outbox payload و ارسال به سرور)
     */
    fun toJson(entity: TaskEstimateEntity): JSONObject {
        return JSONObject().apply {
            put("id", entity.id)
            put("taskId", entity.taskId)
            put("task_id", entity.taskId)
            put("estimatorId", entity.estimatorId)
            put("estimator_id", entity.estimatorId)
            put("estimatorName", entity.estimatorName)
            put("estimator_name", entity.estimatorName)
            put("estimatedAt", entity.estimatedAt)
            put("estimated_at", entity.estimatedAt)
            put("manpower", entity.manpower)
            put("parts", entity.parts)
            put("estimatedHours", entity.estimatedHours)
            put("estimated_hours", entity.estimatedHours)
            put("workType", entity.workType)
            put("work_type", entity.workType)
            put("heatLevel", entity.heatLevel)
            put("heat_level", entity.heatLevel)
            put("pollutionLevel", entity.pollutionLevel)
            put("pollution_level", entity.pollutionLevel)
            put("physicalDifficulty", entity.physicalDifficulty)
            put("physical_difficulty", entity.physicalDifficulty)
            put("technicalComplexity", entity.technicalComplexity)
            put("technical_complexity", entity.technicalComplexity)
            put("notes", entity.notes)
        }
    }
}