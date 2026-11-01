package com.workshop.mechanicbb1.ui.task.log

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.dialog.SelectGroupDialog
import TaskModel
import java.text.SimpleDateFormat
import java.util.*
import android.app.TimePickerDialog
import android.content.Intent
import android.util.Log
import android.view.View
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.ui.task.repository.TaskLogRepository
import java.util.Calendar
import ir.hamsaa.persiandatepicker.PersianDatePickerDialog
import ir.hamsaa.persiandatepicker.api.PersianPickerDate
import ir.hamsaa.persiandatepicker.api.PersianPickerListener
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.workshop.mechanicbb1.data.local.ReportLocalRepository
import com.workshop.mechanicbb1.data.model.Employee
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.ui.task.detail.TaskDetailActivity
import com.workshop.mechanicbb1.ui.task.evaluation.EvaluationActivity
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.workshop.mechanicbb1.utils.UserCache
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import kotlinx.coroutines.launch
import org.json.JSONObject



@Suppress("DEPRECATION")
class AddLogActivity : AppCompatActivity() {

    private lateinit var etActionDescription: EditText
    private lateinit var etDate: EditText
    private lateinit var etStartTime: EditText
    private lateinit var etEndTime: EditText
    private lateinit var tvGroupValue: TextView
    private lateinit var btnEditGroup: Button
    private lateinit var etNotes: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    private lateinit var tvDuration: TextView
       private var task: TaskModel? = null
    private var currentGroupIds: String = ""
    private lateinit var taskLogRepository: TaskLogRepository
    private var isEditMode = false
    private var editingLogId: String? = null
    private var editingLog: TaskLogModel? = null

