package com.workshop.mechanicbb1.ui.task.list

import TaskModel
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.UserCache
import androidx.core.graphics.toColorInt
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCalculator
import com.workshop.mechanicbb1.utils.evaluation.EvaluationRepository
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import org.json.JSONArray

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

        // تنظیم RecyclerView جدول کارکرد
        holder.rvTaskPerformance.layoutManager = LinearLayoutManager(holder.itemView.context)

        if (isExpanded) {
            holder.ivExpand.setImageResource(R.drawable.ic_chevron_up)
            holder.divider.visibility = View.VISIBLE
            holder.detailLayout.visibility = View.VISIBLE
            showAllFields(holder, task)

            // ✅ نمایش بخش جدول کارکرد
            holder.itemView.findViewById<TextView>(R.id.tvPerformanceTitle).visibility = View.VISIBLE
            holder.itemView.findViewById<View>(R.id.tableHeader).visibility = View.VISIBLE
            holder.rvTaskPerformance.visibility = View.VISIBLE

            // بارگذاری جدول کارکرد
            if (!performanceCache.containsKey(position)) {
                loadTaskPerformance(task.id, position, holder)
            } else {
                updateTaskPerformance(position, performanceCache[position]!!, holder)
            }
        } else {
            holder.ivExpand.setImageResource(R.drawable.ic_chevron_down)
            holder.divider.visibility = View.VISIBLE

            // ✅ مخفی کردن بخش جدول کارکرد
            holder.itemView.findViewById<TextView>(R.id.tvPerformanceTitle).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.tableHeader).visibility = View.GONE
            holder.rvTaskPerformance.visibility = View.GONE
            holder.tvEmptyPerformance.visibility = View.GONE

            when (tabType) {
                "unassigned" -> {
                    holder.detailLayout.visibility = View.GONE
                }
                "inProgress", "myCartable" -> {
                    holder.detailLayout.visibility = View.VISIBLE
                    showCollapsedFields(holder, task)
                }
                else -> {
                    holder.detailLayout.visibility = View.GONE
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

        // ✅ برای کارمند در وضعیت 41، نوار سمت راست رو مخفی کن
        if (task.status == "41" && userRole == Config.RoleCode.EMPLOYEE) {
            colorStrip.visibility = View.GONE
            return
        }

        // گزارش جدید دارد → آبی (برای سرشیفت و مدیر)
        if (task.hasUnseenReport && (userRole == Config.RoleCode.SUPERVISOR || userRole == Config.RoleCode.MANAGER)) {
            colorStrip.setBackgroundColor(Color.parseColor("#2196F3"))
            colorStrip.visibility = View.VISIBLE
            return
        }

        // ✅ درخواست تایید (status=41) برای سرشیفت → بنفش (تغییر از قرمز)
        if (task.status == "41" && userRole == Config.RoleCode.SUPERVISOR) {
            colorStrip.setBackgroundColor(Color.parseColor("#9C27B0"))  // ← بنفش
            colorStrip.visibility = View.VISIBLE
            return
        }

        // حالت عادی → مخفی
        colorStrip.visibility = View.GONE
    }
    private fun setTopStripColor(holder: ViewHolder, task: TaskModel) {
        val topStrip = holder.itemView.findViewById<View>(R.id.topColorStrip)

        // بررسی کن فوریت چقدر است
        val urgency = task.urgency

        when {
            urgency == "خیلی زیاد" -> {
                topStrip.setBackgroundColor("#F44336".toColorInt())  // قرمز
                topStrip.visibility = View.VISIBLE
            }
            urgency == "زیاد" -> {
                topStrip.setBackgroundColor("#FFCC80".toColorInt())  // نارنجی
                topStrip.visibility = View.VISIBLE
            }

            else -> {
                topStrip.visibility = View.GONE  // ← مخفی کن
            }
        }

        // ✅ وضعیت 41 برای کارمند → نوار سبز
        if (task.status == "41" && userRole == Config.RoleCode.EMPLOYEE) {
            topStrip.setBackgroundColor(Color.parseColor("#4CAF50"))
            topStrip.visibility = View.VISIBLE
            return
        }

    }

    // ========== تغییر اصلی اینجاست ==========
    // نمایش فیلدهای محدود در حالت بسته
    private fun showCollapsedFields(holder: ViewHolder, task: TaskModel) {
        // مخفی کردن همه فیلدها اول
        holder.tvDescription.visibility = View.GONE
        holder.tvAssignees.visibility = View.GONE
        holder.tvRequestDate.visibility = View.GONE
        holder.tvRequester.visibility = View.GONE
        holder.tvDeclarationMethod.visibility = View.GONE
        holder.tvSystemNumber.visibility = View.GONE
        holder.tvInitialReview.visibility = View.GONE
        // مخفی کردن دیوایدرها
        holder.itemView.findViewById<View>(R.id.dividerDescription)?.visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.dividerAssignee)?.visibility = View.GONE

        // مسئول - استفاده از UserCache
        // مسئول - با رنگ ملایم
        // مسئول
        if (task.responsible.isNotEmpty() && task.responsible != "0") {
            val responsibleName = UserCache.getName(task.responsible)
            holder.tvResponsible.text = "مسئول: $responsibleName"
            holder.tvResponsible.visibility = View.VISIBLE
        } else {
            holder.tvResponsible.visibility = View.GONE
        }

        // انجام‌دهندگان - استفاده از UserCache
        if (task.assignedTo.isNotEmpty()) {
            val assigneeNames = task.assignedTo.split(",").map {
                UserCache.getName(it.trim())  // ← تغییر
            }
            holder.tvAssignees.text = "گروه: ${assigneeNames.joinToString("، ")}"
            holder.tvAssignees.visibility = View.VISIBLE
        } else {
            holder.tvAssignees.visibility = View.GONE
        }

        // واحد - نمایش مستقیم نام مجموعه
        if (task.unit.isNotEmpty() && task.unit != "null") {
            val unitText = Config.UnitCode.getText(task.unit)
            if (unitText.isNotEmpty()) {
                // اگر واحد از نوع "مجموعه‌ها" است، مقدار sub_unit را نشان بده
                if (task.unit == "مجموعه\u200Cها" && task.sub_unit.isNotEmpty()) {
                    holder.tvUnit.text = "مجموعه: ${task.sub_unit}"
                } else {
                    holder.tvUnit.text = "واحد: $unitText"
                }
                holder.tvUnit.visibility = View.VISIBLE
            } else {
                holder.tvUnit.visibility = View.GONE
            }
        } else {
            holder.tvUnit.visibility = View.GONE
        }

        // فوریت
        if (task.urgency.isNotEmpty() && task.urgency != "null" && task.urgency != "عادی") {
            holder.tvPriority.text = "فوریت: ${task.urgency}"

            // ✅ شرط رنگ‌آمیزی همرنگ نوار
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

    // نمایش همه فیلدها در حالت باز
    private fun showAllFields(holder: ViewHolder, task: TaskModel) {
        // توضیحات
        holder.tvDescription.text = task.description.ifEmpty { "توضیحاتی وارد نشده" }
        holder.tvDescription.visibility = View.VISIBLE

        // نمایان کردن دیوایدرها
        holder.itemView.findViewById<View>(R.id.dividerDescription)?.visibility = View.VISIBLE
        holder.itemView.findViewById<View>(R.id.dividerAssignee)?.visibility = View.VISIBLE

        //  نمایش روز هفته
        if (task.request_date.isNotEmpty()) {
            val dayOfWeek = DateUtils.getDayNameFromDate(task.request_date)
            val dateDisplay = if (dayOfWeek.isNotEmpty()) {
                "${task.request_date} ($dayOfWeek)"
            } else {
                task.request_date
            }
            holder.tvRequestDate.text = "تاریخ اعلام: $dateDisplay"
            holder.tvRequestDate.visibility = View.VISIBLE
        } else {
            holder.tvRequestDate.visibility = View.GONE
        }

        // مسئول - استفاده از UserCache
        // مسئول - با رنگ ملایم
        if (task.responsible.isNotEmpty() && task.responsible != "0") {
            val responsibleName = UserCache.getName(task.responsible)

            // ایجاد یک SpannableStringBuilder برای رنگ‌آمیزی
            val text = SpannableStringBuilder("مسئول انجام کار: ")

            // رنگ آبی تیره برای برچسب "مسئول:"
            text.setSpan(
                ForegroundColorSpan(Color.parseColor("#1565C0")),
                0, text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // اضافه کردن نام با رنگ تیره‌تر
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

        // انجام‌دهندگان - استفاده از UserCache
        if (task.assignedTo.isNotEmpty()) {
            val assigneeNames = task.assignedTo.split(",").map {
                UserCache.getName(it.trim())  // ← تغییر
            }
            holder.tvAssignees.text = "گروه انجام دهنده: ${assigneeNames.joinToString("، ")}"
            holder.tvAssignees.visibility = View.VISIBLE
        } else {
            holder.tvAssignees.visibility = View.GONE
        }

        // واحد - نمایش مستقیم نام مجموعه
        if (task.unit.isNotEmpty() && task.unit != "null") {
            val unitText = Config.UnitCode.getText(task.unit)
            if (unitText.isNotEmpty()) {
                if (task.unit == "مجموعه‌ها" && task.sub_unit.isNotEmpty()) {
                    holder.tvUnit.text = "مجموعه: ${task.sub_unit}"
                } else {
                    holder.tvUnit.text = "واحد: $unitText"
                }
                holder.tvUnit.visibility = View.VISIBLE
            } else {
                holder.tvUnit.visibility = View.GONE
            }
        } else {
            holder.tvUnit.visibility = View.GONE
        }

        // فوریت
        if (task.urgency.isNotEmpty() && task.urgency != "null" && task.urgency != "عادی") {
            holder.tvPriority.text = "فوریت: ${task.urgency}"

            // ✅ شرط رنگ‌آمیزی همرنگ نوار
            when (task.urgency) {
                "خیلی زیاد" -> holder.tvPriority.setTextColor("#F44336".toColorInt())
                "زیاد" -> holder.tvPriority.setTextColor("#FF9800".toColorInt())
                else -> holder.tvPriority.setTextColor("#666666".toColorInt())
            }

            holder.tvPriority.visibility = View.VISIBLE
        } else {
            holder.tvPriority.visibility = View.GONE
        }

        // تاریخ اعلام
        if (task.request_date.isNotEmpty()) {
            val displayDate = task.request_date.replace("-", "/")
            holder.tvRequestDate.text = "تاریخ اعلام: $displayDate"
            holder.tvRequestDate.visibility = View.VISIBLE
        } else {
            holder.tvRequestDate.visibility = View.GONE
        }

        // صادرکننده
        if (task.requester.isNotEmpty()) {
            holder.tvRequester.text = "صادرکننده: ${task.requester}"
            holder.tvRequester.visibility = View.VISIBLE
        } else {
            holder.tvRequester.visibility = View.GONE
        }

        // نحوه اعلام
        if (task.declaration_method.isNotEmpty()) {
            holder.tvDeclarationMethod.text = "نحوه اعلام: ${task.declaration_method}"
            holder.tvDeclarationMethod.visibility = View.VISIBLE
        } else {
            holder.tvDeclarationMethod.visibility = View.GONE
        }

        // شماره سامانه
        if (task.system_request_number.isNotEmpty()) {
            holder.tvSystemNumber.text = "شماره سامانه: ${task.system_request_number}"
            holder.tvSystemNumber.visibility = View.VISIBLE
        } else {
            holder.tvSystemNumber.visibility = View.GONE
        }

        // بررسی اولیه
        if (task.initial_review.isNotEmpty()) {
            holder.tvInitialReview.text = "بررسی اولیه: ${task.initial_review}"
            holder.tvInitialReview.visibility = View.VISIBLE
        } else {
            holder.tvInitialReview.visibility = View.GONE
        }
    }

// ===== متدهای جدید برای جدول کارکرد =====

    private fun loadTaskPerformance(taskId: String, position: Int, holder: ViewHolder) {
        val repository = EvaluationRepository(holder.itemView.context)

        repository.getTasks(listOf(taskId)) { tasks ->
            repository.getTaskLogs(taskId) { logs ->
                if (logs.isEmpty()) {
                    Log.d("TaskPerf", "⚠️ logs is empty")
                    holder.rvTaskPerformance.visibility = View.GONE
                    holder.tvEmptyPerformance.visibility = View.VISIBLE
                    return@getTaskLogs
                }

                EvaluationCache.getCoefficients(holder.itemView.context) { coefficients ->
                    Log.d("TaskPerf", "coefficients: $coefficients")

                    if (coefficients == null) {
                        Log.d("TaskPerf", "⚠️ coefficients is null")
                        holder.tvEmptyPerformance.visibility = View.VISIBLE
                        holder.tvEmptyPerformance.text = "خطا در دریافت ضرایب"
                        return@getCoefficients
                    }

                    val indicators = EvaluationCalculator.aggregateIndicators(logs, tasks, coefficients)

                    val displayData = EvaluationCalculator.toDisplayFormat(indicators)

                    if (displayData.isEmpty()) {
                        Log.d("TaskPerf", "⚠️ displayData is empty! Checking each indicator...")
                        for (indicator in indicators) {
                            Log.d("TaskPerf", "indicator: userId=${indicator.userId}, userName=${indicator.userName}, rawPhysical=${indicator.rawPhysical}, finalPhysical=${indicator.finalPhysical}")
                        }
                    }

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

                    Log.d("TaskPerf", "performanceCache[position] size: ${performanceCache[position]?.size}")

                    updateTaskPerformance(
                        position = position,
                        data = performanceCache[position]!!,
                        holder = holder
                    )
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
            showResponsibility = data.any {
                it.responsibility.isNotEmpty() && it.responsibility != "00:00" && it.responsibility != "-"
            },
            showPhysical = data.any {
                it.physical.isNotEmpty() && it.physical != "00:00" && it.physical != "-"
            },
            showTechnical = data.any {
                it.technical.isNotEmpty() && it.technical != "00:00" && it.technical != "-"
            },
            showInspection = data.any {
                it.inspection.isNotEmpty() && it.inspection != "00:00" && it.inspection != "-"
            }
        )

        // تنظیم visibility هدر
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

    // ===== آداپتور جدید برای جدول کارکرد =====
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

    // ===== TaskPerformanceItem با فیلد جدید =====
    data class TaskPerformanceItem(
        val userId: String,
        val userName: String,
        val rawTime: String,              // ساعت خام (همیشه نمایش داده میشه)
        val responsibility: String,       // مسئولیت (با ضریب)
        val physical: String,             // فیزیکی (با ضریب)
        val technical: String,            // فنی (با ضریب)
        val inspection: String            // بررسی (با ضریب)
    )


    fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

    override fun getItemCount() = tasks.size

}
