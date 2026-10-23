package com.workshop.mechanicbb1.ui.reports

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.ReportGroup
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache
import java.util.Calendar

class DailyReportsAdapter(
    private val reportGroups: List<ReportGroup>,
    private val onItemClick: (ReportGroup) -> Unit,
    private val selectedDate: String
) : RecyclerView.Adapter<DailyReportsAdapter.ViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val tvTaskId: TextView = itemView.findViewById(R.id.tvTaskId)
        val tvReportCount: TextView = itemView.findViewById(R.id.tvReportCount)
        val tvTaskTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)

        // گزارشات روز جاری
        val currentDayLogsContainer: LinearLayout = itemView.findViewById(R.id.currentDayLogsContainer)

        // گزارشات روزهای قبل (بسته)
        val previousDaysContainer: View = itemView.findViewById(R.id.previousDaysContainer)
        val previousDaysListContainer: LinearLayout = itemView.findViewById(R.id.previousDaysListContainer)
        val tvPreviousDaysSummary: TextView = itemView.findViewById(R.id.tvPreviousDaysSummary)
        val btnExpand: TextView = itemView.findViewById(R.id.btnExpand)
        val btnViewDetails: TextView = itemView.findViewById(R.id.btnViewDetails)

        val tvTaskDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reportGroup = reportGroups[position]
        val isExpanded = expandedPositions.contains(position)
        val logs = reportGroup.logs

        // ======== تفکیک گزارشات بر اساس تاریخ ========
        val todayLogs = logs.filter { it.date == selectedDate }

        // ✅ گزارشات روزهای قبل از تاریخ انتخاب شده (نه همه روزهای قبل)
        val previousDaysLogs = logs.filter {
            it.date < selectedDate  // فقط تاریخ‌های کوچکتر (قدیمی‌تر)
        }.sortedByDescending { it.date }  // جدیدترین اول (نزولی)

        // ======== هدر ========
        holder.tvTaskId.text = "کار #${reportGroup.taskId}"
        holder.tvReportCount.text = "${logs.size} گزارش"
        holder.tvTaskTitle.text = reportGroup.taskTitle

        // در متد onBindViewHolder، بعد از تنظیم tvTaskTitle:

        // ======== شرح کار ========
        if (reportGroup.taskDescription.isNotEmpty()) {
            holder.tvTaskDescription.visibility = View.VISIBLE
            holder.tvTaskDescription.text = "📌 ${reportGroup.taskDescription}"
        } else {
            holder.tvTaskDescription.visibility = View.GONE
        }

        // ======== ۱. نمایش گزارشات روز جاری ========
        holder.currentDayLogsContainer.removeAllViews()

        if (todayLogs.isNotEmpty()) {
            for (log in todayLogs) {
                val logView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_log_entry, holder.currentDayLogsContainer, false)

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

                // نام کاربر
                tvLogUser.text = log.userName
                tvLogUser.setBackgroundResource(R.drawable.bg_user_pill)
                tvLogUser.setPadding(12, 2, 12, 2)
                tvLogUser.setBackgroundResource(R.drawable.bg_user_pill)
                tvLogUser.setPadding(12, 2, 12, 2)

                if (log == todayLogs.last()) {
                    divider.visibility = View.GONE
                } else {
                    divider.visibility = View.VISIBLE
                }

                holder.currentDayLogsContainer.addView(logView)
            }
        } else {
            // این حالت نباید پیش بیاد، ولی برای امنیت
            Log.w("DailyReportsAdapter", "todayLogs is empty for task: ${reportGroup.taskId}")
            val emptyView = TextView(holder.itemView.context)
            emptyView.text = "❗ خطا: این کار در این روز گزارشی ندارد"
            emptyView.setTextColor(holder.itemView.context.getColor(R.color.priority_high))
            emptyView.textSize = 12f
            emptyView.setPadding(0, 8, 0, 8)
            holder.currentDayLogsContainer.addView(emptyView)
        }

        // ======== ۲. نمایش گزارشات روزهای قبل (بسته) ========
        if (previousDaysLogs.isNotEmpty()) {
            // نمایش شمارشگر
            holder.tvPreviousDaysSummary.visibility = View.VISIBLE
            holder.tvPreviousDaysSummary.text = "📜 ${previousDaysLogs.size} گزارش از روزهای قبل"

            // نمایش دکمه Expand
            holder.btnExpand.visibility = View.VISIBLE
            if (isExpanded) {
                holder.btnExpand.text = "▲ بستن"
                holder.previousDaysContainer.visibility = View.VISIBLE
                holder.previousDaysListContainer.removeAllViews()

                // گروه‌بندی بر اساس تاریخ (به ترتیب نزولی - جدیدترین اول)
                val groupedByDate = previousDaysLogs.groupBy { it.date }
                    .toSortedMap(compareByDescending { it })

                for ((date, logsByDate) in groupedByDate) {
                    val dateHeader = TextView(holder.itemView.context)
                    dateHeader.text = "📅 $date"
                    dateHeader.setTextColor(holder.itemView.context.getColor(R.color.text_secondary))
                    dateHeader.textSize = 12f
                    dateHeader.setPadding(0, 8, 0, 4)
                    holder.previousDaysListContainer.addView(dateHeader)

                    for (log in logsByDate) {
                        val logView = LayoutInflater.from(holder.itemView.context)
                            .inflate(R.layout.item_previous_log_entry, holder.previousDaysListContainer, false)

                        val tvLogDate = logView.findViewById<TextView>(R.id.tvLogDate)
                        val tvLogTime = logView.findViewById<TextView>(R.id.tvLogTime)
                        val tvLogAction = logView.findViewById<TextView>(R.id.tvLogAction)
                        val tvLogUser = logView.findViewById<TextView>(R.id.tvLogUser)
                        val divider = logView.findViewById<View>(R.id.divider)

                        tvLogDate.text = log.date
                        tvLogTime.text = log.startTime
                        tvLogAction.text = log.actionDescription
                        tvLogUser.text = log.userName
                        tvLogUser.setBackgroundResource(R.drawable.bg_user_pill)
                        tvLogUser.setPadding(12, 2, 12, 2)

                        if (log == groupedByDate[date]?.last()) {
                            divider.visibility = View.GONE
                        } else {
                            divider.visibility = View.VISIBLE
                        }

                        holder.previousDaysListContainer.addView(logView)
                    }
                }
            } else {
                holder.btnExpand.text = "▼ مشاهده ${previousDaysLogs.size} گزارش از روزهای قبل"
                holder.previousDaysContainer.visibility = View.GONE
            }
        } else {
            holder.tvPreviousDaysSummary.visibility = View.GONE
            holder.previousDaysContainer.visibility = View.GONE
            holder.btnExpand.visibility = View.GONE
        }

        // ======== ۳. کلیک‌ها ========
        holder.btnExpand.setOnClickListener {
            if (isExpanded) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }

        holder.btnViewDetails.setOnClickListener {
            onItemClick(reportGroup)
        }

        holder.cardView.setOnClickListener {
            onItemClick(reportGroup)
        }
    }

    override fun getItemCount(): Int = reportGroups.size
}