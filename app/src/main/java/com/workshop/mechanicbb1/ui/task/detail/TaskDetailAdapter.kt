package com.workshop.mechanicbb1.ui.task.detail

import TaskModel
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.material.button.MaterialButton
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.evaluation.EvaluationActivity
import com.workshop.mechanicbb1.ui.task.log.AddLogActivity
import com.workshop.mechanicbb1.ui.task.log.TaskLogDetailActivity
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.SeenItem
import com.workshop.mechanicbb1.utils.SeenManager
import com.workshop.mechanicbb1.utils.UserCache
import com.workshop.mechanicbb1.utils.VolleySingleton
import org.json.JSONArray
import org.json.JSONObject


class TaskDetailAdapter(
    private val task: TaskModel,
    private val logs: List<TaskLogModel>,
    private val onEditLogClick: (TaskLogModel) -> Unit,
    private val onDeleteLogClick: (TaskLogModel) -> Unit,
    private val onAddLogClick: () -> Unit,
    private val onRefreshLogs: () -> Unit,
    private val onCompleteTaskClick: () -> Unit,
    private val showCompleteButton: Boolean = false,
    private val canAddLog: Boolean = true,
    private val currentUserId: String = "",
    private val userRole: String = "",
    private val buttonMode: String = "HIDDEN",
    private val onButtonClick: () -> Unit = {},
    private val onEditTaskClick: () -> Unit = {},
    private val onDeleteTaskClick: () -> Unit = {},
    private val onReferTaskClick: () -> Unit = {},
    private val onStatusChange: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_TASK_INFO = 0
        private const val TYPE_ADD_LOG = 1      // ← جابه‌جا شد
        private const val TYPE_TASK_LOG = 2
    }

    override fun getItemCount(): Int {
        val hasAddLog = buttonMode != "HIDDEN"
        return 1 + (if (hasAddLog) 1 else 0) + logs.size  // info + addLog + logs
    }

    override fun getItemViewType(position: Int): Int {
        val hasAddLog = buttonMode != "HIDDEN"

        return when (position) {
            0 -> TYPE_TASK_INFO
            1 -> {
                if (hasAddLog) TYPE_ADD_LOG else TYPE_TASK_LOG
            }
            else -> {
                if (hasAddLog) {
                    // اگر addLog وجود داره، لاگ‌ها از ایندکس 2 شروع می‌شن
                    TYPE_TASK_LOG
                } else {
                    // اگر addLog وجود نداره، لاگ‌ها از ایندکس 1 شروع می‌شن
                    TYPE_TASK_LOG
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TASK_INFO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_task_info_static, parent, false)
                TaskInfoViewHolder(view)
            }
            TYPE_ADD_LOG -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_add_log, parent, false)

                val currentUserId = view.context
                    .getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
                    .getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
                val isResponsible = task.responsible == currentUserId

                AddLogViewHolder(
                    view,
                    onAddLogClick,
                    onCompleteTaskClick,
                    showCompleteButton,
                    canAddLog,
                    buttonMode,
                    onButtonClick,
                    onStatusChange,
                    task.status,
                    isResponsible,
                    logs.isNotEmpty()
                )
            }
            TYPE_TASK_LOG -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_task_log, parent, false)
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
                onEditTask = onEditTaskClick,
                onDeleteTask = onDeleteTaskClick,
                onReferTask = onReferTaskClick
            )
            is AddLogViewHolder -> {
                // کاری نداره، دکمه قبلاً در onCreateViewHolder ست شده
            }
            is TaskLogViewHolder -> {
                // محاسبه ایندکس صحیح لاگ
                val addLogOffset = if (hasAddLog) 1 else 0
                val logPosition = position - 1 - addLogOffset
                if (logPosition in logs.indices) {
                    val log = logs[logPosition]
                    val sharedPref = holder.itemView.context.getSharedPreferences(
                        Config.PrefKeys.USER_PREFS,
                        Context.MODE_PRIVATE
                    )
                    val currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
                    val userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""
                    holder.bind(log, task, currentUserId, userRole, onDeleteLogClick) {
                        onRefreshLogs()
                    }
                }
            }
        }
    }


    // ViewHolder برای شرح کار
    class TaskInfoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // TextView‌ها
        private val tvTaskId: TextView = itemView.findViewById(R.id.tvTaskId)
        private val tvTaskTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        private val tvTaskDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
        private val tvTaskResponsible: TextView = itemView.findViewById(R.id.tvTaskResponsible)

        private val tvTaskPriority: TextView = itemView.findViewById(R.id.tvTaskPriority)
        private val layoutPriority: LinearLayout = itemView.findViewById(R.id.layoutPriority)

        private val tvTaskAssignees: TextView = itemView.findViewById(R.id.tvTaskAssignees)
        private val tvTaskUnit: TextView = itemView.findViewById(R.id.tvTaskUnit)
        private val tvTaskUrgency: TextView = itemView.findViewById(R.id.tvTaskUrgency)
        private val tvRequestDate: TextView = itemView.findViewById(R.id.tvRequestDate)
        private val tvRequester: TextView = itemView.findViewById(R.id.tvRequester)
        private val tvDeclarationMethod: TextView = itemView.findViewById(R.id.tvDeclarationMethod)
        private val tvSystemNumber: TextView = itemView.findViewById(R.id.tvSystemNumber)
        private val tvInitialReview: TextView = itemView.findViewById(R.id.tvInitialReview)
        private val tvReferredBy: TextView = itemView.findViewById(R.id.tvReferredBy)
        private val seenByContainer: LinearLayout = itemView.findViewById(R.id.seenByContainer)

        // Layoutهای جدید برای کنترل visibility
        private val layoutResponsible: LinearLayout = itemView.findViewById(R.id.layoutResponsible)
        private val layoutAssignees: LinearLayout = itemView.findViewById(R.id.layoutAssignees)
        private val layoutUnit: LinearLayout = itemView.findViewById(R.id.layoutUnit)
        private val layoutUrgency: LinearLayout = itemView.findViewById(R.id.layoutUrgency)
        private val layoutRequestDate: LinearLayout = itemView.findViewById(R.id.layoutRequestDate)
        private val layoutRequester: LinearLayout = itemView.findViewById(R.id.layoutRequester)
        private val layoutDeclarationMethod: LinearLayout = itemView.findViewById(R.id.layoutDeclarationMethod)
        private val layoutSystemNumber: LinearLayout = itemView.findViewById(R.id.layoutSystemNumber)
        private val layoutReferredBy: LinearLayout = itemView.findViewById(R.id.layoutReferredBy)

        // CardView‌ها
        private val cardDescription: CardView = itemView.findViewById(R.id.cardDescription)
        private val cardInitialReview: CardView = itemView.findViewById(R.id.cardInitialReview)

        private val taskActionButtons: LinearLayout = itemView.findViewById(R.id.taskActionButtons)
        private val btnEditTask: ImageButton = itemView.findViewById(R.id.btnEditTask)
        private val btnDeleteTask: ImageButton = itemView.findViewById(R.id.btnDeleteTask)
        private val btnReferTask: ImageButton = itemView.findViewById(R.id.btnReferTask)

        @SuppressLint("SetTextI18n")
        fun bind(
            task: TaskModel,
            userRole: String,
            onEditTask: () -> Unit,
            onDeleteTask: () -> Unit,
            onReferTask: () -> Unit
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

            // اولویت
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

            // مسئول
            if (task.responsible.isNotEmpty() && task.responsible != "0") {
                val responsibleName = UserCache.getName(task.responsible)
                tvTaskResponsible.text = responsibleName
                layoutResponsible.visibility = View.VISIBLE
            } else {
                layoutResponsible.visibility = View.GONE
            }

            // گروه انجام‌دهنده
            if (task.assignedTo.isNotEmpty()) {
                val assigneeNames = task.assignedTo.split(",").map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { UserCache.getName(it) }
                tvTaskAssignees.text = assigneeNames.joinToString("، ")
                layoutAssignees.visibility = View.VISIBLE
            } else {
                layoutAssignees.visibility = View.GONE
            }

            // واحد
            if (task.unit.isNotEmpty() && task.unit != "null") {
                val unitText = Config.UnitCode.getText(task.unit)
                if (unitText.isNotEmpty()) {
                    tvTaskUnit.text = unitText
                    layoutUnit.visibility = View.VISIBLE
                } else {
                    layoutUnit.visibility = View.GONE
                }
            } else {
                layoutUnit.visibility = View.GONE
            }

            // فوریت
            if (task.urgency.isNotEmpty() && task.urgency != "عادی") {
                tvTaskUrgency.text = task.urgency
                layoutUrgency.visibility = View.VISIBLE
                // رنگ متن فوریت
                when (task.urgency) {
                    "خیلی زیاد" -> tvTaskUrgency.setTextColor(Color.parseColor("#D32F2F"))
                    "زیاد" -> tvTaskUrgency.setTextColor(Color.parseColor("#FF9800"))
                    else -> tvTaskUrgency.setTextColor(Color.parseColor("#333333"))
                }
            } else {
                layoutUrgency.visibility = View.GONE
            }

            // تاریخ اعلام
            if (task.request_date.isNotEmpty()) {
                val displayDate = task.request_date.replace("-", "/")
                tvRequestDate.text = displayDate
                layoutRequestDate.visibility = View.VISIBLE
            } else {
                layoutRequestDate.visibility = View.GONE
            }

            // صادرکننده
            if (task.requester.isNotEmpty()) {
                tvRequester.text = task.requester
                layoutRequester.visibility = View.VISIBLE
            } else {
                layoutRequester.visibility = View.GONE
            }

            // نحوه اعلام
            if (task.declaration_method.isNotEmpty()) {
                tvDeclarationMethod.text = task.declaration_method
                layoutDeclarationMethod.visibility = View.VISIBLE
            } else {
                layoutDeclarationMethod.visibility = View.GONE
            }

            // شماره سامانه
            if (task.system_request_number.isNotEmpty()) {
                tvSystemNumber.text = task.system_request_number
                layoutSystemNumber.visibility = View.VISIBLE
            } else {
                layoutSystemNumber.visibility = View.GONE
            }

            // بررسی اولیه
            if (task.initial_review.isNotEmpty()) {
                tvInitialReview.text = task.initial_review
                cardInitialReview.visibility = View.VISIBLE
            } else {
                cardInitialReview.visibility = View.GONE
            }

            // ارجاع‌دهنده
            if (task.referredBy.isNotEmpty()) {
                val referredByName = UserCache.getName(task.referredBy)
                tvReferredBy.text = referredByName
                layoutReferredBy.visibility = View.VISIBLE
            } else {
                layoutReferredBy.visibility = View.GONE
            }

            // ====== دکمه‌های عملیاتی ======
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

            // مشاهده‌کنندگان
            displaySeenBy(task.seenBy)
        }


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

                val circleView = createCircleWithLetter(firstLetter, userId)
                seenByContainer.addView(circleView)
            }

            if (remaining > 0) {
                val moreView = TextView(itemView.context).apply {
                    text = "+$remaining"
                    textSize = 10f
                    setTextColor(Color.GRAY)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginStart = 4.dpToPx()
                    }
                }
                seenByContainer.addView(moreView)
            }
        }

        private fun createCircleWithLetter(letter: String, userId: String): View {
            return TextView(itemView.context).apply {
                text = letter
                textSize = 10f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER

                val size = 20.dpToPx()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = 4.dpToPx()
                }

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(getColorForUserId(userId))
                }

                // اضافه کردن Tooltip برای نمایش نام کامل وقتی طولانی نگه می‌دارند
                val userName = UserCache.getName(userId)
                if (userName.isNotEmpty() && userName != "نامشخص") {
                    setOnLongClickListener {
                        Toast.makeText(context, userName, Toast.LENGTH_SHORT).show()
                        true
                    }
                }
            }
        }

        private fun getColorForUserId(userId: String): Int {
            val colors = listOf(
                "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
                "#2196F3", "#03A9F4", "#00BCD4", "#009688",
                "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                "#FFC107", "#FF9800", "#FF5722", "#795548"
            )
            val index = userId.hashCode().mod(colors.size)
            return Color.parseColor(colors[index])
        }

        private fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
    }


    // ViewHolder برای هر گزارش
    class TaskLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLogSummary: TextView = itemView.findViewById(R.id.tvLogSummary)
        private val divider: View = itemView.findViewById(R.id.divider)
        private val detailLayout: View = itemView.findViewById(R.id.detailLayout)
        private val tvActionDescription: TextView = itemView.findViewById(R.id.tvActionDescription)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvWorkers: TextView = itemView.findViewById(R.id.tvWorkers)
        private val tvNewStatus: TextView = itemView.findViewById(R.id.tvNewStatus)
        private val btnAddComment: Button = itemView.findViewById(R.id.btnAddComment)
        private val cardLog: CardView = itemView.findViewById(R.id.cardLog)

        // بخش ارزیابی
        private val evaluationContainer: LinearLayout = itemView.findViewById(R.id.evaluationContainer)
        private val evaluationHeader: LinearLayout = itemView.findViewById(R.id.evaluationHeader)
        private val evaluationDetail: LinearLayout = itemView.findViewById(R.id.evaluationDetail)
        private val ivEvaluationExpand: ImageView = itemView.findViewById(R.id.ivEvaluationExpand)
        private val dividerEvaluation: View = itemView.findViewById(R.id.dividerEvaluation)
        private val tvHeatLevel: TextView = itemView.findViewById(R.id.tvHeatLevel)
        private val tvPollutionLevel: TextView = itemView.findViewById(R.id.tvPollutionLevel)
        private val tvPhysicalDifficulty: TextView = itemView.findViewById(R.id.tvPhysicalDifficulty)
        private val tvTechnicalComplexity: TextView = itemView.findViewById(R.id.tvTechnicalComplexity)

        // دکمه‌های عملیاتی
        private val actionButtonsContainer: LinearLayout = itemView.findViewById(R.id.actionButtonsContainer)
        private val btnEvaluate: Button = itemView.findViewById(R.id.btnEvaluate)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        // دیوایدرها
        private val dividerDescription: View = itemView.findViewById(R.id.dividerDescription)
        private val dividerWorkers: View = itemView.findViewById(R.id.dividerWorkers)
        private val dividerSeenBy: View = itemView.findViewById(R.id.dividerSeenBy)
        private val dividerButtons: View = itemView.findViewById(R.id.dividerButtons)

        private var isEvaluationExpanded = false
        private lateinit var currentLog: TaskLogModel
        private lateinit var currentTask: TaskModel
        private lateinit var onRefreshCallback: () -> Unit
        private lateinit var currentUserId: String
        private lateinit var onDeleteCallback: (TaskLogModel) -> Unit

        private val statusContainer: LinearLayout = itemView.findViewById(R.id.statusContainer)
        private val statusDot: View = itemView.findViewById(R.id.statusDot)


        @SuppressLint("SetTextI18n")
        fun bind(
            log: TaskLogModel,
            task: TaskModel,
            currentUserId: String,
            userRole: String,
            onDelete: (TaskLogModel) -> Unit,
            onRefresh: () -> Unit
        ) {
            this.currentLog = log
            this.currentTask = task
            this.currentUserId = currentUserId
            this.onDeleteCallback = onDelete
            this.onRefreshCallback = onRefresh

            // رنگ کارت برای گزارش‌های جدید
            val isSeenByCurrentUser = currentLog.seenBy.contains(currentUserId)
            if (!isSeenByCurrentUser && (userRole == Config.RoleCode.SUPERVISOR || userRole == Config.RoleCode.MANAGER)) {
                cardLog.setCardBackgroundColor(Color.parseColor("#F0F5FF"))
            } else {
                //cardLog.setCardBackgroundColor(Color.WHITE)
            }

            // هدر
            val timeRange = if (log.startTime.isNotEmpty() || log.endTime.isNotEmpty()) {
                " (${log.startTime} - ${log.endTime})"
            } else {
                ""
            }
            tvLogSummary.text = "📋 گزارش ${log.date} - ${log.userName}$timeRange"

            // در bind
            val dayOfWeek = DateUtils.getDayNameFromDate(log.date)
            val dateDisplay = if (dayOfWeek.isNotEmpty()) {
                "${log.date} ($dayOfWeek)"
            } else {
                log.date
            }

            tvLogSummary.text = "📋 گزارش $dateDisplay - ${log.userName}$timeRange"

            // شرح اقدام
            tvActionDescription.text = log.actionDescription
            if (log.actionDescription.contains("برگشت داده شد")) {
                tvActionDescription.setBackgroundColor(Color.parseColor("#FFEBEE"))
                tvActionDescription.setTextColor(Color.parseColor("#D32F2F"))
            }

            // مدت زمان
            val durationText = calculateDurationText(log.startTime, log.endTime)
            if (durationText.isNotEmpty()) {
                tvDuration.text = "⏱ $durationText"
                tvDuration.visibility = View.VISIBLE
            } else {
                tvDuration.visibility = View.GONE
            }

            // ====== گروه انجام‌دهنده (فقط در صورت مشارکت جزئی) ======
            val assignedToFullList = task.assignedTo.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val responsibleId = task.responsible

            val logWorkers = log.assignedUsers.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val allMembers = assignedToFullList.toMutableSet()
            if (responsibleId.isNotEmpty() && responsibleId != "0") {
                allMembers.add(responsibleId)
            }

            val allParticipated = allMembers.isNotEmpty() && allMembers.all { it in logWorkers }

            if (!allParticipated && logWorkers.isNotEmpty()) {
                val workerNames = logWorkers.mapNotNull {
                    val name = UserCache.getName(it)
                    if (name != "نامشخص" && !name.startsWith("کاربر")) name else null
                }

                if (workerNames.isNotEmpty()) {
                    val fullText = workerNames.joinToString("، ")
                    tvWorkers.text = "👥 گروه انجام‌دهنده: $fullText"
                    tvWorkers.visibility = View.VISIBLE
                    dividerWorkers.visibility = View.VISIBLE

                    val responsibleName = UserCache.getName(responsibleId)
                    if (responsibleName.isNotEmpty() && responsibleName != "نامشخص" && responsibleName in workerNames) {
                        val spannable = android.text.SpannableString(tvWorkers.text)
                        val startIndex = tvWorkers.text.indexOf(responsibleName)
                        if (startIndex != -1) {
                            spannable.setSpan(
                                android.text.style.ForegroundColorSpan(Color.parseColor("#1565C0")),
                                startIndex,
                                startIndex + responsibleName.length,
                                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            tvWorkers.text = spannable
                        }
                    }
                } else {
                    tvWorkers.visibility = View.GONE
                    dividerWorkers.visibility = View.GONE
                }
            } else {
                tvWorkers.visibility = View.GONE
                dividerWorkers.visibility = View.GONE
            }

            // ====== وضعیت جدید ======
            if (log.newStatus.isNotEmpty()) {
                val statusText = Config.StatusCode.getText(log.newStatus)
                tvNewStatus.text = "وضعیت جدید: $statusText"
                statusContainer.visibility = View.VISIBLE

                when (log.newStatus) {
                    Config.StatusCode.IN_PROGRESS -> statusDot.setBackgroundColor(Color.parseColor("#1565C0"))
                    Config.StatusCode.BLOCKED -> statusDot.setBackgroundColor(Color.parseColor("#C62828"))
                    Config.StatusCode.SENT_TO_SUPERVISOR -> statusDot.setBackgroundColor(Color.parseColor("#6A1B9A"))
                    Config.StatusCode.ARCHIVED -> statusDot.setBackgroundColor(Color.parseColor("#4E342E"))
                    else -> statusDot.setBackgroundColor(Color.parseColor("#78909C"))
                }
            } else {
                statusContainer.visibility = View.GONE
            }

            // نمایش مشاهده‌کنندگان
            displaySeenBy(log.seenBy)

            // بخش ارزیابی
            setupEvaluationSection(log)

            // نظرات
            displayComments(log.comments, currentUserId)

            // دکمه ثبت نظر
            val isArchived = task.status == "5"
            if (!isArchived) {
                btnAddComment.visibility = View.VISIBLE
                btnAddComment.setOnClickListener {
                    showAddCommentDialog()
                }
            } else {
                btnAddComment.visibility = View.GONE
            }

            // دکمه‌های عملیاتی
            setupActionButtons(log, task, currentUserId, userRole, onDelete)

            // کلیک روی کارت
            itemView.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, TaskLogDetailActivity::class.java).apply {
                    putExtra("TASK_ID", task.id)
                    putExtra("LOG_ID", log.id)
                    putExtra("IS_TASK_ARCHIVED", task.status == "5")
                }
                context.startActivity(intent)
            }

            // ثبت دیده شدن
            if (::currentUserId.isInitialized && currentUserId.isNotEmpty()) {
                SeenManager.markAsSeen(
                    itemView.context,
                    currentUserId,
                    listOf(SeenItem("TASK_LOG", currentLog.id))
                )
            }
        }

        // ==================== بخش ارزیابی ====================
        @SuppressLint("SetTextI18n")
        private fun setupEvaluationSection(log: TaskLogModel) {
            val hasEvaluationData = log.heatLevel > 30 ||
                    log.pollutionLevel > 0 ||
                    log.physicalDifficulty > 0 ||
                    log.technicalComplexity > 0

            if (!hasEvaluationData) {
                evaluationContainer.visibility = View.GONE
                return
            }

            evaluationContainer.visibility = View.VISIBLE
            dividerEvaluation.visibility = View.VISIBLE

            // هدر ارزیابی
            val tvEvaluationTitle = evaluationHeader.findViewById<TextView>(R.id.tvEvaluationTitle)
            tvEvaluationTitle.text = "📊 ارزیابی گزارش"
            tvEvaluationTitle.setTextColor(Color.parseColor("#555555"))

            // دمای هوا
            if (log.heatLevel > 30) {
                tvHeatLevel.text = "🌡️ دمای هوا: ${log.heatLevel}°C"
                tvHeatLevel.visibility = View.VISIBLE
            } else {
                tvHeatLevel.visibility = View.GONE
            }

            // میزان آلودگی
            if (log.pollutionLevel > 0) {
                tvPollutionLevel.text = "🏭 میزان آلودگی: ${log.pollutionLevel} ppm"
                tvPollutionLevel.visibility = View.VISIBLE
            } else {
                tvPollutionLevel.visibility = View.GONE
            }

            // سختی فیزیکی
            if (log.physicalDifficulty > 0) {
                tvPhysicalDifficulty.text = "💪 سختی فیزیکی: ${Config.Evaluation.getPhysicalText(log.physicalDifficulty)}"
                tvPhysicalDifficulty.visibility = View.VISIBLE
            } else {
                tvPhysicalDifficulty.visibility = View.GONE
            }

            // پیچیدگی فنی
            if (log.technicalComplexity > 0) {
                tvTechnicalComplexity.text = "🔧 پیچیدگی فنی: ${Config.Evaluation.getTechnicalText(log.technicalComplexity)}"
                tvTechnicalComplexity.visibility = View.VISIBLE
            } else {
                tvTechnicalComplexity.visibility = View.GONE
            }

            // حالت بسته پیش‌فرض
            isEvaluationExpanded = false
            evaluationDetail.visibility = View.GONE
            ivEvaluationExpand.setImageResource(R.drawable.ic_chevron_down)
            ivEvaluationExpand.setColorFilter(Color.parseColor("#888888"))

            evaluationHeader.setOnClickListener {
                isEvaluationExpanded = !isEvaluationExpanded
                if (isEvaluationExpanded) {
                    evaluationDetail.visibility = View.VISIBLE
                    ivEvaluationExpand.setImageResource(R.drawable.ic_chevron_up)
                } else {
                    evaluationDetail.visibility = View.GONE
                    ivEvaluationExpand.setImageResource(R.drawable.ic_chevron_down)
                }
            }
        }

        // ==================== دکمه‌های عملیاتی ====================
        private fun setupActionButtons(
            log: TaskLogModel,
            task: TaskModel,
            currentUserId: String,
            userRole: String,
            onDelete: (TaskLogModel) -> Unit
        ) {
            val isArchived = task.status == "5"
            val canEvaluate = !isArchived && (log.userId == currentUserId || userRole == Config.RoleCode.SUPERVISOR)
            val canEditDelete = !isArchived && log.userId == currentUserId

            var hasVisibleButton = false

            if (canEvaluate) {
                btnEvaluate.visibility = View.VISIBLE
                btnEvaluate.setOnClickListener {
                    val intent = Intent(itemView.context, EvaluationActivity::class.java).apply {
                        putExtra("TASK_ID", task.id)
                        putExtra("LOG_ID", log.id)
                        putExtra("TASK_TITLE", task.title)
                        putExtra("TASK_DATE", log.date)
                        putExtra("TASK_START_TIME", log.startTime)
                        putExtra("TASK_END_TIME", log.endTime)
                        putExtra("TASK_DESCRIPTION", log.actionDescription)
                        putExtra("ASSIGNED_USERS", log.assignedUsers)
                        putExtra("DURATION_MINUTES", calculateDurationInMinutes(log.startTime, log.endTime))
                        putExtra("IS_FROM_DETAIL", true)
                    }
                    itemView.context.startActivity(intent)
                }
                hasVisibleButton = true
            } else {
                btnEvaluate.visibility = View.GONE
            }

            if (canEditDelete) {
                btnEdit.visibility = View.VISIBLE
                btnEdit.setOnClickListener {
                    val intent = Intent(itemView.context, AddLogActivity::class.java).apply {
                        putExtra("IS_EDIT_MODE", true)
                        putExtra("LOG_ID", log.id)
                        putExtra("TASK_ID", log.taskId)
                        putExtra("TITLE", task.title)
                        putExtra("DESC", task.description)
                        putExtra("CREATOR", task.creator)
                        putExtra("DATE", task.createDate)
                        putExtra("RESPONSIBLE", task.responsible)
                        putExtra("ASSIGNED_TO", task.assignedTo)
                        putExtra("UNIT", task.unit)
                        putExtra("PRIORITY", task.priority)
                        putExtra("SUB_UNIT", task.sub_unit)
                        putExtra("DECLARATION_METHOD", task.declaration_method)
                        putExtra("REQUESTER", task.requester)
                        putExtra("REQUEST_DATE", task.request_date)
                        putExtra("INITIAL_REVIEW", task.initial_review)
                        putExtra("SYSTEM_REQUEST_NUMBER", task.system_request_number)
                        putExtra("URGENCY", task.urgency)
                    }
                    itemView.context.startActivity(intent)
                }
                hasVisibleButton = true
            } else {
                btnEdit.visibility = View.GONE
            }

            if (canEditDelete) {
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener {
                    onDelete(log)
                }
                hasVisibleButton = true
            } else {
                btnDelete.visibility = View.GONE
            }

            actionButtonsContainer.visibility = if (hasVisibleButton) View.VISIBLE else View.GONE
            dividerButtons.visibility = if (hasVisibleButton) View.VISIBLE else View.GONE
        }

        // ==================== توابع کمکی ====================
        private fun calculateDurationText(startTime: String, endTime: String): String {
            if (startTime.isEmpty() || endTime.isEmpty()) return ""

            return try {
                val startHour = startTime.split(":")[0].toInt()
                val startMinute = startTime.split(":")[1].toInt()
                val endHour = endTime.split(":")[0].toInt()
                val endMinute = endTime.split(":")[1].toInt()

                var durationMinutes = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
                if (durationMinutes < 0) durationMinutes += 24 * 60

                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60

                when {
                    hours > 0 && minutes > 0 -> "مدت زمان: $hours ساعت و $minutes دقیقه"
                    hours > 0 && minutes == 0 -> "مدت زمان: $hours ساعت"
                    hours == 0 && minutes > 0 -> "مدت زمان: $minutes دقیقه"
                    else -> ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        private fun calculateDurationInMinutes(startTime: String, endTime: String): Int {
            try {
                val startParts = startTime.split(":")
                val endParts = endTime.split(":")
                if (startParts.size == 2 && endParts.size == 2) {
                    val startHour = startParts[0].toInt()
                    val startMinute = startParts[1].toInt()
                    val endHour = endParts[0].toInt()
                    val endMinute = endParts[1].toInt()
                    var duration = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
                    if (duration < 0) duration += 24 * 60
                    return duration
                }
            } catch (e: Exception) { }
            return 0
        }

        // ==================== نمایش مشاهده‌کنندگان ====================
        @SuppressLint("SetTextI18n")
        private fun displaySeenBy(seenBy: List<String>) {
            val container = itemView.findViewById<LinearLayout>(R.id.seenByContainer)
            container.removeAllViews()

            if (seenBy.isEmpty()) {
                container.visibility = View.GONE
                dividerSeenBy.visibility = View.GONE
                return
            }

            container.visibility = View.VISIBLE
            dividerSeenBy.visibility = View.VISIBLE

            // برچسب
            val label = TextView(itemView.context).apply {
                text = "👁  "
                textSize = 12f
                setTextColor(Color.parseColor("#78909C"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginEnd = 2.dpToPx()
                }
            }
            container.addView(label)

            val maxDisplay = 6
            val toShow = seenBy.take(maxDisplay)
            val remaining = seenBy.size - maxDisplay

            toShow.forEach { userId ->
                val userName = UserCache.getName(userId)
                val firstLetter = if (userName.isNotEmpty() && userName != "نامشخص") {
                    userName.firstOrNull()?.toString()?.uppercase() ?: "?"
                } else {
                    "?"
                }
                val circleView = createSmallCircleWithLetter(firstLetter, userId)
                container.addView(circleView)
            }

            if (remaining > 0) {
                val moreView = TextView(itemView.context).apply {
                    text = "+$remaining"
                    textSize = 10f
                    setTextColor(Color.parseColor("#78909C"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginStart = 4.dpToPx()
                    }
                }
                container.addView(moreView)
            }
        }

        private fun createSmallCircleWithLetter(letter: String, userId: String): View {
            return TextView(itemView.context).apply {
                text = letter
                textSize = 10f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER

                val size = 22.dpToPx()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = 4.dpToPx()
                }

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(getColorForUserId(userId))
                }
            }
        }

        private fun getColorForUserId(userId: String): Int {
            val colors = listOf(
                "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
                "#2196F3", "#03A9F4", "#00BCD4", "#009688",
                "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
                "#FFC107", "#FF9800", "#FF5722", "#795548"
            )
            val index = userId.hashCode().mod(colors.size)
            return Color.parseColor(colors[index])
        }

        fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

        // ==================== نظرات ====================

        @SuppressLint("SetTextI18n")
        private fun displayComments(commentsJson: String, currentUserId: String) {
            val tvCommentLabel: TextView = itemView.findViewById(R.id.tvCommentLabel)
            val commentsContainer: LinearLayout = itemView.findViewById(R.id.commentsContainer)

            commentsContainer.removeAllViews()

            if (commentsJson.isEmpty() || commentsJson == "[]") {
                tvCommentLabel.visibility = View.GONE
                commentsContainer.visibility = View.GONE
                return
            }

            tvCommentLabel.visibility = View.VISIBLE
            commentsContainer.visibility = View.VISIBLE

            try {
                val jsonArray = JSONArray(commentsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val commentId = obj.getString("id")
                    val userId = obj.getString("userId")
                    val userName = obj.getString("userName")
                    val timestamp = obj.getString("timestamp")
                    val text = obj.getString("text")

                    val commentView = LayoutInflater.from(itemView.context)
                        .inflate(R.layout.item_comment, commentsContainer, false)

                    val tvHeader = commentView.findViewById<TextView>(R.id.tvCommentHeader)
                    val tvText = commentView.findViewById<TextView>(R.id.tvCommentText)
                    val tvExpand = commentView.findViewById<TextView>(R.id.tvCommentExpand)
                    val ivDelete = commentView.findViewById<ImageView>(R.id.ivDeleteComment)

                    tvHeader.text = "$userName - $timestamp"
                    tvText.text = text

                    if (text.length > 100) {
                        tvExpand.visibility = View.VISIBLE
                        tvExpand.setOnClickListener {
                            if (tvText.maxLines == 2) {
                                tvText.maxLines = Int.MAX_VALUE
                                tvExpand.text = "بستن"
                            } else {
                                tvText.maxLines = 2
                                tvExpand.text = "بیشتر"
                            }
                        }
                    }

                    if (userId == currentUserId) {
                        ivDelete.visibility = View.VISIBLE
                        ivDelete.setOnClickListener {
                            deleteComment(commentsJson, commentId)
                        }
                    }

                    commentsContainer.addView(commentView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun deleteComment(commentsJson: String, commentId: String) {
            try {
                val jsonArray = JSONArray(commentsJson)
                val newArray = JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.getString("id") != commentId) {
                        newArray.put(obj)
                    }
                }
                updateLogComments(currentLog.id, newArray.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun showAddCommentDialog() {
            val editText = EditText(itemView.context).apply {
                hint = "نظر خود را وارد کنید..."
                inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setLines(3)
            }

            AlertDialog.Builder(itemView.context)
                .setTitle("ثبت نظر")
                .setView(editText)
                .setPositiveButton("ثبت") { _, _ ->
                    val newCommentText = editText.text.toString().trim()
                    if (newCommentText.isNotEmpty()) {
                        addNewComment(newCommentText)
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        private fun addNewComment(text: String) {
            try {
                val sharedPref = itemView.context.getSharedPreferences(
                    Config.PrefKeys.USER_PREFS,
                    Context.MODE_PRIVATE
                )
                val currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
                val currentUserName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"
                val timestamp = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val commentId = System.currentTimeMillis().toString()

                val newComment = JSONObject().apply {
                    put("id", commentId)
                    put("userId", currentUserId)
                    put("userName", currentUserName)
                    put("timestamp", timestamp)
                    put("text", text)
                }

                val commentsStr = currentLog.comments
                val currentComments = if (commentsStr.isNotEmpty() && commentsStr != "[]") {
                    try {
                        JSONArray(commentsStr)
                    } catch (e: Exception) {
                        JSONArray()
                    }
                } else {
                    JSONArray()
                }
                currentComments.put(newComment)

                btnAddComment.isEnabled = false
                btnAddComment.text = "در حال ثبت..."

                updateLogComments(currentLog.id, currentComments.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(itemView.context, "خطا در ثبت نظر: ${e.message}", Toast.LENGTH_SHORT).show()
                btnAddComment.isEnabled = true
                btnAddComment.text = "ثبت نظر"
            }
        }

        private fun updateLogComments(logId: String, commentsJson: String) {
            val url = "${Config.BASE_URL}?action=updateLogComments"
            val jsonObject = JSONObject().apply {
                put("logId", logId)
                put("comments", commentsJson)
            }

            val request = JsonObjectRequest(
                Request.Method.POST, url, jsonObject,
                { response ->
                    if (response.optString("status") == "success") {
                        btnAddComment.isEnabled = true
                        btnAddComment.text = "ثبت نظر"
                        onRefreshCallback()
                    } else {
                        btnAddComment.isEnabled = true
                        btnAddComment.text = "ثبت نظر"
                        Toast.makeText(itemView.context, "خطا در ثبت نظر", Toast.LENGTH_SHORT).show()
                    }
                },
                { error ->
                    btnAddComment.isEnabled = true
                    btnAddComment.text = "ثبت نظر"
                    error.printStackTrace()
                    Toast.makeText(itemView.context, "خطا در اتصال به شبکه", Toast.LENGTH_SHORT).show()
                }
            )
            VolleySingleton.getInstance(itemView.context).add(request)
        }
    }

    class AddLogViewHolder(
        itemView: View,
        onAddLogClick: () -> Unit,
        onCompleteTaskClick: () -> Unit,
        showCompleteButton: Boolean,
        canAddLog: Boolean,
        private val buttonMode: String,
        private val onButtonClick: () -> Unit,
        private val onStatusChange: (String) -> Unit,
        private val currentStatus: String,
        private val isResponsible: Boolean,
        private val hasLogs: Boolean
    ) : RecyclerView.ViewHolder(itemView) {

        init {
            val btn = itemView.findViewById<Button>(R.id.btnAddLog)
            val statusContainer = itemView.findViewById<LinearLayout>(R.id.statusButtonsContainer)
            val btnContinue = itemView.findViewById<MaterialButton>(R.id.btnStatusContinue)
            val btnBlocked = itemView.findViewById<MaterialButton>(R.id.btnStatusBlocked)
            val btnComplete = itemView.findViewById<MaterialButton>(R.id.btnStatusComplete)

            // ====== دکمه اصلی ======
            when (buttonMode) {
                "ADD_LOG" -> {
                    if (currentStatus == "41") {
                        btn.visibility = View.GONE
                    } else {
                        btn.text = "ثبت گزارش جدید"
                        btn.setOnClickListener { onAddLogClick() }
                        btn.visibility = View.VISIBLE
                        btn.backgroundTintList = ColorStateList.valueOf(
                            Color.parseColor("#9C27B0")
                        )
                    }
                }
                "REQUEST_COMPLETE" -> {
                    btn.text = "اعلام اتمام کار"
                    btn.setOnClickListener { onCompleteTaskClick() }
                    btn.visibility = View.VISIBLE
                    btn.backgroundTintList = ColorStateList.valueOf(
                        Color.parseColor("#2E7D32")
                    )
                }
                "FINAL_REVIEW" -> {
                    btn.text = "بررسی و تأیید نهایی"
                    btn.setOnClickListener { onButtonClick() }
                    btn.visibility = View.VISIBLE
                    btn.backgroundTintList = ColorStateList.valueOf(
                        Color.parseColor("#FF9800")
                    )
                }
                else -> {
                    btn.visibility = View.GONE
                }
            }

            // ====== دکمه‌های وضعیت ======
            if (isResponsible && hasLogs) {
                statusContainer.visibility = View.VISIBLE

                // رنگ‌های پررنگ (فعال)
                val colorContinue = Color.parseColor("#1565C0")   // آبی
                val colorBlocked = Color.parseColor("#C62828")    // قرمز
                val colorComplete = Color.parseColor("#2E7D32")   // سبز

                // رنگ‌های کم‌رنگ (غیرفعال)
                val colorContinueLight = Color.parseColor("#90CAF9")
                val colorBlockedLight = Color.parseColor("#EF9A9A")
                val colorCompleteLight = Color.parseColor("#A5D6A7")

                // ====== تنظیم رنگ‌ها بر اساس وضعیت فعلی ======
                // ادامه
                if (currentStatus == "2") {
                    btnContinue.backgroundTintList = ColorStateList.valueOf(colorContinue)
                    btnContinue.elevation = 8f       // سایه بیشتر
                    btnContinue.alpha = 1.0f
                } else {
                    btnContinue.backgroundTintList = ColorStateList.valueOf(colorContinueLight)
                    btnContinue.elevation = 0f
                    btnContinue.alpha = 0.6f
                }

                // متوقف
                if (currentStatus == "3") {
                    btnBlocked.backgroundTintList = ColorStateList.valueOf(colorBlocked)
                    btnBlocked.elevation = 8f
                    btnBlocked.alpha = 1.0f
                } else {
                    btnBlocked.backgroundTintList = ColorStateList.valueOf(colorBlockedLight)
                    btnBlocked.elevation = 0f
                    btnBlocked.alpha = 0.6f
                }

                // اتمام
                if (currentStatus == "41") {
                    btnComplete.backgroundTintList = ColorStateList.valueOf(colorComplete)
                    btnComplete.elevation = 8f
                    btnComplete.alpha = 1.0f
                } else {
                    btnComplete.backgroundTintList = ColorStateList.valueOf(colorCompleteLight)
                    btnComplete.elevation = 0f
                    btnComplete.alpha = 0.6f
                }

                // ====== Listenerها ======
                btnContinue.setOnClickListener { onStatusChange("2") }
                btnBlocked.setOnClickListener { onStatusChange("3") }
                btnComplete.setOnClickListener { onStatusChange("41") }

            } else {
                statusContainer.visibility = View.GONE
            }
        }
    }
}