package com.workshop.mechanicbb1.ui.task.list

import TaskModel
import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCalculator
import com.workshop.mechanicbb1.utils.evaluation.EvaluationRepository

class ExpandableTaskAdapter(
    private val tasks: List<TaskModel>,
    private val tabType: String,
    private val onItemClick: ((TaskModel) -> Unit)? = null
) : RecyclerView.Adapter<ExpandableTaskAdapter.ViewHolder>() {

    private val expandedPosition = mutableSetOf<Int>()
    private lateinit var userRole: String
    private val performanceCache = mutableMapOf<Int, List<TaskPerformanceItem>>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Header
        val tvId: TextView = itemView.findViewById(R.id.tvTaskId)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        val ivExpand: ImageView = itemView.findViewById(R.id.ivExpand)

        val divider: View = itemView.findViewById(R.id.divider)
        val detailLayout: View = itemView.findViewById(R.id.detailLayout)

        // Detail
        val tvDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
        val tvResponsible: TextView = itemView.findViewById(R.id.tvTaskResponsible)
        val tvAssignees: TextView = itemView.findViewById(R.id.tvTaskAssignees)
        val tvUnit: TextView = itemView.findViewById(R.id.tvTaskUnit)
        val tvPriority: TextView = itemView.findViewById(R.id.tvTaskPriority)
        val tvRequestDate: TextView = itemView.findViewById(R.id.tvRequestDate)
        val tvRequester: TextView = itemView.findViewById(R.id.tvRequester)
        val tvDeclarationMethod: TextView = itemView.findViewById(R.id.tvDeclarationMethod)
        val tvSystemNumber: TextView = itemView.findViewById(R.id.tvSystemNumber)
        val tvInitialReview: TextView = itemView.findViewById(R.id.tvInitialReview)

        // جدول کارکرد
        val rvTaskPerformance: RecyclerView = itemView.findViewById(R.id.rvTaskPerformance)
        val tvEmptyPerformance: TextView = itemView.findViewById(R.id.tvEmptyPerformance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_expandable, parent, false)

        val sharedPref = view.context.getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
        userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        val isExpanded = expandedPosition.contains(position)

        holder.tvId.text = "#${task.id}"
        holder.tvTitle.text = task.title

        setStripColor(holder, task)
        setTopStripColor(holder, task)

        holder.rvTaskPerformance.layoutManager = LinearLayoutManager(holder.itemView.context)

        if (isExpanded) {
            holder.ivExpand.setImageResource(R.drawable.ic_chevron_up)
            holder.divider.visibility = View.VISIBLE
            holder.detailLayout.visibility = View.VISIBLE
            showAllFields(holder, task)

            holder.itemView.findViewById<TextView>(R.id.tvPerformanceTitle).visibility = View.VISIBLE
            holder.itemView.findViewById<View>(R.id.tableHeader).visibility = View.VISIBLE
            holder.rvTaskPerformance.visibility = View.VISIBLE

            if (!performanceCache.containsKey(position)) {
                loadTaskPerformance(task.id, position, holder)
            } else {
                updateTaskPerformance(position, performanceCache[position]!!, holder)
            }
        }  else {
        holder.ivExpand.setImageResource(R.drawable.ic_chevron_down)
        holder.divider.visibility = View.VISIBLE

        holder.itemView.findViewById<TextView>(R.id.tvPerformanceTitle).visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.tableHeader).visibility = View.GONE
        holder.rvTaskPerformance.visibility = View.GONE
        holder.tvEmptyPerformance.visibility = View.GONE

        when (tabType) {
            "archived", "queue" -> {
                // ✅ یه خطی (فقط عنوان + ID)
                holder.detailLayout.visibility = View.GONE
            }
            else -> {
                // ✅ چند خطی (کارهای من، کارهای امروز)
                holder.detailLayout.visibility = View.VISIBLE
                showCollapsedFields(holder, task)
            }
        }
    }

        holder.itemView.findViewById<View>(R.id.headerLayout).setOnClickListener {
            if (isExpanded) {
                expandedPosition.remove(position)
            } else {
                expandedPosition.clear()
                expandedPosition.add(position)
            }
            notifyDataSetChanged()
        }

        if (onItemClick != null) {
            holder.itemView.setOnClickListener {
                onItemClick?.invoke(task)
            }
        }
    }

    private fun setStripColor(holder: ViewHolder, task: TaskModel) {
        val colorStrip = holder.itemView.findViewById<View>(R.id.colorStrip)
        val sharedPref = holder.itemView.context.getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
        val userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        if (task.status == "41" && userRole == Config.RoleCode.EMPLOYEE) {
            colorStrip.visibility = View.GONE
            return
        }

        if (task.hasUnseenReport && (userRole == Config.RoleCode.SUPERVISOR || userRole == Config.RoleCode.MANAGER)) {
            colorStrip.setBackgroundColor(Color.parseColor("#2196F3"))
            colorStrip.visibility = View.VISIBLE
            return
        }

        if (task.status == "41" && userRole == Config.RoleCode.SUPERVISOR) {
            colorStrip.setBackgroundColor(Color.parseColor("#9C27B0"))
            colorStrip.visibility = View.VISIBLE
            return
        }

        colorStrip.visibility = View.GONE
    }

    private fun setTopStripColor(holder: ViewHolder, task: TaskModel) {
        val topStrip = holder.itemView.findViewById<View>(R.id.topColorStrip)

        val urgency = task.urgency

        when {
            urgency == "خیلی زیاد" -> {
                topStrip.setBackgroundColor("#F44336".toColorInt())
                topStrip.visibility = View.VISIBLE
            }
            urgency == "زیاد" -> {
                topStrip.setBackgroundColor("#FFCC80".toColorInt())
                topStrip.visibility = View.VISIBLE
            }
            else -> {
                topStrip.visibility = View.GONE
            }
        }

        if (task.status == "41" && userRole == Config.RoleCode.EMPLOYEE) {
            topStrip.setBackgroundColor(Color.parseColor("#4CAF50"))
            topStrip.visibility = View.VISIBLE
            return
        }
    }

    // ============================================================
    // نمایش فیلدها در حالت بسته (همه‌ی تب‌ها)
    // ============================================================
    private fun showCollapsedFields(holder: ViewHolder, task: TaskModel) {
        // مخفی کردن فیلدهای منسوخ (حذف‌شده از Task)
        holder.tvDescription.visibility = View.GONE
        holder.tvRequester.visibility = View.GONE
        holder.tvDeclarationMethod.visibility = View.GONE
        holder.tvSystemNumber.visibility = View.GONE
        holder.tvInitialReview.visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.dividerDescription)?.visibility = View.GONE

        // مسئول
        if (task.responsible.isNotEmpty() && task.responsible != "0") {
            val responsibleName = UserCache.getName(task.responsible)
            holder.tvResponsible.text = "مسئول: $responsibleName"
            holder.tvResponsible.visibility = View.VISIBLE
        } else {
            holder.tvResponsible.visibility = View.GONE
        }

        // گروه انجام‌دهنده
        if (task.assignedTo.isNotEmpty() && task.assignedTo != "0") {
            val assigneeNames = task.assignedTo.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "0" }
                .map { UserCache.getName(it) }
            if (assigneeNames.isNotEmpty()) {
                holder.tvAssignees.text = "گروه: ${assigneeNames.joinToString("، ")}"
                holder.tvAssignees.visibility = View.VISIBLE
                holder.itemView.findViewById<View>(R.id.dividerAssignee)?.visibility = View.VISIBLE
            } else {
                holder.tvAssignees.visibility = View.GONE
                holder.itemView.findViewById<View>(R.id.dividerAssignee)?.visibility = View.GONE
            }
        } else {
            holder.tvAssignees.visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.dividerAssignee)?.visibility = View.GONE
        }

        // واحد (حذف شده)
        holder.tvUnit.visibility = View.GONE

        // ⬇️⬇️⬇️ scheduledDate ⬇️⬇️⬇️
        if (!task.scheduledDate.isNullOrEmpty()) {
            val today = DateUtils.gregorianToShamsi()
            val dayName = DateUtils.getDayNameFromDate(task.scheduledDate)
            val dateDisplay = if (dayName.isNotEmpty()) {
                "${task.scheduledDate} ($dayName)"
            } else {
                task.scheduledDate
            }

            val (label, color) = when {
                task.scheduledDate == today -> "📅 امروز: $dateDisplay" to "#2E7D32"
                task.scheduledDate < today -> "📅 عقب‌افتاده: $dateDisplay" to "#FF9800"
                else -> "📅 برنامه‌ریزی: $dateDisplay" to "#2196F3"
            }

            holder.tvRequestDate.text = label
            holder.tvRequestDate.setTextColor(color.toColorInt())
            holder.tvRequestDate.visibility = View.VISIBLE
        } else {
            holder.tvRequestDate.visibility = View.GONE
        }
        // ⬆️⬆️⬆️ پایان scheduledDate ⬆️⬆️⬆️

        // فوریت
        if (task.urgency.isNotEmpty() && task.urgency != "null" && task.urgency != "عادی") {
            holder.tvPriority.text = "فوریت: ${task.urgency}"
            when (task.urgency) {
                "خیلی زیاد" -> holder.tvPriority.setTextColor("#F44336".toColorInt())
                "زیاد" -> holder.tvPriority.setTextColor("#FF9800".toColorInt())
                else -> holder.tvPriority.setTextColor("#666666".toColorInt())
            }
            holder.tvPriority.visibility = View.VISIBLE
        } else {
            holder.tvPriority.visibility = View.GONE
        }
    }

    // ============================================================
    // نمایش همه‌ی فیلدها در حالت باز
    // ============================================================
    private fun showAllFields(holder: ViewHolder, task: TaskModel) {
        // توضیحات
        holder.tvDescription.text = task.description.ifEmpty { "توضیحاتی وارد نشده" }
        holder.tvDescription.visibility = View.VISIBLE

        holder.itemView.findViewById<View>(R.id.dividerDescription)?.visibility = View.VISIBLE
        holder.itemView.findViewById<View>(R.id.dividerAssignee)?.visibility = View.VISIBLE

        // مسئول
        if (task.responsible.isNotEmpty() && task.responsible != "0") {
            val responsibleName = UserCache.getName(task.responsible)

            val text = SpannableStringBuilder("مسئول انجام کار: ")
            text.setSpan(
                ForegroundColorSpan(Color.parseColor("#1565C0")),
                0, text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val nameSpan = SpannableStringBuilder(responsibleName)
            nameSpan.setSpan(
                ForegroundColorSpan(Color.parseColor("#333333")),
                0, nameSpan.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.append(nameSpan)

            holder.tvResponsible.text = text
            holder.tvResponsible.visibility = View.VISIBLE
        } else {
            holder.tvResponsible.visibility = View.GONE
        }

        // گروه
        if (task.assignedTo.isNotEmpty() && task.assignedTo != "0") {
            val assigneeNames = task.assignedTo.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "0" }
                .map { UserCache.getName(it) }
            if (assigneeNames.isNotEmpty()) {
                holder.tvAssignees.text = "گروه انجام دهنده: ${assigneeNames.joinToString("، ")}"
                holder.tvAssignees.visibility = View.VISIBLE
            } else {
                holder.tvAssignees.visibility = View.GONE
            }
        } else {
            holder.tvAssignees.visibility = View.GONE
        }

        // واحد (حذف شده)
        holder.tvUnit.visibility = View.GONE

        // scheduledDate
        if (!task.scheduledDate.isNullOrEmpty()) {
            val today = DateUtils.gregorianToShamsi()
            val dayName = DateUtils.getDayNameFromDate(task.scheduledDate)
            val dateDisplay = if (dayName.isNotEmpty()) {
                "${task.scheduledDate} ($dayName)"
            } else {
                task.scheduledDate
            }

            val (label, color) = when {
                task.scheduledDate == today -> "📅 تاریخ برنامه‌ریزی: امروز ($dateDisplay)" to "#2E7D32"
                task.scheduledDate < today -> "📅 تاریخ برنامه‌ریزی: عقب‌افتاده ($dateDisplay)" to "#FF9800"
                else -> "📅 تاریخ برنامه‌ریزی: آینده ($dateDisplay)" to "#2196F3"
            }

            holder.tvRequestDate.text = label
            holder.tvRequestDate.setTextColor(color.toColorInt())
            holder.tvRequestDate.visibility = View.VISIBLE
        } else {
            holder.tvRequestDate.text = "📅 تاریخ برنامه‌ریزی: تعیین نشده"
            holder.tvRequestDate.setTextColor("#999999".toColorInt())
            holder.tvRequestDate.visibility = View.VISIBLE
        }

        // فوریت
        if (task.urgency.isNotEmpty() && task.urgency != "null" && task.urgency != "عادی") {
            holder.tvPriority.text = "فوریت: ${task.urgency}"
            when (task.urgency) {
                "خیلی زیاد" -> holder.tvPriority.setTextColor("#F44336".toColorInt())
                "زیاد" -> holder.tvPriority.setTextColor("#FF9800".toColorInt())
                else -> holder.tvPriority.setTextColor("#666666".toColorInt())
            }
            holder.tvPriority.visibility = View.VISIBLE
        } else {
            holder.tvPriority.visibility = View.GONE
        }

        // فیلدهای منسوخ
        holder.tvRequester.visibility = View.GONE
        holder.tvDeclarationMethod.visibility = View.GONE
        holder.tvSystemNumber.visibility = View.GONE
        holder.tvInitialReview.visibility = View.GONE
    }

    // ===== جدول کارکرد =====
    private fun loadTaskPerformance(taskId: String, position: Int, holder: ViewHolder) {
        val repository = EvaluationRepository(holder.itemView.context)

        repository.getTasks(listOf(taskId)) { tasks ->
            repository.getTaskLogs(taskId) { logs ->
                if (logs.isEmpty()) {
                    holder.rvTaskPerformance.visibility = View.GONE
                    holder.tvEmptyPerformance.visibility = View.VISIBLE
                    return@getTaskLogs
                }

                EvaluationCache.getCoefficients(holder.itemView.context) { coefficients ->
                    if (coefficients == null) {
                        holder.tvEmptyPerformance.visibility = View.VISIBLE
                        holder.tvEmptyPerformance.text = "خطا در دریافت ضرایب"
                        return@getCoefficients
                    }

                    val indicators = EvaluationCalculator.aggregateIndicators(logs, tasks, coefficients)
                    val displayData = EvaluationCalculator.toDisplayFormat(indicators)

                    performanceCache[position] = displayData.map { item ->
                        TaskPerformanceItem(
                            userId = item.userId,
                            userName = item.userName,
                            rawTime = item.rawPhysical,
                            responsibility = item.finalResponsibility,
                            physical = item.finalPhysical,
                            technical = item.finalTechnical,
                            inspection = item.finalInspection
                        )
                    }

                    updateTaskPerformance(position, performanceCache[position]!!, holder)
                }
            }
        }
    }

    private fun updateTaskPerformance(
        position: Int,
        data: List<TaskPerformanceItem>,
        holder: ViewHolder
    ) {
        if (data.isEmpty()) {
            holder.rvTaskPerformance.visibility = View.GONE
            holder.tvEmptyPerformance.visibility = View.VISIBLE
            return
        }

        holder.rvTaskPerformance.visibility = View.VISIBLE
        holder.tvEmptyPerformance.visibility = View.GONE

        val visibility = EvaluationCalculator.ColumnVisibility(
            showResponsibility = data.any { it.responsibility.isNotEmpty() && it.responsibility != "00:00" && it.responsibility != "-" },
            showPhysical = data.any { it.physical.isNotEmpty() && it.physical != "00:00" && it.physical != "-" },
            showTechnical = data.any { it.technical.isNotEmpty() && it.technical != "00:00" && it.technical != "-" },
            showInspection = data.any { it.inspection.isNotEmpty() && it.inspection != "00:00" && it.inspection != "-" }
        )

        holder.itemView.findViewById<TextView>(R.id.headerResponsibility).visibility =
            if (visibility.showResponsibility) View.VISIBLE else View.GONE
        holder.itemView.findViewById<TextView>(R.id.headerPhysical).visibility =
            if (visibility.showPhysical) View.VISIBLE else View.GONE
        holder.itemView.findViewById<TextView>(R.id.headerTechnical).visibility =
            if (visibility.showTechnical) View.VISIBLE else View.GONE
        holder.itemView.findViewById<TextView>(R.id.headerInspection).visibility =
            if (visibility.showInspection) View.VISIBLE else View.GONE

        val adapter = TaskPerformanceAdapter(data, visibility)
        holder.rvTaskPerformance.adapter = adapter
    }

    inner class TaskPerformanceAdapter(
        private val data: List<TaskPerformanceItem>,
        private val visibility: EvaluationCalculator.ColumnVisibility
    ) : RecyclerView.Adapter<TaskPerformanceAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvPerfName)
            val tvRawTime: TextView = itemView.findViewById(R.id.tvPerfRawTime)
            val tvResponsibility: TextView = itemView.findViewById(R.id.tvPerfResponsibility)
            val tvPhysical: TextView = itemView.findViewById(R.id.tvPerfPhysical)
            val tvTechnical: TextView = itemView.findViewById(R.id.tvPerfTechnical)
            val tvInspection: TextView = itemView.findViewById(R.id.tvPerfInspection)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task_performance, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = data[position]
            holder.tvName.text = item.userName
            holder.tvRawTime.text = item.rawTime

            holder.tvResponsibility.visibility = if (visibility.showResponsibility) View.VISIBLE else View.GONE
            holder.tvPhysical.visibility = if (visibility.showPhysical) View.VISIBLE else View.GONE
            holder.tvTechnical.visibility = if (visibility.showTechnical) View.VISIBLE else View.GONE
            holder.tvInspection.visibility = if (visibility.showInspection) View.VISIBLE else View.GONE

            holder.tvResponsibility.text = if (item.responsibility.isEmpty()) "-" else item.responsibility
            holder.tvPhysical.text = if (item.physical.isEmpty()) "-" else item.physical
            holder.tvTechnical.text = if (item.technical.isEmpty()) "-" else item.technical
            holder.tvInspection.text = if (item.inspection.isEmpty()) "-" else item.inspection
        }

        override fun getItemCount(): Int = data.size
    }

    data class TaskPerformanceItem(
        val userId: String,
        val userName: String,
        val rawTime: String,
        val responsibility: String,
        val physical: String,
        val technical: String,
        val inspection: String
    )

    fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

    override fun getItemCount() = tasks.size
}