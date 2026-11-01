package com.workshop.mechanicbb1.ui.task.detail.viewholder

import TaskModel
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.util.TaskDetailUiUtils
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache

class TaskInfoViewHolder(
    itemView: View,
    private val onStatusChange: (String) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    // ============================================================
    // ویوها
    // ============================================================
    private val tvTaskId: TextView = itemView.findViewById(R.id.tvTaskId)
    private val tvTaskTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
    private val tvTaskDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
    private val cardDescription: CardView = itemView.findViewById(R.id.cardDescription)

    private val tvTaskResponsible: TextView = itemView.findViewById(R.id.tvTaskResponsible)
    private val layoutResponsible: LinearLayout = itemView.findViewById(R.id.layoutResponsible)

    private val tvTaskAssignees: TextView = itemView.findViewById(R.id.tvTaskAssignees)
    private val layoutAssignees: LinearLayout = itemView.findViewById(R.id.layoutAssignees)

    private val tvTaskUrgency: TextView = itemView.findViewById(R.id.tvTaskUrgency)
    private val layoutUrgency: LinearLayout = itemView.findViewById(R.id.layoutUrgency)

    private val tvTaskPriority: TextView = itemView.findViewById(R.id.tvTaskPriority)
    private val layoutPriority: LinearLayout = itemView.findViewById(R.id.layoutPriority)

    private val tvScheduledDate: TextView = itemView.findViewById(R.id.tvScheduledDate)
    private val layoutScheduledDate: LinearLayout = itemView.findViewById(R.id.layoutScheduledDate)

    private val tvReferredBy: TextView = itemView.findViewById(R.id.tvReferredBy)
    private val layoutReferredBy: LinearLayout = itemView.findViewById(R.id.layoutReferredBy)

    // وضعیت
    private val statusSection: View = itemView.findViewById(R.id.statusSection)
    private val sliderStatus: Slider = itemView.findViewById(R.id.sliderStatus)
    private val tvStatusLabel: TextView = itemView.findViewById(R.id.tvStatusLabel)

    // WorkOrderها
    private val workOrdersSection: View = itemView.findViewById(R.id.workOrdersSection)
    private val rvWorkOrders: RecyclerView = itemView.findViewById(R.id.rvWorkOrders)
    private val tvWorkOrdersTitle: TextView = itemView.findViewById(R.id.tvWorkOrdersTitle)

    // مشاهده‌کنندگان
    private val seenByContainer: LinearLayout = itemView.findViewById(R.id.seenByContainer)

    // دکمه‌های عملیات
    private val taskActionButtons: LinearLayout = itemView.findViewById(R.id.taskActionButtons)
    private val btnEditTask: ImageButton = itemView.findViewById(R.id.btnEditTask)
    private val btnDeleteTask: ImageButton = itemView.findViewById(R.id.btnDeleteTask)
    private val btnReferTask: ImageButton = itemView.findViewById(R.id.btnReferTask)

    // ============================================================
    // bind
    // ============================================================
    @SuppressLint("SetTextI18n")
    fun bind(
        task: TaskModel,
        userRole: String,
        currentUserId: String,
        workOrders: List<WorkOrderModel>,
        onEditTask: () -> Unit,
        onDeleteTask: () -> Unit,
        onReferTask: () -> Unit,
        onWorkOrderClick: (WorkOrderModel) -> Unit
    ) {
        tvTaskId.text = "#${task.id}"
        tvTaskTitle.text = task.title

        // توضیحات
        if (task.description.isNotEmpty()) {
            tvTaskDescription.text = task.description
            cardDescription.visibility = View.VISIBLE
        } else {
            cardDescription.visibility = View.GONE
        }

        bindScheduledDate(task)
        bindPriority(task)

        // مسئول
        if (task.responsible.isNotEmpty() && task.responsible != "0") {
            tvTaskResponsible.text = UserCache.getName(task.responsible)
            layoutResponsible.visibility = View.VISIBLE
        } else {
            layoutResponsible.visibility = View.GONE
        }

        bindAssignees(task)
        bindUrgency(task)

        // ارجاع‌دهنده
        if (task.referredBy.isNotEmpty()) {
            tvReferredBy.text = UserCache.getName(task.referredBy)
            layoutReferredBy.visibility = View.VISIBLE
        } else {
            layoutReferredBy.visibility = View.GONE
        }

        setupStatusSection(task, userRole, currentUserId)
        setupWorkOrdersSection(workOrders, onWorkOrderClick)

        // دکمه‌های عملیات
        val canManageTask = userRole == Config.RoleCode.MANAGER || userRole == Config.RoleCode.SUPERVISOR
        val isArchived = task.status == "5"

        if (canManageTask && !isArchived) {
            taskActionButtons.visibility = View.VISIBLE
            btnEditTask.setOnClickListener { onEditTask() }
            btnDeleteTask.setOnClickListener { onDeleteTask() }
            btnReferTask.setOnClickListener { onReferTask() }
        } else {
            taskActionButtons.visibility = View.GONE
        }

        displaySeenBy(task.seenBy)
    }

    // ============================================================
    // Bind helpers
    // ============================================================
    private fun bindScheduledDate(task: TaskModel) {
        if (!task.scheduledDate.isNullOrEmpty()) {
            val today = DateUtils.gregorianToShamsi()
            val dayName = DateUtils.getDayNameFromDate(task.scheduledDate)
            val dateDisplay = if (dayName.isNotEmpty()) {
                "${task.scheduledDate} ($dayName)"
            } else {
                task.scheduledDate
            }
            val color = when {
                task.scheduledDate == today -> "#2E7D32"
                task.scheduledDate < today -> "#FF9800"
                else -> "#2196F3"
            }
            tvScheduledDate.text = dateDisplay
            tvScheduledDate.setTextColor(Color.parseColor(color))
            layoutScheduledDate.visibility = View.VISIBLE
        } else {
            layoutScheduledDate.visibility = View.GONE
        }
    }

    private fun bindPriority(task: TaskModel) {
        if (task.priority == 50) {
            layoutPriority.visibility = View.GONE
        } else {
            layoutPriority.visibility = View.VISIBLE
            tvTaskPriority.text = task.priority.toString()
            when {
                task.priority >= 80 -> tvTaskPriority.setTextColor(Color.parseColor("#D32F2F"))
                task.priority >= 50 -> tvTaskPriority.setTextColor(Color.parseColor("#FF9800"))
                else -> tvTaskPriority.setTextColor(Color.parseColor("#4CAF50"))
            }
        }
    }

    private fun bindAssignees(task: TaskModel) {
        if (task.assignedTo.isNotEmpty() && task.assignedTo != "0") {
            val assigneeNames = task.assignedTo.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "0" }
                .map { UserCache.getName(it) }
            if (assigneeNames.isNotEmpty()) {
                tvTaskAssignees.text = assigneeNames.joinToString("، ")
                layoutAssignees.visibility = View.VISIBLE
            } else {
                layoutAssignees.visibility = View.GONE
            }
        } else {
            layoutAssignees.visibility = View.GONE
        }
    }

    private fun bindUrgency(task: TaskModel) {
        if (task.urgency.isNotEmpty() && task.urgency != "عادی") {
            tvTaskUrgency.text = task.urgency
            layoutUrgency.visibility = View.VISIBLE
            when (task.urgency) {
                "خیلی زیاد" -> tvTaskUrgency.setTextColor(Color.parseColor("#D32F2F"))
                "زیاد" -> tvTaskUrgency.setTextColor(Color.parseColor("#FF9800"))
                else -> tvTaskUrgency.setTextColor(Color.parseColor("#333333"))
            }
        } else {
            layoutUrgency.visibility = View.GONE
        }
    }

    // ============================================================
    // وضعیت — Material Slider
    // ============================================================
    private fun setupStatusSection(task: TaskModel, userRole: String, currentUserId: String) {
        val isArchived = task.status == "5"
        val isQueued = task.status == "1"   // ✅ جدید
        val canChangeStatus = (task.responsible == currentUserId) ||
                (userRole == Config.RoleCode.SUPERVISOR) ||
                (userRole == Config.RoleCode.MANAGER)

        if (isArchived || isQueued) {   // ✅ isQueued اضافه شد
            statusSection.visibility = View.GONE
            return
        }

        statusSection.visibility = View.VISIBLE

        sliderStatus.isEnabled = canChangeStatus
        sliderStatus.alpha = if (canChangeStatus) 1.0f else 0.5f

        sliderStatus.clearOnChangeListeners()

        sliderStatus.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val newStatus = valueToStatus(value.toInt())
            updateStatusUI(newStatus)
            onStatusChange(newStatus)
        }

        sliderStatus.post {
            sliderStatus.value = statusToValue(task.status)
            updateStatusUI(task.status)
        }
    }

    private fun statusToValue(status: String): Float = when (status) {
        "3" -> 0f    // متوقف → چپ
        "2" -> 1f    // ادامه دارد → وسط
        "41" -> 2f   // اتمام کار → راست
        else -> 1f   // پیش‌فرض: وسط
    }

    private fun valueToStatus(value: Int): String = when (value) {
        0 -> "3"     // چپ → متوقف
        1 -> "2"     // وسط → ادامه دارد
        2 -> "41"    // راست → اتمام کار
        else -> "2"
    }

    private fun updateStatusUI(status: String) {
        val (text, color) = when (status) {
            "2" -> "ادامه دارد" to Color.parseColor("#1565C0")
            "3" -> "متوقف" to Color.parseColor("#C62828")
            "41" -> "اتمام کار" to Color.parseColor("#2E7D32")
            else -> "نامشخص" to Color.parseColor("#78909C")
        }

        tvStatusLabel.text = text
        tvStatusLabel.setTextColor(color)
        tvStatusLabel.background.setTint(lightenColor(color, 0.9f))

        sliderStatus.thumbTintList = ColorStateList.valueOf(color)
        sliderStatus.trackActiveTintList = ColorStateList.valueOf(color)
        sliderStatus.tickTintList = ColorStateList.valueOf(color)
        sliderStatus.setHaloTintList(ColorStateList.valueOf(Color.TRANSPARENT))
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun Int.dpToPx(): Int =
        (this * itemView.context.resources.displayMetrics.density).toInt()

    // ============================================================
    // WorkOrderها
    // ============================================================
    @SuppressLint("SetTextI18n")
    private fun setupWorkOrdersSection(
        workOrders: List<WorkOrderModel>,
        onWorkOrderClick: (WorkOrderModel) -> Unit
    ) {
        if (workOrders.isEmpty()) {
            workOrdersSection.visibility = View.GONE
            return
        }

        workOrdersSection.visibility = View.VISIBLE
        tvWorkOrdersTitle.text = "🔗 درخواست‌های مرتبط (${workOrders.size})"

        rvWorkOrders.layoutManager = LinearLayoutManager(itemView.context)
        rvWorkOrders.adapter = WorkOrderMiniAdapter(workOrders) { wo ->
            onWorkOrderClick(wo)
        }
    }

    // ============================================================
    // مشاهده‌کنندگان
    // ============================================================
    @SuppressLint("SetTextI18n")
    private fun displaySeenBy(seenBy: List<String>) {
        seenByContainer.removeAllViews()

        if (seenBy.isEmpty()) {
            seenByContainer.visibility = View.GONE
            return
        }

        seenByContainer.visibility = View.VISIBLE

        val maxDisplay = 6
        val toShow = seenBy.take(maxDisplay)
        val remaining = seenBy.size - maxDisplay

        toShow.forEach { userId ->
            val userName = UserCache.getName(userId)
            val firstLetter = if (userName.isNotEmpty() && userName != "نامشخص")
                userName.firstOrNull()?.toString()?.uppercase() ?: "?"
            else "?"

            val circleView = TaskDetailUiUtils.createSeenByCircle(
                itemView.context, firstLetter, userId, userName, 22
            )
            seenByContainer.addView(circleView)
        }

        if (remaining > 0) {
            val moreView = TextView(itemView.context).apply {
                text = "+$remaining"
                textSize = 10f
                setTextColor(Color.parseColor("#78909C"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    marginStart = 4.dpToPx()
                }
            }
            seenByContainer.addView(moreView)
        }
    }
}