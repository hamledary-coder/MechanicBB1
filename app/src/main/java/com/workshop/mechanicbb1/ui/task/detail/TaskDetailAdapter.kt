package com.workshop.mechanicbb1.ui.task.detail

import TaskModel
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.viewholder.AddLogViewHolder
import com.workshop.mechanicbb1.ui.task.detail.viewholder.TaskInfoViewHolder
import com.workshop.mechanicbb1.ui.task.detail.viewholder.TaskLogViewHolder

class TaskDetailAdapter(
    private val task: TaskModel,
    private val logs: List<TaskLogModel>,
    private val workOrders: List<WorkOrderModel>,
    private val onEditLogClick: (TaskLogModel) -> Unit,
    private val onDeleteLogClick: (TaskLogModel) -> Unit,
    private val onAddLogClick: () -> Unit,
    private val onRefreshLogs: () -> Unit,
    private val onButtonClick: () -> Unit,
    private val currentUserId: String = "",
    private val userRole: String = "",
    private var buttonMode: String = "HIDDEN",
    private var currentStatus: String = task.status,
    private val onEditTaskClick: () -> Unit = {},
    private val onDeleteTaskClick: () -> Unit = {},
    private val onReferTaskClick: () -> Unit = {},
    private val onWorkOrderClick: (WorkOrderModel) -> Unit = {},
    private val onStatusChange: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_TASK_INFO = 0
        private const val TYPE_ADD_LOG = 1
        private const val TYPE_TASK_LOG = 2
    }

    fun updateButtonMode(newMode: String) {
        this.buttonMode = newMode
    }

    fun updateCurrentStatus(newStatus: String) {
        this.currentStatus = newStatus
    }

    fun getButtonMode(): String = buttonMode

    override fun getItemCount(): Int {
        val hasAddLog = buttonMode != "HIDDEN"
        return 1 + (if (hasAddLog) 1 else 0) + logs.size
    }

    override fun getItemViewType(position: Int): Int {
        val hasAddLog = buttonMode != "HIDDEN"
        return when (position) {
            0 -> TYPE_TASK_INFO
            1 -> if (hasAddLog) TYPE_ADD_LOG else TYPE_TASK_LOG
            else -> TYPE_TASK_LOG
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TASK_INFO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.taskdetail_info_item, parent, false)
                TaskInfoViewHolder(view, onStatusChange)
            }
            TYPE_ADD_LOG -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.taskdetail_action_item, parent, false)
                AddLogViewHolder(view, onAddLogClick, onButtonClick, buttonMode, currentStatus)
            }
            TYPE_TASK_LOG -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.taskdetail_log_item, parent, false)
                TaskLogViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val hasAddLog = buttonMode != "HIDDEN"

        when (holder) {
            is TaskInfoViewHolder -> holder.bind(
                task = task,
                userRole = userRole,
                currentUserId = currentUserId,
                workOrders = workOrders,
                onEditTask = onEditTaskClick,
                onDeleteTask = onDeleteTaskClick,
                onReferTask = onReferTaskClick,
                onWorkOrderClick = onWorkOrderClick
            )

            is AddLogViewHolder -> holder.bind(buttonMode, currentStatus)

            is TaskLogViewHolder -> {
                val addLogOffset = if (hasAddLog) 1 else 0
                val logPosition = position - 1 - addLogOffset
                if (logPosition in logs.indices) {
                    val log = logs[logPosition]
                    val prefs = holder.itemView.context.getSharedPreferences(
                        Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE
                    )
                    val uid = prefs.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
                    val role = prefs.getString(Config.PrefKeys.USER_ROLE, "") ?: ""
                    holder.bind(log, task, uid, role, onDeleteLogClick) { onRefreshLogs() }
                }
            }
        }
    }
}