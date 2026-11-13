package com.workshop.mechanicbb1.ui.task.list

import TaskModel
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.viewholder.WorkOrderMiniAdapter
import com.workshop.mechanicbb1.ui.workorder.WorkOrderDetailActivity
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCalculator
import com.workshop.mechanicbb1.utils.evaluation.EvaluationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adapter قابل‌گسترش برای لیست کارها.
 *
 * ساختار هر کارت:
 *   ┌─ نوار بالایی (فوریت) ──────────────────┐
 *   │ ┌─ هدر (کلیک‌پذیر → TaskDetailActivity) │
 *   │ │ ID + Badge وضعیت + اولویت            │
 *   │ │ عنوان                                 │
 *   │ │ مسئول + گروه + تاریخ                 │
 *   │ └───────────────────────────────────────┘
 *   │ ┌─ detailLayout (فقط وقتی بازه) ────────┐
 *   │ │ توضیحات + فوریت                       │
 *   │ │ درخواست‌های مرتبط (WorkOrders)        │
 *   │ │ جدول کارکرد                           │
 *   │ └───────────────────────────────────────┘
 *   │ [ مشاهده جزئیات ▼ ]  ← moreButton        │
 *   └─────────────────────────────────────────┘
 *
 * @param tasks لیست کارها
 * @param tabType نوع تب ("myCartable" | "inProgress" | "queue" | "archived")
 * @param scope CoroutineScope برای لود WorkOrders (از Fragment پاس داده می‌شه)
 * @param onItemClick کلیک روی هدر → TaskDetailActivity
 */
