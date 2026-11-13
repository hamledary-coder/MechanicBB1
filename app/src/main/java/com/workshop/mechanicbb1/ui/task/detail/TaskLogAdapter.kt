package com.workshop.mechanicbb1.ui.task.detail

import TaskModel
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.viewholder.TaskLogViewHolder

class TaskLogAdapter(
    private val task: TaskModel,
    private var logs: List<TaskLogModel>,
    private val onDeleteLogClick: (TaskLogModel) -> Unit,
    private val onRefreshLogs: () -> Unit
) : RecyclerView.Adapter<TaskLogViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateLogs(newLogs: List<TaskLogModel>) {
        this.logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.taskdetail_log_item, parent, false)
        return TaskLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskLogViewHolder, position: Int) {
        val log = logs[position]
        val prefs = holder.itemView.context.getSharedPreferences(
            Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE
        )
        val uid = prefs.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        val role = prefs.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        // ✅ شماره گزارش: از جدید به قدیم
        val reportNumber = logs.size - position

        holder.bind(
            log = log,
            task = task,
            currentUserId = uid,
            userRole = role,
            reportNumber = reportNumber,   // ← جدید
            onDelete = onDeleteLogClick,
            onRefresh = { onRefreshLogs() }
        )
    }

    override fun getItemCount(): Int = logs.size
}