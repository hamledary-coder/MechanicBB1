package com.workshop.mechanicbb1.ui.task.quicklog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import TaskModel

class QuickLogTaskAdapter(
    private val onItemClick: (TaskModel) -> Unit
) : RecyclerView.Adapter<QuickLogTaskAdapter.TaskViewHolder>() {

    private var tasks = listOf<TaskModel>()

    fun submitList(newList: List<TaskModel>) {
        tasks = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_log_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

    inner class TaskViewHolder(itemView: android.view.View) :
        RecyclerView.ViewHolder(itemView) {

        private val tvTitle = itemView.findViewById<android.widget.TextView>(R.id.tvTaskTitle)
        private val tvId = itemView.findViewById<android.widget.TextView>(R.id.tvTaskId)
        private val tvStatus = itemView.findViewById<android.widget.TextView>(R.id.tvTaskStatus)
        private val tvUnit = itemView.findViewById<android.widget.TextView>(R.id.tvTaskUnit)
        private val viewStatusDot = itemView.findViewById<android.view.View>(R.id.viewStatusDot)

        fun bind(task: TaskModel) {
            tvTitle.text = task.title
            tvId.text = "#${task.id}"

            val statusText = Config.StatusCode.getText(task.status)
            tvStatus.text = statusText

            val statusColor = when (task.status) {
                Config.StatusCode.IN_PROGRESS -> android.graphics.Color.parseColor("#2196F3")
                Config.StatusCode.BLOCKED -> android.graphics.Color.parseColor("#F44336")

                Config.StatusCode.SENT_TO_SUPERVISOR -> android.graphics.Color.parseColor("#9C27B0")
                else -> android.graphics.Color.parseColor("#78909C")
            }
            viewStatusDot.setBackgroundColor(statusColor)

            //val unitText = Config.UnitCode.getText(task.unit)
            //tvUnit.text = if (unitText.isNotEmpty() && task.unit.isNotEmpty()) "🏢 $unitText" else ""

            itemView.setOnClickListener {
                onItemClick(task)
            }
        }
    }
}