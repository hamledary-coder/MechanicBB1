package com.workshop.mechanicbb1.ui.task.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.entity.TaskLogWorkOrderProgressEntity

class WorkOrderProgressDetailAdapter(
    private val progresses: List<TaskLogWorkOrderProgressEntity>
) : RecyclerView.Adapter<WorkOrderProgressDetailAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val woColorBullet: View = view.findViewById(R.id.woColorBullet)
        val tvWoTitle: TextView = view.findViewById(R.id.tvWoTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvProgress: TextView = view.findViewById(R.id.tvProgress)
        val tvDetailsNotes: TextView = view.findViewById(R.id.tvDetailsNotes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_log_workorder_detail_item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = progresses[position]

        holder.tvWoTitle.text = "درخواست ${p.workOrderId}"

        val (statusText, statusColor) = when (p.progress) {
            100 -> "✅ انجام شد" to "#4CAF50"
            0 -> "❌ انجام نشد" to "#F44336"
            else -> "⏳ ادامه دارد" to "#FF9800"
        }
        holder.tvStatus.text = statusText
        holder.tvStatus.setTextColor(Color.parseColor(statusColor))
        holder.tvProgress.text = "پیشرفت: ${p.progress}٪"
        holder.woColorBullet.setBackgroundColor(Color.parseColor(statusColor))

        if (p.hasDetails && p.detailsNotes.isNotEmpty()) {
            holder.tvDetailsNotes.text = "📝 ${p.detailsNotes}"
            holder.tvDetailsNotes.visibility = View.VISIBLE
        } else {
            holder.tvDetailsNotes.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = progresses.size
}