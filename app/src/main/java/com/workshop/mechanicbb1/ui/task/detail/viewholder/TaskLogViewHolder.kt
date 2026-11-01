package com.workshop.mechanicbb1.ui.task.detail.viewholder

import TaskModel
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.util.TaskDetailUiUtils
import com.workshop.mechanicbb1.ui.task.detail.util.TaskDetailUiUtils.dpToPx
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

    private val evaluationContainer: LinearLayout = itemView.findViewById(R.id.evaluationContainer)
    private val evaluationHeader: LinearLayout = itemView.findViewById(R.id.evaluationHeader)
    private val evaluationDetail: LinearLayout = itemView.findViewById(R.id.evaluationDetail)
    private val ivEvaluationExpand: ImageView = itemView.findViewById(R.id.ivEvaluationExpand)
    private val dividerEvaluation: View = itemView.findViewById(R.id.dividerEvaluation)
    private val tvHeatLevel: TextView = itemView.findViewById(R.id.tvHeatLevel)
    private val tvPollutionLevel: TextView = itemView.findViewById(R.id.tvPollutionLevel)
    private val tvPhysicalDifficulty: TextView = itemView.findViewById(R.id.tvPhysicalDifficulty)
    private val tvTechnicalComplexity: TextView = itemView.findViewById(R.id.tvTechnicalComplexity)

    private val actionButtonsContainer: LinearLayout = itemView.findViewById(R.id.actionButtonsContainer)
    private val btnEvaluate: Button = itemView.findViewById(R.id.btnEvaluate)
    private val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
    private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

    private val dividerDescription: View = itemView.findViewById(R.id.dividerDescription)
    private val dividerWorkers: View = itemView.findViewById(R.id.dividerWorkers)
    private val dividerSeenBy: View = itemView.findViewById(R.id.dividerSeenBy)
    private val dividerButtons: View = itemView.findViewById(R.id.dividerButtons)

    private var isEvaluationExpanded = false
    private lateinit var currentLog: TaskLogModel
    private lateinit var currentTask: TaskModel
    private lateinit var onRefreshCallback: () -> Unit
    private lateinit var currentUserId: String

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
        this.onRefreshCallback = onRefresh

        val isSeenByCurrentUser = currentLog.seenBy.contains(currentUserId)
        if (!isSeenByCurrentUser && (userRole == Config.RoleCode.SUPERVISOR || userRole == Config.RoleCode.MANAGER)) {
            cardLog.setCardBackgroundColor(Color.parseColor("#F0F5FF"))
        }

        val timeRange = if (log.startTime.isNotEmpty() || log.endTime.isNotEmpty()) {
            " (${log.startTime} - ${log.endTime})"
        } else ""

        val dayOfWeek = DateUtils.getDayNameFromDate(log.date)
        val dateDisplay = if (dayOfWeek.isNotEmpty()) "${log.date} ($dayOfWeek)" else log.date
        tvLogSummary.text = "📋 گزارش $dateDisplay - ${log.userName}$timeRange"

        tvActionDescription.text = log.actionDescription
        if (log.actionDescription.contains("برگشت داده شد")) {
            tvActionDescription.setBackgroundColor(Color.parseColor("#FFEBEE"))
            tvActionDescription.setTextColor(Color.parseColor("#D32F2F"))
        }

        val durationText = TaskDetailUiUtils.calculateDurationText(log.startTime, log.endTime)
        if (durationText.isNotEmpty()) {
            tvDuration.text = "⏱ $durationText"
            tvDuration.visibility = View.VISIBLE
        } else {
            tvDuration.visibility = View.GONE
        }

        // گروه انجام‌دهنده
        val assignedToFullList = task.assignedTo.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val responsibleId = task.responsible
        val logWorkers = log.assignedUsers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val allMembers = assignedToFullList.toMutableSet()
        if (responsibleId.isNotEmpty() && responsibleId != "0") allMembers.add(responsibleId)
        val allParticipated = allMembers.isNotEmpty() && allMembers.all { it in logWorkers }

        if (!allParticipated && logWorkers.isNotEmpty()) {
            val workerNames = logWorkers.mapNotNull {
                val name = UserCache.getName(it)
                if (name != "نامشخص" && !name.startsWith("کاربر")) name else null
            }
            if (workerNames.isNotEmpty()) {
                tvWorkers.text = "👥 گروه انجام‌دهنده: ${workerNames.joinToString("، ")}"
                tvWorkers.visibility = View.VISIBLE
                dividerWorkers.visibility = View.VISIBLE
            } else {
                tvWorkers.visibility = View.GONE
                dividerWorkers.visibility = View.GONE
            }
        } else {
            tvWorkers.visibility = View.GONE
            dividerWorkers.visibility = View.GONE
        }

        // وضعیت جدید
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

        displaySeenBy(log.seenBy)
        setupEvaluationSection(log)
        displayComments(log.comments, currentUserId)

        val isArchived = task.status == "5"
        if (!isArchived) {
            btnAddComment.visibility = View.VISIBLE
            btnAddComment.setOnClickListener { showAddCommentDialog() }
        } else {
            btnAddComment.visibility = View.GONE
        }

        setupActionButtons(log, task, currentUserId, userRole, onDelete)

        itemView.setOnClickListener {
            val intent = Intent(itemView.context, TaskLogDetailActivity::class.java).apply {
                putExtra("TASK_ID", task.id)
                putExtra("LOG_ID", log.id)
                putExtra("IS_TASK_ARCHIVED", task.status == "5")
            }
            itemView.context.startActivity(intent)
        }

        if (::currentUserId.isInitialized && currentUserId.isNotEmpty()) {
            SeenManager.markAsSeen(
                itemView.context, currentUserId,
                listOf(SeenItem("TASK_LOG", currentLog.id))
            )
        }
    }

    private fun setupEvaluationSection(log: TaskLogModel) {
        val hasData = log.heatLevel > 30 || log.pollutionLevel > 0 ||
                log.physicalDifficulty > 0 || log.technicalComplexity > 0

        if (!hasData) {
            evaluationContainer.visibility = View.GONE
            return
        }

        evaluationContainer.visibility = View.VISIBLE
        dividerEvaluation.visibility = View.VISIBLE

        if (log.heatLevel > 30) {
            tvHeatLevel.text = "🌡️ دمای هوا: ${log.heatLevel}°C"
            tvHeatLevel.visibility = View.VISIBLE
        } else tvHeatLevel.visibility = View.GONE

        if (log.pollutionLevel > 0) {
            tvPollutionLevel.text = "🏭 میزان آلودگی: ${log.pollutionLevel} ppm"
            tvPollutionLevel.visibility = View.VISIBLE
        } else tvPollutionLevel.visibility = View.GONE

        if (log.physicalDifficulty > 0) {
            tvPhysicalDifficulty.text = "💪 سختی فیزیکی: ${Config.Evaluation.getPhysicalText(log.physicalDifficulty)}"
            tvPhysicalDifficulty.visibility = View.VISIBLE
        } else tvPhysicalDifficulty.visibility = View.GONE

        if (log.technicalComplexity > 0) {
            tvTechnicalComplexity.text = "🔧 پیچیدگی فنی: ${Config.Evaluation.getTechnicalText(log.technicalComplexity)}"
            tvTechnicalComplexity.visibility = View.VISIBLE
        } else tvTechnicalComplexity.visibility = View.GONE

        isEvaluationExpanded = false
        evaluationDetail.visibility = View.GONE
        ivEvaluationExpand.setImageResource(R.drawable.ic_chevron_down)

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
                    putExtra("DURATION_MINUTES", TaskDetailUiUtils.calculateDurationInMinutes(log.startTime, log.endTime))
                    putExtra("IS_FROM_DETAIL", true)
                }
                itemView.context.startActivity(intent)
            }
            hasVisibleButton = true
        } else btnEvaluate.visibility = View.GONE

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
                    putExtra("PRIORITY", task.priority)
                    putExtra("URGENCY", task.urgency)
                }
                itemView.context.startActivity(intent)
            }
            hasVisibleButton = true
        } else btnEdit.visibility = View.GONE

        if (canEditDelete) {
            btnDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener { onDelete(log) }
            hasVisibleButton = true
        } else btnDelete.visibility = View.GONE

        actionButtonsContainer.visibility = if (hasVisibleButton) View.VISIBLE else View.GONE
        dividerButtons.visibility = if (hasVisibleButton) View.VISIBLE else View.GONE
    }

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

        val label = TextView(itemView.context).apply {
            text = "👁  "
            textSize = 12f
            setTextColor(Color.parseColor("#78909C"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
            val firstLetter = if (userName.isNotEmpty() && userName != "نامشخص")
                userName.firstOrNull()?.toString()?.uppercase() ?: "?" else "?"
            val circleView = TaskDetailUiUtils.createSeenByCircle(
                itemView.context, firstLetter, userId, userName, 22
            )
            container.addView(circleView)
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
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = 4.dpToPx()
                }
            }
            container.addView(moreView)
        }
    }

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
                    .inflate(R.layout.taskdetail_comment_item, commentsContainer, false)

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
                    ivDelete.setOnClickListener { deleteComment(commentsJson, commentId) }
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
                if (obj.getString("id") != commentId) newArray.put(obj)
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
                if (newCommentText.isNotEmpty()) addNewComment(newCommentText)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun addNewComment(text: String) {
        try {
            val sharedPref = itemView.context.getSharedPreferences(
                Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE
            )
            val uid = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
            val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"
            val timestamp = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            val commentId = System.currentTimeMillis().toString()

            val newComment = JSONObject().apply {
                put("id", commentId)
                put("userId", uid)
                put("userName", userName)
                put("timestamp", timestamp)
                put("text", text)
            }

            val currentComments = if (currentLog.comments.isNotEmpty() && currentLog.comments != "[]") {
                try { JSONArray(currentLog.comments) } catch (e: Exception) { JSONArray() }
            } else JSONArray()
            currentComments.put(newComment)

            btnAddComment.isEnabled = false
            btnAddComment.text = "در حال ثبت..."
            updateLogComments(currentLog.id, currentComments.toString())
        } catch (e: Exception) {
            e.printStackTrace()
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
                btnAddComment.isEnabled = true
                btnAddComment.text = "ثبت نظر"
                if (response.optString("status") == "success") onRefreshCallback()
            },
            { error ->
                btnAddComment.isEnabled = true
                btnAddComment.text = "ثبت نظر"
                error.printStackTrace()
            }
        )
        VolleySingleton.getInstance(itemView.context).add(request)
    }
}