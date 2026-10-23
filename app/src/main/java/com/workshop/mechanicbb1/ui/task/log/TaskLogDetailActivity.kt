package com.workshop.mechanicbb1.ui.task.log

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.repository.TaskLogRepository
import com.workshop.mechanicbb1.utils.UserCache
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.workshop.mechanicbb1.utils.SeenItem
import com.workshop.mechanicbb1.utils.SeenManager
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView

@Suppress("DEPRECATION")
class TaskLogDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var progressBar: ProgressBar

    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvReporter: TextView
    private lateinit var tvActionDescription: TextView
    private lateinit var tvWorkers: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvNewStatus: TextView

    private lateinit var layoutConditional: LinearLayout
    private lateinit var tvConditionalLabel: TextView
    private lateinit var tvConditionalText: TextView

    private lateinit var layoutWorkCondition: LinearLayout
    private lateinit var tvHeatAndPollution: TextView
    private lateinit var tvWorkType: TextView

    private lateinit var layoutNotes: LinearLayout
    private lateinit var tvNotes: TextView

    private lateinit var commentsContainer: LinearLayout
    private lateinit var btnAddComment: Button

    private var optionsMenu: Menu? = null

    private lateinit var seenByContainer: LinearLayout

    private lateinit var taskLogRepository: TaskLogRepository

    // مقداردهی اولیه با یک مقدار پیش‌فرض
    private var currentLog: TaskLogModel? = null
    private var taskId: String = ""
    private var logId: String = ""
    private var currentUserId: String = ""
    private var userRole: String = ""
    private var isTaskArchived: Boolean = false

    companion object {
        private const val STATUS_CONTINUE = "2"
        private const val STATUS_STOPPED = "3"
        private const val STATUS_COMPLETED = "4"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_log_detail)

        initViews()
        setupToolbar()
        initData()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        progressBar = findViewById(R.id.progressBar)

        tvDate = findViewById(R.id.tvDate)
        tvTime = findViewById(R.id.tvTime)
        tvReporter = findViewById(R.id.tvReporter)
        tvActionDescription = findViewById(R.id.tvActionDescription)
        tvWorkers = findViewById(R.id.tvWorkers)
        tvDuration = findViewById(R.id.tvDuration)
        tvNewStatus = findViewById(R.id.tvNewStatus)

        layoutConditional = findViewById(R.id.layoutConditional)
        tvConditionalLabel = findViewById(R.id.tvConditionalLabel)
        tvConditionalText = findViewById(R.id.tvConditionalText)

        layoutWorkCondition = findViewById(R.id.layoutWorkCondition)
        tvHeatAndPollution = findViewById(R.id.tvHeatAndPollution)
        tvWorkType = findViewById(R.id.tvWorkType)

        layoutNotes = findViewById(R.id.layoutNotes)
        tvNotes = findViewById(R.id.tvNotes)

        commentsContainer = findViewById(R.id.commentsContainer)
        btnAddComment = findViewById(R.id.btnAddComment)

        seenByContainer = findViewById(R.id.seenByContainer)

        btnAddComment.setOnClickListener { showAddCommentDialog() }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "جزئیات گزارش"
        }
        toolbar.navigationIcon?.setTint(Color.WHITE)
    }

    private fun initData() {
        taskId = intent.getStringExtra("TASK_ID") ?: ""
        logId = intent.getStringExtra("LOG_ID") ?: ""
        isTaskArchived = intent.getBooleanExtra("IS_TASK_ARCHIVED", false)

        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        taskLogRepository = TaskLogRepository(this)

        if (taskId.isEmpty() || logId.isEmpty()) {
            Toast.makeText(this, "خطا: شناسه کار یا گزارش یافت نشد", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadLogData()
    }

    private fun loadLogData() {
        showLoading(true)

        taskLogRepository.getTaskLogs(
            taskId = taskId,
            onSuccess = { logs ->
                runOnUiThread {
                    val log = logs.find { it.id == logId }
                    if (log != null) {
                        currentLog = log
                        displayLogData()
                        showLoading(false)

                        if (currentUserId.isNotEmpty()) {
                            SeenManager.markAsSeen(
                                this,
                                currentUserId,
                                listOf(SeenItem("TASK_LOG", currentLog!!.id))
                            )
                        }
                    } else {
                        showLoading(false)
                        Toast.makeText(this, "گزارش یافت نشد", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            },
            onError = { message ->
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, "خطا در بارگذاری: $message", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }

    private fun displayLogData() {
        val log = currentLog ?: return

        // هدر
        tvDate.text = "تاریخ: ${log.date}"
        val timeRange = if (log.startTime.isNotEmpty() || log.endTime.isNotEmpty()) {
            "ساعت: ${log.startTime} - ${log.endTime}"
        } else {
            "ساعت: ثبت نشده"
        }
        tvTime.text = timeRange

        val reporterName = UserCache.getName(log.userId).takeIf { it != "نامشخص" } ?: log.userName
        tvReporter.text = "این گزارش توسط $reporterName ثبت شده"

        tvActionDescription.text = log.actionDescription

        // گروه انجام‌دهنده
        if (log.assignedUsers.isNotEmpty()) {
            val workerNames = log.assignedUsers.split(",").mapNotNull {
                val name = UserCache.getName(it.trim())
                if (name != "نامشخص" && !name.startsWith("کاربر")) name else null
            }
            tvWorkers.text = if (workerNames.isNotEmpty()) workerNames.joinToString("، ") else "تعیین نشده"
        } else {
            tvWorkers.text = "تعیین نشده"
        }

        // مدت زمان
        if (log.startTime.isNotEmpty() && log.endTime.isNotEmpty()) {
            tvDuration.text = calculateDuration(log.startTime, log.endTime)
        } else {
            tvDuration.text = "ثبت نشده"
        }

        // وضعیت جدید
        val statusText = when (log.newStatus) {
            STATUS_CONTINUE -> "ادامه دارد"
            STATUS_STOPPED -> "متوقف"
            STATUS_COMPLETED -> "اتمام کار"
            else -> "نامشخص"
        }
        tvNewStatus.text = statusText

        // شرح اقدام بعدی / علت توقف
        when (log.newStatus) {
            STATUS_CONTINUE -> {
                tvConditionalLabel.text = "📝 شرح اقدام بعدی"
                layoutConditional.visibility = View.VISIBLE
                tvConditionalText.text = log.notes.takeIf { it.isNotEmpty() } ?: "ثبت نشده"
            }
            STATUS_STOPPED -> {
                tvConditionalLabel.text = "⚠️ علت توقف"
                layoutConditional.visibility = View.VISIBLE
                tvConditionalText.text = log.notes.takeIf { it.isNotEmpty() } ?: "ثبت نشده"
            }
            else -> {
                layoutConditional.visibility = View.GONE
            }
        }

        // شرایط کار
        val hasWorkCondition = log.heatLevel > 0 || log.pollutionLevel > 0 || log.workType.isNotEmpty()
        if (hasWorkCondition) {
            layoutWorkCondition.visibility = View.VISIBLE
            val heatPollutionText = buildString {
                if (log.heatLevel > 0) append("دما: ${log.heatLevel}°C")
                if (log.pollutionLevel > 0) {
                    if (isNotEmpty()) append("  |  ")
                    append("آلودگی: ${log.pollutionLevel} ppm")
                }
                if (isEmpty()) append("ثبت نشده")
            }
            tvHeatAndPollution.text = heatPollutionText

            val workTypeText = when (log.workType) {
                "1" -> "نوع کار: تعمیر تجهیزات ثابت"
                "2" -> "نوع کار: عیب‌یابی تجهیزات دوار"
                "3" -> "نوع کار: بررسی و بازرسی"
                else -> if (log.workType.isNotEmpty()) "نوع کار: ${log.workType}" else ""
            }
            tvWorkType.text = workTypeText
            tvWorkType.visibility = if (workTypeText.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            layoutWorkCondition.visibility = View.GONE
        }

        // توضیحات تکمیلی
        val hasNotes = log.notes.isNotEmpty() && log.newStatus !in listOf(STATUS_CONTINUE, STATUS_STOPPED)
        if (hasNotes) {
            layoutNotes.visibility = View.VISIBLE
            tvNotes.text = log.notes
        } else {
            layoutNotes.visibility = View.GONE
        }

        displayComments(log.comments)
        displaySeenBy(log.seenBy)

        updateMenuVisibility()
        supportInvalidateOptionsMenu()
    }

    private fun calculateDuration(startTime: String, endTime: String): String {
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
                else -> "مدت زمان: کمتر از یک دقیقه"
            }
        } catch (e: Exception) {
            "مدت زمان: نامشخص"
        }
    }

    private fun displayComments(commentsJson: String) {
        commentsContainer.removeAllViews()

        if (commentsJson.isEmpty() || commentsJson == "[]") {
            val emptyView = TextView(this).apply {
                text = "هنوز نظری ثبت نشده است"
                textSize = 13f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            commentsContainer.addView(emptyView)
            return
        }

        try {
            val jsonArray = JSONArray(commentsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val commentId = obj.getString("id")
                val userId = obj.getString("userId")
                val userName = obj.getString("userName")
                val timestamp = obj.getString("timestamp")
                val text = obj.getString("text")

                val commentView = LayoutInflater.from(this)
                    .inflate(R.layout.item_comment_full, commentsContainer, false)

                val tvUserName = commentView.findViewById<TextView>(R.id.tvCommentUserName)
                val tvTimestamp = commentView.findViewById<TextView>(R.id.tvCommentTimestamp)
                val tvCommentText = commentView.findViewById<TextView>(R.id.tvCommentText)
                val ivDelete = commentView.findViewById<ImageView>(R.id.ivDeleteComment)

                tvUserName.text = userName
                tvTimestamp.text = timestamp
                tvCommentText.text = text

                val canDelete = !isTaskArchived && userId == currentUserId
                if (canDelete) {
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

    private fun displaySeenBy(seenBy: List<String>) {
        seenByContainer.removeAllViews()

        if (seenBy.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "هنوز کسی این گزارش را ندیده است"
                textSize = 12f
                setTextColor(Color.GRAY)
            }
            seenByContainer.addView(emptyView)
            return
        }

        val maxDisplay = 8
        val toShow = seenBy.take(maxDisplay)
        val remaining = seenBy.size - maxDisplay

        toShow.forEach { userId ->
            val userName = UserCache.getName(userId)
            val firstLetter = if (userName.isNotEmpty() && userName != "نامشخص")
                userName.firstOrNull()?.toString()?.uppercase() ?: "?"
            else "?"

            val circleView = createSeenByCircle(firstLetter, userId, userName)
            seenByContainer.addView(circleView)
        }

        if (remaining > 0) {
            val moreView = TextView(this).apply {
                text = "+$remaining"
                textSize = 11f
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

    private fun createSeenByCircle(letter: String, userId: String, fullName: String): View {
        return TextView(this).apply {
            text = letter
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            val size = 28.dpToPx()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = 4.dpToPx()
            }

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getColorForUserId(userId))
            }

            setOnLongClickListener {
                Toast.makeText(context, fullName, Toast.LENGTH_SHORT).show()
                true
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

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun showAddCommentDialog() {
        if (isTaskArchived) {
            Toast.makeText(this, "کار بایگانی شده و امکان ثبت نظر وجود ندارد", Toast.LENGTH_SHORT).show()
            return
        }

        val editText = EditText(this)
        editText.hint = "نظر خود را وارد کنید..."
        editText.inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        editText.setLines(3)

        AlertDialog.Builder(this)
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
        val log = currentLog ?: return

        try {
            val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
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

            val commentsStr = log.comments ?: "[]"
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

            updateLogComments(log.id, currentComments.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "خطا در ثبت نظر: ${e.message}", Toast.LENGTH_SHORT).show()
            btnAddComment.isEnabled = true
            btnAddComment.text = "➕ افزودن نظر"
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
                btnAddComment.text = "➕ افزودن نظر"
                if (response.optString("status") == "success") {
                    currentLog = currentLog?.copy(comments = commentsJson)
                    displayComments(commentsJson)
                } else {
                    Toast.makeText(this, "خطا در ثبت نظر", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                btnAddComment.isEnabled = true
                btnAddComment.text = "➕ افزودن نظر"
                error.printStackTrace()
                Toast.makeText(this, "خطا در اتصال به شبکه", Toast.LENGTH_SHORT).show()
            }
        )
        VolleySingleton.getInstance(this).add(request)
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
            updateLogComments(currentLog?.id ?: return, newArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        optionsMenu = menu
        updateMenuVisibility()
        return true
    }

    private fun updateMenuVisibility() {
        val log = currentLog
        val menu = optionsMenu ?: return

        menu.clear()

        if (!isTaskArchived && log != null && log.userId == currentUserId) {
            menuInflater.inflate(R.menu.menu_task_log_detail, menu)

            // تنظیم رنگ برنامه‌ای برای آیکون‌ها
            val editItem = menu.findItem(R.id.action_edit_log)
            val deleteItem = menu.findItem(R.id.action_delete_log)

            editItem?.icon?.setTint(Color.parseColor("#FF9800"))  // نارنجی
            deleteItem?.icon?.setTint(Color.parseColor("#F44336")) // قرمز
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_edit_log -> {
                editLog()
                true
            }
            R.id.action_delete_log -> {
                confirmDeleteLog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun editLog() {
        val log = currentLog ?: return
        val intent = Intent(this, AddLogActivity::class.java).apply {
            putExtra("IS_EDIT_MODE", true)
            putExtra("LOG_ID", log.id)
            putExtra("TASK_ID", log.taskId)
        }
        startActivity(intent)
        finish()
    }

    private fun confirmDeleteLog() {
        AlertDialog.Builder(this)
            .setTitle("حذف گزارش")
            .setMessage("آیا از حذف این گزارش مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ ->
                deleteLog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun deleteLog() {
        val log = currentLog ?: return
        showLoading(true)
        taskLogRepository.deleteTaskLog(
            logId = log.id,
            onSuccess = {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, "گزارش با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            onError = { message ->
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, "خطا در حذف: $message", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}