class ExpandableTaskAdapter(
    private val tasks: List<TaskModel>,
    private val tabType: String,
    private val scope: CoroutineScope,
    private val onItemClick: ((TaskModel) -> Unit)? = null
) : RecyclerView.Adapter<ExpandableTaskAdapter.ViewHolder>() {

    /** موقعیت‌های بازشده (فقط یکی می‌تونه باز باشه) */
    private val expandedPosition = mutableSetOf<Int>()

    /** نقش کاربر (EMPLOYEE / SUPERVISOR / MANAGER) */
    private lateinit var userRole: String

    /** کش جدول کارکرد برای هر position */
    private val performanceCache = mutableMapOf<Int, List<TaskPerformanceItem>>()

    /** کش WorkOrders برای هر task ID */
    private val workOrdersCache = mutableMapOf<String, List<com.workshop.mechanicbb1.data.model.WorkOrderModel>>()

    // =========================================================================
    // ViewHolder
    // =========================================================================
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ===== هدر =====
        val tvId: TextView = itemView.findViewById(R.id.tvTaskId)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val tvTaskPriority: TextView = itemView.findViewById(R.id.tvTaskPriority)
        val tvResponsible: TextView = itemView.findViewById(R.id.tvTaskResponsible)
        val tvAssignees: TextView = itemView.findViewById(R.id.tvTaskAssignees)
        val tvRequestDate: TextView = itemView.findViewById(R.id.tvRequestDate)
        val tvMoreText: TextView = itemView.findViewById(R.id.tvMoreText)
        val moreButton: View = itemView.findViewById(R.id.moreButton)

        // ===== Divider + detail =====
        val divider: View = itemView.findViewById(R.id.divider)
        val detailLayout: View = itemView.findViewById(R.id.detailLayout)

        // ===== detail: توضیحات + فوریت =====
        val tvDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
        val tvTaskUrgencyDetail: TextView = itemView.findViewById(R.id.tvTaskUrgencyDetail)

        // ===== detail: WorkOrders =====
        val workOrdersSection: View = itemView.findViewById(R.id.workOrdersSection)
        val tvWorkOrdersTitle: TextView = itemView.findViewById(R.id.tvWorkOrdersTitle)
        val rvWorkOrders: RecyclerView = itemView.findViewById(R.id.rvWorkOrders)

        // ===== detail: جدول کارکرد =====
        val rvTaskPerformance: RecyclerView = itemView.findViewById(R.id.rvTaskPerformance)
        val tvEmptyPerformance: TextView = itemView.findViewById(R.id.tvEmptyPerformance)
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_expandable_item, parent, false)

        val prefs = view.context.getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
        userRole = prefs.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        val isExpanded = expandedPosition.contains(position)

        // ===== هدر =====
        holder.tvId.text = "#${task.id}"
        holder.tvTitle.text = task.title
        applyStatusStyle(holder, task)   // badge + نوار کناری + اولویت
        setTopStripColor(holder, task)   // نوار بالایی (فوریت، فقط برای کارهای جاری)
        bindQuickInfo(holder, task)      // مسئول + گروه + تاریخ

        // ===== «مشاهده جزئیات» / «بستن» =====
        holder.tvMoreText.text = if (isExpanded) "بستن ▲" else "مشاهده جزئیات ▼"

        // ===== Divider + detail =====
        holder.divider.visibility = View.VISIBLE
        if (isExpanded) {
            holder.detailLayout.visibility = View.VISIBLE
            showAllFields(holder, task)
            loadWorkOrders(holder, task)            // ← درخواست‌های مرتبط
            loadTaskPerformance(task.id, position, holder)   // ← جدول کارکرد
        } else {
            holder.detailLayout.visibility = View.GONE
            holder.workOrdersSection.visibility = View.GONE
            holder.itemView.findViewById<TextView>(R.id.tvPerformanceTitle).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.tableHeader).visibility = View.GONE
            holder.rvTaskPerformance.visibility = View.GONE
            holder.tvEmptyPerformance.visibility = View.GONE
        }

        // ===== کلیک‌ها =====
        // هدر → TaskDetailActivity
        holder.itemView.findViewById<View>(R.id.headerLayout).setOnClickListener {
            onItemClick?.invoke(task)
        }
        // «مشاهده جزئیات» → باز/بسته inline
        holder.moreButton.setOnClickListener {
            toggleExpansion(position)
        }
    }

    override fun getItemCount(): Int = tasks.size

    // =========================================================================
    // بخش ۱: هدر — وضعیت (Badge + نوار + اولویت)
    // =========================================================================

    /** اطلاعات بصری هر وضعیت */
    private data class StatusStyle(
        val label: String,
        val color: Int,       // رنگ نوار کناری
        val bgColor: Int      // پس‌زمینه‌ی badge
    )

    /**
     * استایل وضعیت رو برمی‌گردونه.
     * اولویت: اگه گزارش جدید (unseen) داره → آبی، وگرنه بر اساس status.
     */
    private fun getStatusStyle(task: TaskModel): StatusStyle {
        // ۱. گزارش جدید (سرشیفت/مدیر)
        if (task.hasUnseenReport &&
            (userRole == Config.RoleCode.SUPERVISOR || userRole == Config.RoleCode.MANAGER)) {
            return StatusStyle(
                label = "🔵 گزارش جدید",
                color = Color.parseColor("#2196F3"),
                bgColor = Color.parseColor("#E3F2FD")
            )
        }

        // ۲. بر اساس status
        return when (task.status) {
            "2" -> StatusStyle("در حال انجام", Color.parseColor("#2E7D32"), Color.parseColor("#E8F5E9"))
            "21", "31" -> StatusStyle("⏳ منتظر گزارش", Color.parseColor("#E65100"), Color.parseColor("#FFF3E0"))
            "3" -> StatusStyle("⛔ متوقف", Color.parseColor("#C62828"), Color.parseColor("#FFEBEE"))
            "41" -> StatusStyle("✓ اتمام", Color.parseColor("#1565C0"), Color.parseColor("#E3F2FD"))
            "1" -> StatusStyle("ارجاع نشده", Color.parseColor("#757575"), Color.parseColor("#F5F5F5"))
            else -> StatusStyle(Config.StatusCode.getText(task.status), Color.parseColor("#9E9E9E"), Color.parseColor("#EEEEEE"))
        }
    }

    /**
     * رنگ نوار کناری + badge وضعیت + اولویت (با رنگ‌بندی).
     */
    private fun applyStatusStyle(holder: ViewHolder, task: TaskModel) {
        val style = getStatusStyle(task)

        // نوار کناری
        val colorStrip = holder.itemView.findViewById<View>(R.id.colorStrip)
        colorStrip.setBackgroundColor(style.color)
        colorStrip.visibility = View.VISIBLE

        // Badge
        holder.tvStatusBadge.text = style.label
        holder.tvStatusBadge.setTextColor(style.color)
        holder.tvStatusBadge.background.setTint(style.bgColor)

        // اولویت (با رنگ‌بندی بر اساس مقدار)
        holder.tvTaskPriority.text = "🎯 ${task.priority}"
        val (priorityColor, priorityBg) = when {
            task.priority >= 80 -> "#C62828" to "#FFEBEE"   // قرمز
            task.priority >= 60 -> "#E65100" to "#FFF3E0"   // نارنجی
            task.priority >= 40 -> "#1565C0" to "#E3F2FD"   // آبی
            else -> "#666666" to "#F5F5F5"                  // خاکستری
        }
        holder.tvTaskPriority.setTextColor(priorityColor.toColorInt())
        holder.tvTaskPriority.background.setTint(priorityBg.toColorInt())
    }

    /**
     * نوار بالایی (فوریت) — فقط برای کارهای جاری.
     * برای 4 (اتمام درخواست)، 41 (ارسال به سرشیفت)، 5 (بایگانی) نمایش داده نمی‌شه.
     */
    private fun setTopStripColor(holder: ViewHolder, task: TaskModel) {
        val topStrip = holder.itemView.findViewById<View>(R.id.topColorStrip)

        // ❌ برای وضعیت‌های پایانی، نوار بالایی معنی نداره
        if (task.status in listOf("4", "41", "5")) {
            topStrip.visibility = View.GONE
            return
        }

        // ✅ بر اساس فوریت
        when (task.urgency) {
            "خیلی زیاد" -> {
                topStrip.setBackgroundColor("#F44336".toColorInt())
                topStrip.visibility = View.VISIBLE
            }
            "زیاد" -> {
                topStrip.setBackgroundColor("#FFCC80".toColorInt())
                topStrip.visibility = View.VISIBLE
            }
            else -> {
                topStrip.visibility = View.GONE
            }
        }
    }

    /**
     * Quick Info تو هدر: مسئول + گروه + تاریخ برنامه‌ریزی.
     */
    @SuppressLint("SetTextI18n")
    private fun bindQuickInfo(holder: ViewHolder, task: TaskModel) {
        // مسئول
        if (task.responsible.isNotEmpty() && task.responsible != "0") {
            holder.tvResponsible.text = "👤 مسئول: ${UserCache.getName(task.responsible)}"
            holder.tvResponsible.visibility = View.VISIBLE
        } else {
            holder.tvResponsible.visibility = View.GONE
        }

        // گروه
        if (task.assignedTo.isNotEmpty() && task.assignedTo != "0") {
            val names = task.assignedTo.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "0" }
                .map { UserCache.getName(it) }
            if (names.isNotEmpty()) {
                holder.tvAssignees.text = "👥 گروه: ${names.joinToString("، ")}"
                holder.tvAssignees.visibility = View.VISIBLE
            } else {
                holder.tvAssignees.visibility = View.GONE
            }
        } else {
            holder.tvAssignees.visibility = View.GONE
        }

        // تاریخ برنامه‌ریزی
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
    }

    // =========================================================================
    // بخش ۲: detail — توضیحات + فوریت
    // =========================================================================

    @SuppressLint("SetTextI18n")
    private fun showAllFields(holder: ViewHolder, task: TaskModel) {
        // توضیحات
        holder.tvDescription.text = task.description.ifEmpty { "توضیحاتی وارد نشده" }

        // فوریت (تو detail)
        if (task.urgency.isNotEmpty() && task.urgency != "null" && task.urgency != "عادی") {
            holder.tvTaskUrgencyDetail.text = "فوریت: ${task.urgency}"
            when (task.urgency) {
                "خیلی زیاد" -> holder.tvTaskUrgencyDetail.setTextColor("#F44336".toColorInt())
                "زیاد" -> holder.tvTaskUrgencyDetail.setTextColor("#FF9800".toColorInt())
                else -> holder.tvTaskUrgencyDetail.setTextColor("#666666".toColorInt())
            }
            holder.tvTaskUrgencyDetail.visibility = View.VISIBLE
        } else {
            holder.tvTaskUrgencyDetail.visibility = View.GONE
        }
    }

    // =========================================================================
    // بخش ۳: detail — WorkOrders (درخواست‌های مرتبط)
    // =========================================================================

    /**
     * لود WorkOrders مرتبط با کار.
     * از کش استفاده می‌کنه (اگه قبلاً لود شده).
     */
    private fun loadWorkOrders(holder: ViewHolder, task: TaskModel) {
        // از کش
        val cached = workOrdersCache[task.id]
        if (cached != null) {
            bindWorkOrders(holder, cached)
            return
        }

        // لود از Room
        scope.launch {
            try {
                val repo = TaskLocalRepository(holder.itemView.context)
                val workOrders = repo.getWorkOrdersForTaskOnce(task.id)
                workOrdersCache[task.id] = workOrders
                bindWorkOrders(holder, workOrders)
            } catch (e: Exception) {
                holder.workOrdersSection.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindWorkOrders(holder: ViewHolder, workOrders: List<com.workshop.mechanicbb1.data.model.WorkOrderModel>) {
        if (workOrders.isEmpty()) {
            holder.workOrdersSection.visibility = View.GONE
            return
        }

        holder.workOrdersSection.visibility = View.VISIBLE
        holder.tvWorkOrdersTitle.text = "🔗 درخواست‌های مرتبط (${workOrders.size})"

        holder.rvWorkOrders.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.rvWorkOrders.adapter = WorkOrderMiniAdapter(
            items = workOrders,
            onClick = { wo ->
                val intent = Intent(holder.itemView.context, WorkOrderDetailActivity::class.java).apply {
                    putExtra("WORK_ORDER_ID", wo.id)
                }
                holder.itemView.context.startActivity(intent)
            }
        )
    }

    // =========================================================================
    // بخش ۴: detail — جدول کارکرد
    // =========================================================================

    private fun loadTaskPerformance(taskId: String, position: Int, holder: ViewHolder) {
        // از کش
        if (performanceCache.containsKey(position)) {
            updateTaskPerformance(position, performanceCache[position]!!, holder)
            return
        }

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
                        holder.tvEmptyPerformance.text = "خطا در دریافت ضرایب"
                        holder.tvEmptyPerformance.visibility = View.VISIBLE
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
                            technicalParticipation = item.finalTechnicalParticipation,   // ← جدید
                            inspection = item.finalInspection
                        )
                    }

                    updateTaskPerformance(position, performanceCache[position]!!, holder)
                }
            }
        }
    }

    private fun updateTaskPerformance(position: Int, data: List<TaskPerformanceItem>, holder: ViewHolder) {
        holder.itemView.findViewById<TextView>(R.id.tvPerformanceTitle).visibility = View.VISIBLE
        holder.itemView.findViewById<View>(R.id.tableHeader).visibility = View.VISIBLE

        if (data.isEmpty()) {
            holder.rvTaskPerformance.visibility = View.GONE
            holder.tvEmptyPerformance.visibility = View.VISIBLE
            return
        }

        holder.rvTaskPerformance.visibility = View.VISIBLE
        holder.tvEmptyPerformance.visibility = View.GONE

        // تعیین ستون‌های قابل نمایش
        val visibility = EvaluationCalculator.ColumnVisibility(
            showResponsibility = data.any { it.responsibility.isNotEmpty() && it.responsibility != "00:00" && it.responsibility != "-" },
            showPhysical = data.any { it.physical.isNotEmpty() && it.physical != "00:00" && it.physical != "-" },
            showTechnical = data.any { it.technical.isNotEmpty() && it.technical != "00:00" && it.technical != "-" },
            showTechnicalParticipation = data.any { it.technicalParticipation.isNotEmpty() && it.technicalParticipation != "00:00" && it.technicalParticipation != "-" },  // ← جدید
            showInspection = data.any { it.inspection.isNotEmpty() && it.inspection != "00:00" && it.inspection != "-" }
        )

        holder.itemView.findViewById<TextView>(R.id.headerResponsibility).visibility =
            if (visibility.showResponsibility) View.VISIBLE else View.GONE
        holder.itemView.findViewById<TextView>(R.id.headerPhysical).visibility =
            if (visibility.showPhysical) View.VISIBLE else View.GONE
        holder.itemView.findViewById<TextView>(R.id.headerTechnical).visibility =
            if (visibility.showTechnical) View.VISIBLE else View.GONE
        holder.itemView.findViewById<TextView>(R.id.headerTechnicalParticipation).visibility =
            if (visibility.showTechnicalParticipation) View.VISIBLE else View.GONE   // ← جدید
        holder.itemView.findViewById<TextView>(R.id.headerInspection).visibility =
            if (visibility.showInspection) View.VISIBLE else View.GONE

        holder.rvTaskPerformance.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.rvTaskPerformance.adapter = TaskPerformanceAdapter(data, visibility)
    }

    // =========================================================================
    // بخش ۵: expand/collapse
    // =========================================================================

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleExpansion(position: Int) {
        if (expandedPosition.contains(position)) {
            expandedPosition.remove(position)
        } else {
            expandedPosition.clear()   // فقط یکی باز
            expandedPosition.add(position)
        }
        notifyDataSetChanged()
    }

    // =========================================================================
    // Adapter داخلی: جدول کارکرد
    // =========================================================================
    inner class TaskPerformanceAdapter(
        private val data: List<TaskPerformanceItem>,
        private val visibility: EvaluationCalculator.ColumnVisibility
    ) : RecyclerView.Adapter<TaskPerformanceAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvPerfName)
            val tvRawTime: TextView = itemView.findViewById(R.id.tvPerfRawTime)
            val tvResponsibility: TextView = itemView.findViewById(R.id.tvPerfResponsibility)
            val tvPhysical: TextView = itemView.findViewById(R.id.tvPerfPhysical)
            val tvTechnical: TextView = itemView.findViewById(R.id.tvPerfTechnical)
            val tvTechnicalParticipation: TextView = itemView.findViewById(R.id.tvPerfTechnicalParticipation)   // ← جدید
            val tvInspection: TextView = itemView.findViewById(R.id.tvPerfInspection)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task_performance, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = data[position]
            holder.tvName.text = item.userName
            holder.tvRawTime.text = item.rawTime

            holder.tvResponsibility.visibility = if (visibility.showResponsibility) View.VISIBLE else View.GONE
            holder.tvPhysical.visibility = if (visibility.showPhysical) View.VISIBLE else View.GONE
            holder.tvTechnical.visibility = if (visibility.showTechnical) View.VISIBLE else View.GONE
            holder.tvTechnicalParticipation.visibility = if (visibility.showTechnicalParticipation) View.VISIBLE else View.GONE   // ← جدید
            holder.tvInspection.visibility = if (visibility.showInspection) View.VISIBLE else View.GONE

            holder.tvResponsibility.text = item.responsibility.ifEmpty { "-" }
            holder.tvPhysical.text = item.physical.ifEmpty { "-" }
            holder.tvTechnical.text = item.technical.ifEmpty { "-" }
            holder.tvTechnicalParticipation.text = item.technicalParticipation.ifEmpty { "-" }   // ← جدید
            holder.tvInspection.text = item.inspection.ifEmpty { "-" }
        }

        override fun getItemCount(): Int = data.size
    }

    // =========================================================================
    // Model داخلی: یه ردیف جدول کارکرد
    // =========================================================================
    data class TaskPerformanceItem(
        val userId: String,
        val userName: String,
        val rawTime: String,
        val responsibility: String,
        val physical: String,
        val technical: String,
        val technicalParticipation: String,   // ← جدید
        val inspection: String
    )

    // =========================================================================
    // Extension
    // =========================================================================
    fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
}