    // متغیر برای جلوگیری از بارگذاری مکرر
    private var isUserCacheLoaded = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_log)

        // تنظیم Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (intent.getBooleanExtra("IS_EDIT_MODE", false)) {
            "ویرایش گزارش"
        } else {
            "ثبت گزارش جدید"
        }

        taskLogRepository = TaskLogRepository(this)

        initEditMode()
        initTaskData()
        initViews()
        setupDatePicker()
        setupTimePickers()
        setupGroupSelection()
        setupButtons()

        ensureUserCacheLoaded()
        updateGroupDisplay()
        setupExpandableCards()

        if (isEditMode) {
            loadLogForEdit()
            btnSubmit.text = "ویرایش گزارش"
        }
    }

    private fun ensureUserCacheLoaded() {
        // اگر کش خالی است یا هنوز بارگذاری نشده
        if (UserCache.getSize() == 0 && !isUserCacheLoaded) {
            isUserCacheLoaded = true
            UserCache.loadAllUsers {
                runOnUiThread {
                    updateGroupDisplay()
                }
            }
        }
    }

    private fun initEditMode() {
        isEditMode = intent.getBooleanExtra("IS_EDIT_MODE", false)
        editingLogId = intent.getStringExtra("LOG_ID")
        supportActionBar?.title = if (isEditMode) "ویرایش گزارش" else "ثبت گزارش جدید"
    }

    private fun initTaskData() {
        task = TaskModel(
            id = intent.getStringExtra("TASK_ID") ?: "",
            createDate = intent.getStringExtra("DATE") ?: "",
            title = intent.getStringExtra("TITLE") ?: "",
            description = intent.getStringExtra("DESC") ?: "",
            creator = intent.getStringExtra("CREATOR") ?: "",
            status = "1",
            assignedTo = intent.getStringExtra("ASSIGNED_TO") ?: "",
            responsible = intent.getStringExtra("RESPONSIBLE") ?: "",
            priority = intent.getIntExtra("PRIORITY", 50),
            urgency = intent.getStringExtra("URGENCY") ?: "",
            referredBy = "",
            scheduledDate = null
            // ← حذف: pendingInvites, unit, sub_unit, declaration_method,
            //        requester, request_date, initial_review, system_request_number
        )
        currentGroupIds = task?.assignedTo ?: ""
    }

    private fun initViews() {
        etActionDescription = findViewById(R.id.etActionDescription)
        etDate = findViewById(R.id.etDate)
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)
        tvGroupValue = findViewById(R.id.tvGroupValue)
        btnEditGroup = findViewById(R.id.btnEditGroup)
        etNotes = findViewById(R.id.etNotes)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnCancel = findViewById(R.id.btnCancel)
        tvDuration = findViewById(R.id.tvDuration)

        if (!isEditMode) {
            etStartTime.setText("09:00")
            etEndTime.setText("11:00")
            calculateDuration()
            etDate.setText(DateUtils.gregorianToShamsi())
        }
    }

    private fun loadLogForEdit() {
        val taskId = task?.id ?: ""
        val logId = editingLogId ?: ""

        taskLogRepository.getTaskLogs(taskId,
            onSuccess = { allLogs ->  // ← نام را عوض کن
                val log = allLogs.find { it.id == logId }
                if (log != null) {
                    populateLogData(log, allLogs)  // ← همه لاگ‌ها را پاس بده
                } else {
                    showErrorAndFinish("گزارش یافت نشد")
                }
            },
            onError = { message ->
                showErrorAndFinish("خطا در بارگذاری: $message")
            }
        )
    }

    private fun populateLogData(log: TaskLogModel, allLogs: List<TaskLogModel> = emptyList()) {

        editingLog = log
        etActionDescription.setText(log.actionDescription)
        etDate.setText(log.date)
        etStartTime.setText(log.startTime)
        etEndTime.setText(log.endTime)
        calculateDuration()

        currentGroupIds = log.assignedUsers
        updateGroupDisplay()
    }

    private fun showErrorAndFinish(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupDatePicker() {
        etDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val currentDate = etDate.text.toString().split("/")
        val initYear = currentDate.getOrNull(0)?.toIntOrNull() ?: 1405
        val initMonth = currentDate.getOrNull(1)?.toIntOrNull() ?: 1
        val initDay = currentDate.getOrNull(2)?.toIntOrNull() ?: 1

        PersianDatePickerDialog(this)
            .setPositiveButtonString("تأیید")
            .setNegativeButton("انصراف")
            .setTodayButton("امروز")
            .setTodayButtonVisible(true)
            .setInitDate(initYear, initMonth, initDay)
            .setMinYear(1404)
            .setMaxYear(PersianDatePickerDialog.THIS_YEAR + 1)
            .setListener(object : PersianPickerListener {
                override fun onDateSelected(persianPickerDate: PersianPickerDate) {
                    val year = persianPickerDate.persianYear
                    val month = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianMonth)
                    val day = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianDay)
                    etDate.setText("$year/$month/$day")
                }
                override fun onDismissed() { }
            })
            .show()
    }

    private fun setupTimePickers() {
        etStartTime.setOnClickListener {
            showTimePicker(etStartTime)
        }
        etEndTime.setOnClickListener {
            showTimePicker(etEndTime)
        }
    }

    private fun showTimePicker(editText: EditText) {
        val currentTime = editText.text.toString()
        val hour = if (currentTime.isNotEmpty() && currentTime.contains(":")) {
            currentTime.split(":")[0].toIntOrNull() ?: 8
        } else {
            8
        }

        val minute = if (currentTime.isNotEmpty() && currentTime.contains(":")) {
            currentTime.split(":")[1].toIntOrNull() ?: 0
        } else {
            0
        }

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minuteOfHour ->
                val time = String.format("%02d:%02d", hourOfDay, minuteOfHour)
                editText.setText(time)
                calculateDuration()
            },
            hour,
            minute,
            true
        )
        timePickerDialog.show()
    }

    private fun calculateDuration(): Int {
        val start = etStartTime.text.toString()
        val end = etEndTime.text.toString()

        if (start.isNotEmpty() && end.isNotEmpty() && start.contains(":") && end.contains(":")) {
            try {
                val startParts = start.split(":")
                val endParts = end.split(":")

                if (startParts.size == 2 && endParts.size == 2) {
                    val startHour = startParts[0].toInt()
                    val startMinute = startParts[1].toInt()
                    val endHour = endParts[0].toInt()
                    val endMinute = endParts[1].toInt()

                    var durationMinutes = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
                    if (durationMinutes < 0) durationMinutes += 24 * 60

                    val hours = durationMinutes / 60
                    val minutes = durationMinutes % 60

                    tvDuration.text = "مدت زمان: $hours ساعت و $minutes دقیقه"
                    tvDuration.visibility = View.VISIBLE

                    return durationMinutes  // ← برگرداندن عدد
                }
            } catch (e: Exception) {
                Log.e("AddLog", "Error calculating duration", e)
            }
        }
        tvDuration.visibility = View.GONE
        return 0
    }

    private fun setupGroupSelection() {
        btnEditGroup.setOnClickListener {
            val originalGroupIds = task?.assignedTo ?: ""
            val originalGroupIdList = originalGroupIds.split(",").filter { it.isNotEmpty() }

            // ساخت لیست کارمندانی که در گروه اصلی هستند با استفاده از UserCache
            val groupMembers = originalGroupIdList.mapNotNull { id ->
                val name = UserCache.getName(id)
                if (name != "نامشخص" && !name.startsWith("کاربر")) {
                    Employee(id, name, "")
                } else {
                    null
                }
            }

            // اگر لیست خالی است یا کش هنوز بارگذاری نشده، ابتدا بارگذاری کن
            if (groupMembers.isEmpty() && originalGroupIdList.isNotEmpty()) {
                Toast.makeText(this, "در حال بارگذاری اسامی کاربران...", Toast.LENGTH_SHORT).show()
                UserCache.loadAllUsers {
                    runOnUiThread {
                        setupGroupSelection()
                        btnEditGroup.performClick()
                    }
                }
                return@setOnClickListener
            }

            val dialog = SelectGroupDialog(
                currentGroupIds = currentGroupIds,
                availableEmployees = groupMembers
            ) { newGroupIds ->
                currentGroupIds = newGroupIds
                updateGroupDisplay()
            }
            dialog.show(supportFragmentManager, "SelectGroupDialog")
        }
    }

    private fun updateGroupDisplay() {
        if (currentGroupIds.isNotEmpty()) {
            val ids = currentGroupIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            if (ids.isEmpty()) {
                tvGroupValue.text = "تعیین نشده"
                return
            }

            val names = ids.mapNotNull { id ->
                val name = UserCache.getName(id)
                if (name != "نامشخص" && !name.startsWith("کاربر $id")) {
                    name
                } else {
                    null
                }
            }

            if (names.isNotEmpty()) {
                tvGroupValue.text = names.joinToString("، ")
            } else {
                tvGroupValue.text = ids.joinToString("، ")
            }
        } else {
            tvGroupValue.text = "تعیین نشده"
        }
    }
    private fun setupButtons() {
        btnSubmit.setOnClickListener {
            if (validateForm()) {
                disableButton(btnSubmit)
                if (isEditMode) updateLog() else submitLog()
            }
        }
        btnCancel.setOnClickListener { finish() }
    }
    private fun validateForm(): Boolean {
        if (etActionDescription.text.isNullOrBlank()) {
            Toast.makeText(this, "لطفاً شرح اقدام را وارد کنید", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etDate.text.isNullOrBlank()) {
            Toast.makeText(this, "لطفاً تاریخ را انتخاب کنید", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
    private fun getCurrentUserInfo(): Pair<String, String> {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"
        return Pair(userId, userName)
    }

    private fun createTaskLogModel(status: String): TaskLogModel {
        val (userId, userName) = getCurrentUserInfo()

        // اگر در حالت ویرایش هستیم، از لاگ اصلی استفاده کن
        val sourceLog = if (isEditMode) editingLog else null

        return TaskLogModel(
            id = if (isEditMode) editingLogId ?: "" else System.currentTimeMillis().toString(),
            taskId = task?.id ?: "",
            userId = userId,
            userName = userName,
            date = etDate.text.toString(),
            startTime = etStartTime.text.toString(),
            endTime = etEndTime.text.toString(),
            actionDescription = etActionDescription.text.toString(),
            assignedUsers = currentGroupIds,
            newStatus = "",
            attachments = sourceLog?.attachments ?: "",
            notes = etNotes.text.toString(),
            // ✅ مهم: اینا رو از لاگ اصلی نگه دار
            heatLevel = sourceLog?.heatLevel ?: 30,
            pollutionLevel = sourceLog?.pollutionLevel ?: 0,
            workType = sourceLog?.workType ?: "1",
            physicalDifficulty = sourceLog?.physicalDifficulty ?: 0,
            technicalComplexity = sourceLog?.technicalComplexity ?: 0,
            seenBy = sourceLog?.seenBy ?: emptyList(),
            duration = sourceLog?.duration ?: "",
            comments = sourceLog?.comments ?: "[]",
            taskUrgency = task?.urgency ?: "عادی"
        )
    }

    private fun createTaskLogModelWithId(status: String, id: String): TaskLogModel {
        val (userId, userName) = getCurrentUserInfo()

        return TaskLogModel(
            id = id,
            taskId = task?.id ?: "",
            userId = userId,
            userName = userName,
            date = etDate.text.toString(),
            startTime = etStartTime.text.toString(),
            endTime = etEndTime.text.toString(),
            actionDescription = etActionDescription.text.toString(),
            assignedUsers = currentGroupIds,
            newStatus = status,
            attachments = "",
            notes = etNotes.text.toString(),   // ← قبلاً getFinalNotes() بود
            heatLevel = 30,
            pollutionLevel = 0,
            workType = "1",
            physicalDifficulty = 0,
            technicalComplexity = 0,
            seenBy = emptyList(),
            duration = "",
            comments = "[]",
            taskUrgency = task?.urgency ?: "عادی"
        )
    }
    private fun submitLog() {
        val log = createTaskLogModel("")

        lifecycleScope.launch {
            try {
                val reportLocalRepo = ReportLocalRepository(this@AddLogActivity)
                val newId = reportLocalRepo.generateNextReportId(task?.id ?: "")
                val logWithId = log.copy(id = newId)

                reportLocalRepo.saveReportLocally(logWithId)
                OutboxHelper.enqueueAddTaskLog(this@AddLogActivity, logWithId)
                ReportSyncWorker.enqueue(this@AddLogActivity)

                enableButton(btnSubmit)
                Toast.makeText(this@AddLogActivity, "گزارش ثبت شد", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)

                // رفتن به EvaluationActivity
                val intent = Intent(this@AddLogActivity, EvaluationActivity::class.java).apply {
                    putExtra("TASK_ID", task?.id ?: "")
                    putExtra("LOG_ID", newId)
                    putExtra("TASK_TITLE", task?.title ?: "")
                    putExtra("TASK_DATE", etDate.text.toString())
                    putExtra("TASK_START_TIME", etStartTime.text.toString())
                    putExtra("TASK_END_TIME", etEndTime.text.toString())
                    putExtra("TASK_DESCRIPTION", etActionDescription.text.toString())
                    putExtra("ASSIGNED_USERS", currentGroupIds)
                    putExtra("DURATION_MINUTES", calculateDuration())
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                enableButton(btnSubmit)
                Toast.makeText(this@AddLogActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun updateLog() {
        val log = createTaskLogModel("")

        taskLogRepository.updateTaskLog(
            log = log,
            onSuccess = {
                runOnUiThread {
                    enableButton(btnSubmit)
                    Toast.makeText(this, "گزارش ویرایش شد", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            },
            onError = { message ->
                runOnUiThread {
                    enableButton(btnSubmit)
                    Toast.makeText(this, "خطا: $message", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    private fun disableButton(button: Button) {
        button.isEnabled = false
        button.alpha = 0.5f
    }
    private fun enableButton(button: Button) {
        button.isEnabled = true
        button.alpha = 1.0f
    }
    private fun setupExpandableCards() {
        // تیم اجرایی
        findViewById<LinearLayout>(R.id.headerTeam).setOnClickListener {
            toggleCard(R.id.contentTeam, R.id.ivExpandTeam)
        }

        // توضیحات تکمیلی
        findViewById<LinearLayout>(R.id.headerNotes).setOnClickListener {
            toggleCard(R.id.contentNotes, R.id.ivExpandNotes)
        }
    }

    private fun toggleCard(contentId: Int, iconId: Int) {
        val content = findViewById<LinearLayout>(contentId)
        val icon = findViewById<ImageView>(iconId)

        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            icon.rotation = 0f
        } else {
            content.visibility = View.VISIBLE
            icon.rotation = 180f
        }
    }
}