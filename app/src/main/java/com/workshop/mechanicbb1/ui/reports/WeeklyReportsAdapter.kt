package com.workshop.mechanicbb1.ui.reports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.ReportGroup
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache

class WeeklyReportsAdapter(
    private val reportGroups: List<ReportGroup>,
    private val onItemClick: (ReportGroup) -> Unit
) : RecyclerView.Adapter<WeeklyReportsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val tvTaskId: TextView = itemView.findViewById(R.id.tvTaskId)
        val tvReportCount: TextView = itemView.findViewById(R.id.tvReportCount)
        val tvTaskTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        val tvTaskDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
        val tvResponsible: TextView = itemView.findViewById(R.id.tvResponsible)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val logsListContainer: LinearLayout = itemView.findViewById(R.id.logsListContainer)
        val btnViewDetails: TextView = itemView.findViewById(R.id.btnViewDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weekly_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reportGroup = reportGroups[position]
        val logs = reportGroup.logs

        // ======== هدر ========
        holder.tvTaskId.text = "کار #${reportGroup.taskId}"
        holder.tvReportCount.text = "${logs.size} گزارش"
        holder.tvTaskTitle.text = reportGroup.taskTitle

        // ======== شرح کار ========
        if (reportGroup.taskDescription.isNotEmpty()) {
            holder.tvTaskDescription.visibility = View.VISIBLE
            holder.tvTaskDescription.text = "📌 ${reportGroup.taskDescription}"
        } else {
            holder.tvTaskDescription.visibility = View.GONE
        }

        // ======== مسئول ========
        val responsibleName = if (reportGroup.taskResponsible.isNotEmpty() && reportGroup.taskResponsible != "0") {
            UserCache.getName(reportGroup.taskResponsible)
        } else {
            "تعیین نشده"
        }
        holder.tvResponsible.text = "مسئول: $responsibleName"
        holder.tvStatus.text = "وضعیت: ${Config.StatusCode.getText(reportGroup.taskStatus)}"

        // ======== نمایش هر گزارش ========
        holder.logsListContainer.removeAllViews()

        for ((index, log) in logs.withIndex()) {
            val logView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_log_entry, holder.logsListContainer, false)

            val tvDayName = logView.findViewById<TextView>(R.id.tvDayName)
            val tvLogDate = logView.findViewById<TextView>(R.id.tvLogDate)
            val tvLogTime = logView.findViewById<TextView>(R.id.tvLogTime)
            val tvLogAction = logView.findViewById<TextView>(R.id.tvLogAction)
            val tvLogUser = logView.findViewById<TextView>(R.id.tvLogUser)
            val divider = logView.findViewById<View>(R.id.divider)

            // ✅ تنظیم نام روز هفته
            val dayName = DateUtils.getDayNameFromDate(log.date)
            tvDayName.text = dayName
            tvLogDate.text = log.date
            tvLogTime.text = log.startTime
            tvLogAction.text = log.actionDescription

            val userName = if (log.userId.isNotEmpty()) {
                UserCache.getName(log.userId)
            } else {
                log.userName
            }
            tvLogUser.text = userName
            tvLogUser.setBackgroundResource(R.drawable.bg_user_pill)
            tvLogUser.setPadding(12, 2, 12, 2)

            if (index == logs.size - 1) {
                divider.visibility = View.GONE
            } else {
                divider.visibility = View.VISIBLE
            }

            holder.logsListContainer.addView(logView)
        }

        // ======== کلیک‌ها ========
        holder.btnViewDetails.setOnClickListener {
            onItemClick(reportGroup)
        }

        holder.cardView.setOnClickListener {
            onItemClick(reportGroup)
        }
    }

    override fun getItemCount(): Int = reportGroups.size
}