package com.workshop.mechanicbb1.data.local

import android.content.Context
import com.workshop.mechanicbb1.data.model.ReportGroup
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.sync.ReportMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

class BossReportsLocalRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getReportsInRange(startDate: String, endDate: String): Flow<List<TaskLogModel>> {
        return db.reportDao().getByDateRange(startDate, endDate).mapLatest { entities ->
            entities.map { ReportMapper.toModel(it) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getGroupedReports(startDate: String, endDate: String): Flow<List<ReportGroup>> {
        return getReportsInRange(startDate, endDate).mapLatest { logs ->
            val grouped = logs.groupBy { it.taskId }
            val taskIds = grouped.keys.toList()
            val tasks = db.taskDao().getByIds(taskIds)
            val taskMap = tasks.associateBy { it.id }

            grouped.map { (taskId, taskLogs) ->
                val task = taskMap[taskId]
                ReportGroup(
                    taskId = taskId,
                    taskTitle = task?.title ?: "",
                    taskDescription = task?.description ?: "",
                    taskUnit = task?.unit ?: "",
                    taskUrgency = task?.urgency ?: "",
                    taskStatus = task?.status ?: "",
                    taskResponsible = task?.responsible ?: "",
                    taskAssignedTo = task?.assignedTo ?: "",
                    logs = taskLogs.toMutableList()
                )
            }
        }
    }
}