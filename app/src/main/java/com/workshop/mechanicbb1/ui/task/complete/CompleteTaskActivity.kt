package com.workshop.mechanicbb1.ui.task.complete

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.cartable.CartableActivity
import com.workshop.mechanicbb1.ui.task.repository.TaskLogRepository
import com.workshop.mechanicbb1.utils.UserCache
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class CompleteTaskActivity : AppCompatActivity() {

    private lateinit var taskLogRepository: TaskLogRepository
    private lateinit var tvTotalDuration: TextView
    private lateinit var tvAvgHeat: TextView
    private lateinit var tvAvgPollution: TextView
    private lateinit var tableContent: LinearLayout
    private lateinit var btnConfirmComplete: Button
    private lateinit var btnBack: Button
    private lateinit var btnReject: Button

    private var taskId: String = ""
    private var taskTitle: String = ""
    private var currentStatus: String = ""
    private var userRole: String = ""

    companion object {
        private const val STATUS_IN_PROGRESS = "2"
        private const val STATUS_REQUEST_COMPLETE = "4"
        private const val STATUS_SENT_TO_SUPERVISOR = "41"
        private const val STATUS_ARCHIVED = "5"
        private const val STATUS_REJECTED = "22"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_task)

        // مقداردهی اولیه UserCache (اگر قبلاً نشده)
        if (UserCache.getSize() == 0) {
            UserCache.init(applicationContext)
        }

        setupToolbar()
        initViews()
        loadIntentData()
        loadUserRole()
        taskLogRepository = TaskLogRepository(this)
        loadTaskLogs()
        loadCurrentStatus()
        setupBackButton()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "اتمام کار"
        }
    }

    private fun initViews() {
        tvTotalDuration = findViewById(R.id.tvTotalDuration)
        tvAvgHeat = findViewById(R.id.tvAvgHeat)
        tvAvgPollution = findViewById(R.id.tvAvgPollution)
        tableContent = findViewById(R.id.tableContent)
        btnConfirmComplete = findViewById(R.id.btnConfirmComplete)
        btnBack = findViewById(R.id.btnBack)
        btnReject = findViewById(R.id.btnReject)
    }

    private fun loadIntentData() {
        taskId = intent.getStringExtra("TASK_ID") ?: ""
        taskTitle = intent.getStringExtra("TASK_TITLE") ?: ""
        currentStatus = intent.getStringExtra("CURRENT_STATUS") ?: STATUS_IN_PROGRESS
    }

    private fun loadUserRole() {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""
    }

    private fun setupBackButton() {
        btnBack.setOnClickListener { finish() }
    }

    private fun setupButtonsByRoleAndStatus() {
        when {
            isEmployeeRequestingCompletion() -> {
                setupEmployeeCompletion()
            }
            isSupervisorApproving() -> {
                setupSupervisorApproval()
            }
            else -> {
                setupDefaultCompletion()
            }
        }
    }

    private fun isEmployeeRequestingCompletion(): Boolean =
        userRole == Config.RoleCode.EMPLOYEE && (currentStatus == STATUS_IN_PROGRESS || currentStatus == STATUS_REQUEST_COMPLETE)

    private fun isSupervisorApproving(): Boolean =
        userRole == Config.RoleCode.SUPERVISOR && currentStatus == STATUS_SENT_TO_SUPERVISOR

    private fun setupEmployeeCompletion() {
        showConfirmButton("اعلام اتمام کار") { completeTaskAsResponsible() }
        hideRejectButton()
    }

    private fun setupSupervisorApproval() {
        showConfirmButton("تأیید و ارسال به بایگانی") { completeTaskAsSupervisor() }
        showRejectButton("برگشت به مسئول کار") { showRejectDialog() }
    }

    private fun setupDefaultCompletion() {
        showConfirmButton("اعلام اتمام کار") { completeTaskAsResponsible() }
        hideRejectButton()
    }

    private fun showConfirmButton(text: String, onClick: () -> Unit) {
        btnConfirmComplete.visibility = View.VISIBLE
        btnConfirmComplete.text = text
        btnConfirmComplete.setOnClickListener { onClick() }
    }

    private fun hideRejectButton() {
        btnReject.visibility = View.GONE
    }

    private fun showRejectButton(text: String, onClick: () -> Unit) {
        btnReject.visibility = View.VISIBLE
        btnReject.text = text
        btnReject.setOnClickListener { onClick() }
    }

    private fun loadTaskLogs() {
        taskLogRepository.getTaskLogs(
            taskId = taskId,
            onSuccess = { logs ->
                if (logs.isNotEmpty()) {
                    // اول همه کاربران رو از سرور بارگذاری کن، بعد نمایش بده
                    UserCache.loadAllUsers {
                        runOnUiThread {
                            calculateAndDisplay(logs)
                        }
                    }
                } else {
                    Toast.makeText(this, "گزارشی برای این کار وجود ندارد", Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            onError = { message ->
                Toast.makeText(this, "خطا در دریافت گزارش‌ها: $message", Toast.LENGTH_SHORT).show()
                finish()
            }
        )
    }

    private fun loadCurrentStatus() {
        val url = "${Config.BASE_URL}?action=getTask&taskId=$taskId"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                currentStatus = response.optString("status", STATUS_IN_PROGRESS)
                setupButtonsByRoleAndStatus()
            },
            { error ->
                Log.e("CompleteTask", "Error loading status: ${error.message}")
                currentStatus = STATUS_IN_PROGRESS
                setupButtonsByRoleAndStatus()
            }
        )
        VolleySingleton.getInstance(this).add(request)
    }

    private fun calculateAndDisplay(logs: List<TaskLogModel>) {
        var totalPersonHours = 0.0
        var totalWeightedHeat = 0.0
        var totalWeightedPollution = 0.0
        val uniqueWorkTypes = mutableSetOf<String>()
        val userStats = mutableMapOf<String, UserStats>()

        for (log in logs) {
            val duration = calculateDuration(log.startTime, log.endTime)

            if (log.workType.isNotEmpty()) {
                uniqueWorkTypes.add(log.workType)
            }

            val userIds = log.assignedUsers.split(",").filter { it.isNotEmpty() }
            for (userId in userIds) {
                val stats = userStats.getOrPut(userId) { UserStats() }
                stats.totalDuration += duration
                stats.weightedHeat += log.heatLevel * duration
                stats.weightedPollution += log.pollutionLevel * duration
                totalPersonHours += duration
            }

            totalWeightedHeat += log.heatLevel * duration * userIds.size
            totalWeightedPollution += log.pollutionLevel * duration * userIds.size
        }

        displayTotalDuration(totalPersonHours)
        displayAverages(totalPersonHours, totalWeightedHeat, totalWeightedPollution)
        displayWorkTypes(uniqueWorkTypes)
        displayUserStatsTable(userStats)
    }

    @SuppressLint("SetTextI18n")
    private fun displayTotalDuration(totalPersonHours: Double) {
        tvTotalDuration.text = "کل زمان صرف شده: ${formatNumber(totalPersonHours)} نفر-ساعت"
    }

    @SuppressLint("SetTextI18n")
    private fun displayAverages(totalHours: Double, weightedHeat: Double, weightedPollution: Double) {
        if (totalHours > 0) {
            tvAvgHeat.text = "میانگین وزنی گرما: ${formatNumber(weightedHeat / totalHours)} درجه"
            tvAvgPollution.text = "میانگین وزنی آلودگی: ${formatNumber(weightedPollution / totalHours)} ppm"
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatNumber(value: Double): String = String.format("%.1f", value)

    private fun calculateDuration(startTime: String, endTime: String): Double {
        if (startTime.isEmpty() || endTime.isEmpty()) return 0.0
        return try {
            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
            var durationMinutes = endMinutes - startMinutes
            if (durationMinutes < 0) durationMinutes += 24 * 60
            durationMinutes / 60.0
        } catch (e: Exception) {
            0.0
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayWorkTypes(workTypes: Set<String>) {
        if (workTypes.isEmpty()) return

        val workTypeNames = workTypes.joinToString("، ") { code ->
            when (code) {
                "1" -> "تجهیزات ثابت"
                "2" -> "عیب‌یابی تجهیزات دوار"
                "3" -> "بررسی"
                else -> "نامشخص"
            }
        }

        val workTypesContainer = findViewById<LinearLayout>(R.id.workTypesContainer)
        workTypesContainer?.apply {
            removeAllViews()
            val tvWorkTypes = TextView(this@CompleteTaskActivity).apply {
                text = "نوع کار انجام شده: $workTypeNames"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                setPadding(0, 8, 0, 0)
            }
            addView(tvWorkTypes)
            visibility = View.VISIBLE
        }
    }

    private fun displayUserStatsTable(userStats: Map<String, UserStats>) {
        tableContent.removeAllViews()

        // اگر کاربری در کش نیست، اول همه رو بارگذاری کن
        val missingUsers = userStats.keys.filter { !UserCache.hasUser(it) && it.isNotEmpty() && it != "0" }
        if (missingUsers.isNotEmpty()) {
            UserCache.loadAllUsers {
                runOnUiThread {
                    displayUserStatsTableInternal(userStats)
                }
            }
        } else {
            displayUserStatsTableInternal(userStats)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayUserStatsTableInternal(userStats: Map<String, UserStats>) {
        tableContent.removeAllViews()

        for ((userId, stats) in userStats) {
            // گرفتن نام کاربر از UserCache
            val userName = if (userId.isNotEmpty() && userId != "0") {
                UserCache.getName(userId)
            } else {
                "نامشخص"
            }

            val duration = stats.totalDuration
            val avgHeat = if (duration > 0) stats.weightedHeat / duration else 0.0
            val avgPollution = if (duration > 0) stats.weightedPollution / duration else 0.0

            val row = LayoutInflater.from(this).inflate(R.layout.item_user_stats_row, tableContent, false)

            row.findViewById<TextView>(R.id.tvUserName).text = userName
            row.findViewById<TextView>(R.id.tvUserDuration).text = formatNumber(duration)
            row.findViewById<TextView>(R.id.tvUserHeat).text = "${formatNumber(avgHeat)} / ${formatNumber(avgPollution)}"
            row.findViewById<TextView>(R.id.tvUserPollution).visibility = View.GONE

            tableContent.addView(row)
        }
    }

    private fun completeTaskAsResponsible() {
        updateTaskStatus(taskId, STATUS_SENT_TO_SUPERVISOR) { success ->
            if (success) {
                Toast.makeText(this, "درخواست تأیید به سرشیفت ارسال شد", Toast.LENGTH_SHORT).show()
                navigateToCartable()
            } else {
                Toast.makeText(this, "خطا در ارسال درخواست", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun completeTaskAsSupervisor() {
        updateTaskStatus(taskId, STATUS_ARCHIVED) { success ->
            if (success) {
                Toast.makeText(this, "کار تأیید و بایگانی شد", Toast.LENGTH_SHORT).show()
                navigateToCartable()
            } else {
                Toast.makeText(this, "خطا در بایگانی", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToCartable() {
        val intent = Intent(this, CartableActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun updateTaskStatus(taskId: String, newStatus: String, callback: (Boolean) -> Unit) {
        val url = "${Config.BASE_URL}?action=updateTaskStatus"
        val jsonObject = JSONObject().apply {
            put("taskId", taskId)
            put("status", newStatus)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, jsonObject,
            { response -> callback(response.optString("status") == "success") },
            { error ->
                Log.e("CompleteTask", "Error updating status: ${error.message}")
                callback(false)
            }
        )
        VolleySingleton.getInstance(this).add(request)
    }

    private fun showRejectDialog() {
        val editText = EditText(this).apply {
            hint = "علت برگشت را وارد کنید..."
            inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLines(3)
        }

        AlertDialog.Builder(this)
            .setTitle("برگشت به مسئول کار")
            .setMessage("لطفاً دلیل برگشت کار را وارد کنید:")
            .setView(editText)
            .setPositiveButton("تأیید") { _, _ ->
                val reason = editText.text.toString().trim()
                if (reason.isNotEmpty()) {
                    rejectTask(reason)
                } else {
                    Toast.makeText(this, "لطفاً علت برگشت را وارد کنید", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun rejectTask(reason: String) {
        updateTaskStatus(taskId, STATUS_IN_PROGRESS) { _ -> }

        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        val currentUserName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"

        val systemLog = TaskLogModel(
            id = System.currentTimeMillis().toString(),
            taskId = taskId,
            userId = currentUserId,
            userName = currentUserName,
            date = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date()),
            startTime = "",
            endTime = "",
            actionDescription = "❌ کار توسط سرشیفت برگشت داده شد",
            assignedUsers = "",
            newStatus = STATUS_REJECTED,
            attachments = "",
            notes = "علت برگشت: $reason",
            heatLevel = 30,
            pollutionLevel = 0,
            workType = "3"
        )

        taskLogRepository.addTaskLog(
            log = systemLog,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "کار به مسئول برگشت داده شد", Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(this, "خطا: $message", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    data class UserStats(
        var totalDuration: Double = 0.0,
        var weightedHeat: Double = 0.0,
        var weightedPollution: Double = 0.0
    )
}