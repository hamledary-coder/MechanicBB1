package com.workshop.mechanicbb1.ui.task.log

import TaskModel
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.TaskLogWorkOrderProgressEntity
import com.workshop.mechanicbb1.data.local.repository.ReportLocalRepository
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.model.Employee
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.ui.task.dialog.SelectGroupDialog
import com.workshop.mechanicbb1.ui.task.evaluation.EvaluationActivity
import com.workshop.mechanicbb1.ui.task.log.dialog.ConfirmReportDialog
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache
import ir.hamsaa.persiandatepicker.PersianDatePickerDialog
import ir.hamsaa.persiandatepicker.api.PersianPickerDate
import ir.hamsaa.persiandatepicker.api.PersianPickerListener
import kotlinx.coroutines.launch
import java.util.Locale

@Suppress("DEPRECATION")
class AddLogActivity : AppCompatActivity() {

    // ============================================================
    // Views
    // ============================================================
    private lateinit var etDate: EditText
    private lateinit var etStartTime: EditText
    private lateinit var etEndTime: EditText
    private lateinit var tvGroupValue: TextView
    private lateinit var btnEditGroup: Button
    private lateinit var etNotes: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    private lateinit var tvDuration: TextView

    // ✅ WorkOrder Progress
    private lateinit var cardWorkOrderProgress: View
    private lateinit var tvWorkOrderProgressCount: TextView
    private lateinit var rvWorkOrderProgress: RecyclerView
    private var workOrderProgressAdapter: WorkOrderProgressAdapter? = null

    // ============================================================
    // Data
    // ============================================================
    private var task: TaskModel? = null
    private var currentGroupIds: String = ""
    private val workOrdersList = mutableListOf<WorkOrderModel>()

    private var selectedDate: String = ""

    private lateinit var taskLocalRepository: TaskLocalRepository
    private var isEditMode = false
    private var editingLogId: String? = null
    private var editingLog: TaskLogModel? = null

    private var isUserCacheLoaded = false

    // ============================================================
    // Lifecycle
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // پدینگ بالا و پایین رو به اندازه نوارها اضافه میکنیم
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            // به سیستم میگیم که این Insets رو مصرف کردیم و به بچه‌ها نده
            WindowInsetsCompat.CONSUMED
        }
        setContentView(R.layout.add_log_activity)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        taskLocalRepository = TaskLocalRepository(this)

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
        } else {
            loadWorkOrdersForTask { workOrders ->
                workOrdersList.clear()
                workOrdersList.addAll(workOrders)

                lifecycleScope.launch {
                    val existingProgresses = loadExistingProgresses()
                    setupWorkOrderProgressSection(existingProgresses)
                }
            }
        }
    }

    // ============================================================
    // Init
    // ============================================================
    private fun ensureUserCacheLoaded() {
        if (UserCache.getSize() == 0 && !isUserCacheLoaded) {
            isUserCacheLoaded = true
            UserCache.loadAllUsers {
                runOnUiThread { updateGroupDisplay() }
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
        )
        currentGroupIds = task?.assignedTo ?: ""
    }

    @SuppressLint("SetTextI18n")
    private fun initViews() {
        etDate = findViewById(R.id.etDate)
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)
        tvGroupValue = findViewById(R.id.tvGroupValue)
        btnEditGroup = findViewById(R.id.btnEditGroup)
        etNotes = findViewById(R.id.etNotes)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnCancel = findViewById(R.id.btnCancel)
        tvDuration = findViewById(R.id.tvDuration)

        cardWorkOrderProgress = findViewById(R.id.cardWorkOrderProgress)
        tvWorkOrderProgressCount = findViewById(R.id.tvWorkOrderProgressCount)
        rvWorkOrderProgress = findViewById(R.id.rvWorkOrderProgress)
        rvWorkOrderProgress.layoutManager = LinearLayoutManager(this)
        rvWorkOrderProgress.isNestedScrollingEnabled = false
        rvWorkOrderProgress.setHasFixedSize(false)

        // ✅ فقط برای ثبت جدید
        if (!isEditMode) {
            etStartTime.setText("09:00")
            etEndTime.setText("11:00")
            calculateDuration()

            selectedDate = DateUtils.gregorianToShamsi()
            updateDateDisplay()
        }
        // برای ویرایش، populateLogData (از loadLogForEdit) این کارو می‌کنه
    }

    // ============================================================
    // WorkOrder Progress
    // ============================================================
    private fun loadWorkOrdersForTask(onLoaded: (List<WorkOrderModel>) -> Unit) {
        val taskId = task?.id ?: ""
        if (taskId.isEmpty()) {
            onLoaded(emptyList())
            return
        }

        lifecycleScope.launch {
            try {
                val allWorkOrders = taskLocalRepository.getWorkOrdersForTaskOnce(taskId)
                val db = AppDatabase.getInstance(this@AddLogActivity)

                // ✅ فیلتر: فقط WorkOrderهایی که «انجام شد» نیستن (از work_orders.progress)
                val activeWorkOrders = allWorkOrders.filter { wo ->
                    val entity = db.workOrderDao().getById(wo.id)
                    entity == null || entity.progress < 100
                }

                onLoaded(activeWorkOrders)
            } catch (e: Exception) {
                Log.e("AddLog", "Error loading work orders: ${e.message}")
                onLoaded(emptyList())
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupWorkOrderProgressSection(
        existingProgresses: Map<String, WorkOrderProgressAdapter.ProgressData> = emptyMap()
    ) {
        if (workOrdersList.isEmpty()) {
            cardWorkOrderProgress.visibility = View.GONE
            return
        }

        cardWorkOrderProgress.visibility = View.VISIBLE
        tvWorkOrderProgressCount.text = "(${workOrdersList.size})"

        workOrderProgressAdapter = WorkOrderProgressAdapter(
            workOrders = workOrdersList,
            existingProgresses = existingProgresses
        )
        rvWorkOrderProgress.adapter = workOrderProgressAdapter

        rvWorkOrderProgress.post {
            rvWorkOrderProgress.requestLayout()
            rvWorkOrderProgress.invalidate()
        }
    }

    // ============================================================
    // Edit Mode
    // ============================================================
    private fun loadLogForEdit() {
        val logId = editingLogId ?: ""

        lifecycleScope.launch {
            try {
                val reportRepo = ReportLocalRepository(this@AddLogActivity)
                val log = reportRepo.getReportById(logId)
                if (log != null) {
                    populateLogData(log)

                    val workOrders = taskLocalRepository.getWorkOrdersForTaskOnce(task?.id ?: "")
                    workOrdersList.clear()
                    workOrdersList.addAll(workOrders)

                    // ✅ برای ویرایش: progressهای **همین گزارش**
                    val existingProgresses = loadEditProgresses(logId)
                    setupWorkOrderProgressSection(existingProgresses)

                } else {
                    showErrorAndFinish("گزارش یافت نشد")
                }
            } catch (e: Exception) {
                showErrorAndFinish("خطا در بارگذاری: ${e.message}")
            }
        }
    }

    private suspend fun loadEditProgresses(logId: String): Map<String, WorkOrderProgressAdapter.ProgressData> {
        val db = AppDatabase.getInstance(this@AddLogActivity)
        val result = mutableMapOf<String, WorkOrderProgressAdapter.ProgressData>()

        // progressهای **همین گزارش** (برای نمایش)
        val currentProgresses = db.taskLogWorkOrderProgressDao().getByLogOnce(logId)

        currentProgresses.forEach { p ->
            // progress قبلی — قبل از این گزارش
            val previous = db.taskLogWorkOrderProgressDao()
                .getLatestForWorkOrderBeforeLog(p.workOrderId, logId)

            val prevStatus = when (previous?.progress) {
                null -> null   // اولین گزارش
                100 -> WorkOrderProgressAdapter.Status.COMPLETED
                0 -> WorkOrderProgressAdapter.Status.NOT_COMPLETED
                else -> WorkOrderProgressAdapter.Status.IN_PROGRESS
            }

            result[p.workOrderId] = WorkOrderProgressAdapter.ProgressData(
                workOrderId = p.workOrderId,
                status = when (p.progress) {
                    100 -> WorkOrderProgressAdapter.Status.COMPLETED
                    0 -> WorkOrderProgressAdapter.Status.NOT_COMPLETED
                    else -> WorkOrderProgressAdapter.Status.IN_PROGRESS
                },
                progress = p.progress,
                hasDetails = p.hasDetails,
                detailsNotes = p.detailsNotes,
                previousDetailsNotes = previous?.detailsNotes ?: "",
                previousProgress = previous?.progress ?: -1,   // ✅ جدید
                previousStatus = prevStatus                    // ✅ جدید
            )
        }

        return result
    }

    private suspend fun loadExistingProgresses(): Map<String, WorkOrderProgressAdapter.ProgressData> {
        val db = AppDatabase.getInstance(this@AddLogActivity)
        val result = mutableMapOf<String, WorkOrderProgressAdapter.ProgressData>()

        workOrdersList.forEach { wo ->
            val latest = db.taskLogWorkOrderProgressDao()
                .getLatestForWorkOrderByDate(wo.id)

            if (latest != null) {
                val prevStatus = when (latest.progress) {
                    100 -> WorkOrderProgressAdapter.Status.COMPLETED
                    0 -> WorkOrderProgressAdapter.Status.NOT_COMPLETED
                    else -> WorkOrderProgressAdapter.Status.IN_PROGRESS
                }

                result[wo.id] = WorkOrderProgressAdapter.ProgressData(
                    workOrderId = wo.id,
                    status = prevStatus,              // فعلی = قبلی (شروع از قبلی)
                    progress = latest.progress,
                    hasDetails = false,
                    detailsNotes = "",
                    previousDetailsNotes = latest.detailsNotes,
                    previousProgress = latest.progress,     // ✅ جدید
                    previousStatus = prevStatus             // ✅ جدید
                )
            }
            // اگه latest == null → اولین گزارش → previousProgress=-1 (پیش‌فرض)
        }

        return result
    }

    private fun populateLogData(log: TaskLogModel) {
        editingLog = log

        // ✅ تاریخ خالص
        selectedDate = log.date
        updateDateDisplay()

        etStartTime.setText(log.startTime)
        etEndTime.setText(log.endTime)
        etNotes.setText(log.notes)
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

    // ============================================================
    // Date Picker
    // ============================================================
    private fun setupDatePicker() {
        etDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        // ✅ تاریخ خالص
        val dateText = selectedDate.ifEmpty {
            etDate.text.toString().substringBefore(" (").trim()
        }

        val dialog = PersianDatePickerDialog(this)
            .setPositiveButtonString("تأیید")
            .setNegativeButton("انصراف")
            .setTodayButton("امروز")
            .setTodayButtonVisible(true)
            .setMinYear(1404)
            .setMaxYear(PersianDatePickerDialog.THIS_YEAR + 1)
            .setListener(object : PersianPickerListener {
                @SuppressLint("SetTextI18n")
                override fun onDateSelected(persianPickerDate: PersianPickerDate) {
                    val year = persianPickerDate.persianYear
                    val month = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianMonth)
                    val day = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianDay)
                    selectedDate = "$year/$month/$day"
                    updateDateDisplay()
                }
                override fun onDismissed() { }
            })

        // ✅ setInitDate آخر، فقط اگه تاریخ داریم
        if (dateText.isNotEmpty()) {
            val parts = dateText.split("/")
            val y = parts.getOrNull(0)?.toIntOrNull()
            val m = parts.getOrNull(1)?.toIntOrNull()
            val d = parts.getOrNull(2)?.toIntOrNull()

            if (y != null && m != null && d != null) {
                dialog.setInitDate(y, m, d)
            }
        }

        dialog.show()
    }

    /**
     * نمایش تاریخ به همراه نام روز در etDate
     * مثال: "1405/08/19 (شنبه)"
     */
    @SuppressLint("SetTextI18n")
    private fun updateDateDisplay() {
        if (selectedDate.isEmpty()) return
        val dayName = DateUtils.getDayNameFromDate(selectedDate)
        val display = if (dayName.isNotEmpty()) "$selectedDate ($dayName)" else selectedDate
        etDate.setText(display)
    }

    // ============================================================
    // Time Picker
    // ============================================================
    private fun setupTimePickers() {
        etStartTime.setOnClickListener { showTimePicker(etStartTime) }
        etEndTime.setOnClickListener { showTimePicker(etEndTime) }
    }

    @SuppressLint("DefaultLocale")
    private fun showTimePicker(editText: EditText) {
        val currentTime = editText.text.toString()
        val hour = if (currentTime.contains(":")) currentTime.split(":")[0].toIntOrNull() ?: 8 else 8
        val minute = if (currentTime.contains(":")) currentTime.split(":")[1].toIntOrNull() ?: 0 else 0

        TimePickerDialog(
            this,
            { _, hourOfDay, minuteOfHour ->
                val time = String.format("%02d:%02d", hourOfDay, minuteOfHour)
                editText.setText(time)
                calculateDuration()
            },
            hour,
            minute,
            true
        ).show()
    }

    @SuppressLint("SetTextI18n")
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

                    return durationMinutes
                }
            } catch (e: Exception) {
                Log.e("AddLog", "Error calculating duration", e)
            }
        }
        tvDuration.visibility = View.GONE
        return 0
    }

    // ============================================================
    // Group Selection
    // ============================================================
    private fun setupGroupSelection() {
        btnEditGroup.setOnClickListener {
            val originalGroupIds = task?.assignedTo ?: ""
            val originalGroupIdList = originalGroupIds.split(",").filter { it.isNotEmpty() }

            val groupMembers = originalGroupIdList.mapNotNull { id ->
                val name = UserCache.getName(id)
                if (name != "نامشخص" && !name.startsWith("کاربر")) {
                    Employee(id, name, "")
                } else null
            }

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
                if (name != "نامشخص" && !name.startsWith("کاربر $id")) name else null
            }

            tvGroupValue.text = if (names.isNotEmpty()) names.joinToString("، ") else ids.joinToString("، ")
        } else {
            tvGroupValue.text = "تعیین نشده"
        }
    }

    // ============================================================
    // Buttons
    // ============================================================
    private fun setupButtons() {
        btnSubmit.setOnClickListener {
            if (validateForm()) {
                showConfirmDialog()
            }
        }
        btnCancel.setOnClickListener { finish() }
    }

    private fun showConfirmDialog() {
        val progressData = workOrderProgressAdapter?.getProgressData() ?: emptyList()

        val previewData = ConfirmReportDialog.ReportPreviewData(
            date = selectedDate,   // ✅ تاریخ خالص
            startTime = etStartTime.text.toString(),
            endTime = etEndTime.text.toString(),
            durationMinutes = calculateDuration(),
            groupUserIds = currentGroupIds,
            workOrders = workOrdersList,
            progressData = progressData,
            notes = etNotes.text.toString()
        )

        ConfirmReportDialog(this, previewData) {
            disableButton(btnSubmit)
            if (isEditMode) updateLog() else submitLog()
        }.show()
    }

    private fun validateForm(): Boolean {
        // ✅ بررسی تاریخ خالص
        if (selectedDate.isBlank()) {
            Toast.makeText(this, "لطفاً تاریخ را انتخاب کنید", Toast.LENGTH_SHORT).show()
            return false
        }

        if (workOrdersList.isNotEmpty()) {
            val adapter = workOrderProgressAdapter
            if (adapter == null) {
                Toast.makeText(this, "خطا: بخش درخواست‌ها بارگذاری نشده", Toast.LENGTH_SHORT).show()
                return false
            }
            val progresses = adapter.getProgressData()
            if (progresses.size != workOrdersList.size) {
                Toast.makeText(this, "لطفاً وضعیت همه‌ی درخواست‌ها را مشخص کنید", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        // ✅ حداقل یکی از:
        //  - تغییر وضعیت/پیشرفت تو WorkOrder
        //  - شرح جدید تو WorkOrder
        //  - notes پر
        val adapter = workOrderProgressAdapter
        val hasAnyChange = adapter?.getProgressData()?.any { p ->
            val hasProgressChange = p.previousProgress == -1 || p.progress != p.previousProgress
            val hasStatusChange = p.previousStatus == null || p.status != p.previousStatus
            val hasNewDetails = p.hasDetails && p.detailsNotes.isNotBlank() &&
                    p.detailsNotes != p.previousDetailsNotes
            hasProgressChange || hasStatusChange || hasNewDetails
        } ?: false

        val hasNotes = !etNotes.text.isNullOrBlank()

        if (!hasAnyChange && !hasNotes) {
            Toast.makeText(
                this,
                "هیچ تغییری برای ثبت وجود ندارد. حداقل یکی از درخواست‌ها را تغییر دهید یا توضیحی بنویسید",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        return true
    }

    // ============================================================
    // Build Models
    // ============================================================
    private fun getCurrentUserInfo(): Pair<String, String> {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"
        return Pair(userId, userName)
    }

<<<<<<< Updated upstream
    private fun buildActionDescriptionFromProgress(): String {
        val adapter = workOrderProgressAdapter ?: return ""
        val progresses = adapter.getProgressData()
        if (progresses.isEmpty()) return ""
=======
<<<<<<< Updated upstream
    private fun createTaskLogModel(status: String): TaskLogModel {
=======
    private fun buildActionDescriptionFromProgress(): String {
        val sb = StringBuilder()

        // ==========================================
        // 1. گزارش فعالیت‌های مربوط به WorkOrder
        // ==========================================
        val adapter = workOrderProgressAdapter
        val progresses = adapter?.getProgressData() ?: emptyList()

        val visibleProgresses = progresses.filter { p ->
            val hasProgressChange =
                p.previousProgress == -1 || p.progress != p.previousProgress

            val hasStatusChange =
                p.previousStatus == null || p.status != p.previousStatus

            val hasNewDetails =
                p.hasDetails &&
                        p.detailsNotes.isNotBlank() &&
                        p.detailsNotes != p.previousDetailsNotes

            hasProgressChange || hasStatusChange || hasNewDetails
        }

        visibleProgresses.forEachIndexed { index, p ->

            val wo = workOrdersList.find { it.id == p.workOrderId }
                ?: return@forEachIndexed

            val statusLine = when (p.status) {
                WorkOrderProgressAdapter.Status.COMPLETED ->
                    "✅ ${wo.title}: انجام شد"

                WorkOrderProgressAdapter.Status.IN_PROGRESS ->
                    "⏳ ${p.progress} درصد از درخواست مربوط به «${wo.title}» انجام شده (ادامه دارد)"

                WorkOrderProgressAdapter.Status.NOT_COMPLETED ->
                    "❌ ${wo.title}: انجام نشد"
            }

            sb.append(statusLine)

            if (p.hasDetails && p.detailsNotes.isNotBlank()) {
                sb.append("\n   📝 ${p.detailsNotes}")
            }

            if (index < visibleProgresses.size - 1) {
                sb.append("\n\n")
            }
        }

        // ==========================================
        // 2. فعالیت‌های خارج از WorkOrder
        // ==========================================
        val otherActivities = etNotes.text.toString().trim()

        if (otherActivities.isNotBlank()) {

            if (sb.isNotEmpty()) {
                sb.append("\n\n")
            }

            sb.append("🔧 گزارش کارهای داخلی:\n")
            sb.append(otherActivities)
        }

        // ==========================================
        // خروجی نهایی گزارش
        // ==========================================
        return sb.toString().trim()
    }

    private fun createTaskLogModel(status: String, id: String? = null): TaskLogModel {
>>>>>>> Stashed changes
        val (userId, userName) = getCurrentUserInfo()
>>>>>>> Stashed changes

        // ✅ فیلتر: فقط WorkOrderهایی که **تغییر** داشتن
        val visibleProgresses = progresses.filter { p ->
            val hasProgressChange = p.previousProgress == -1 || p.progress != p.previousProgress
            val hasStatusChange = p.previousStatus == null || p.status != p.previousStatus
            val hasNewDetails = p.hasDetails && p.detailsNotes.isNotBlank() &&
                    p.detailsNotes != p.previousDetailsNotes

            val hasAnyChange = hasProgressChange || hasStatusChange || hasNewDetails

            // NOT_COMPLETED بدون تغییر → حذف
            if (p.status == WorkOrderProgressAdapter.Status.NOT_COMPLETED) {
                hasAnyChange
            } else {
                hasAnyChange
            }
        }

        if (visibleProgresses.isEmpty()) return ""

        val sb = StringBuilder()
        visibleProgresses.forEachIndexed { index, p ->
            val wo = workOrdersList.find { it.id == p.workOrderId } ?: return@forEachIndexed

            val statusLine = when (p.status) {
                WorkOrderProgressAdapter.Status.COMPLETED ->
                    "✅ ${wo.title}: انجام شد"
                WorkOrderProgressAdapter.Status.IN_PROGRESS ->
                    "⏳ ${p.progress} درصد از درخواست مربوط به «${wo.title}» انجام شده (ادامه دارد)"
                WorkOrderProgressAdapter.Status.NOT_COMPLETED ->
                    "❌ ${wo.title}: انجام نشد"
            }

            sb.append(statusLine)

            if (p.hasDetails && p.detailsNotes.isNotBlank()) {
                sb.append("\n   📝 ${p.detailsNotes}")
            }

            if (index < visibleProgresses.size - 1) {
                sb.append("\n\n")
            }
        }
        return sb.toString().trim()
    }

    private fun createTaskLogModel(status: String, id: String? = null): TaskLogModel {
        val (userId, userName) = getCurrentUserInfo()
        val sourceLog = if (isEditMode) editingLog else null

        val actionDescription = buildActionDescriptionFromProgress()

        return TaskLogModel(
            id = id ?: if (isEditMode) editingLogId ?: "" else System.currentTimeMillis().toString(),
            taskId = task?.id ?: "",
            userId = userId,
            userName = userName,
            date = selectedDate,
            startTime = etStartTime.text.toString(),
            endTime = etEndTime.text.toString(),
            actionDescription = actionDescription,
            assignedUsers = currentGroupIds,
            newStatus = status,
            attachments = sourceLog?.attachments ?: "",
            notes = etNotes.text.toString(),
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

    private fun buildWorkOrderProgressEntities(taskLogId: String): List<TaskLogWorkOrderProgressEntity> {
        val adapter = workOrderProgressAdapter ?: return emptyList()

        return adapter.getProgressData().map { p ->
            TaskLogWorkOrderProgressEntity(
                id = "p_${taskLogId}_${p.workOrderId}",
                taskLogId = taskLogId,
                workOrderId = p.workOrderId,
                progress = p.progress,
                hasDetails = p.hasDetails,
                detailsNotes = p.detailsNotes,
                nextAction = "",
                createdAt = System.currentTimeMillis()
            )
        }
    }

    // ============================================================
    // Submit Log (Create)
    // ============================================================
    private fun submitLog() {
        lifecycleScope.launch {
            try {
                val reportLocalRepo = ReportLocalRepository(this@AddLogActivity)
                val newId = reportLocalRepo.generateNextReportId(task?.id ?: "")
                val log = createTaskLogModel("", newId)

                reportLocalRepo.saveReportLocally(log)

                val progresses = buildWorkOrderProgressEntities(newId)
                val db = AppDatabase.getInstance(this@AddLogActivity)

                if (progresses.isNotEmpty()) {
                    // ۱. INSERT تاریخچه
                    db.taskLogWorkOrderProgressDao().insertAll(progresses)

                    // ۲. UPDATE work_orders.progress (Room) + Outbox (سرور)
                    progresses.forEach { p ->
                        val latest = db.taskLogWorkOrderProgressDao()
                            .getLatestForWorkOrderByDate(p.workOrderId)
                        val newProgress = latest?.progress ?: 0

                        db.workOrderDao().updateProgress(p.workOrderId, newProgress)

                        val woEntity = db.workOrderDao().getById(p.workOrderId)
                        if (woEntity != null) {
                            val woModel = WorkOrderModel(
                                id = woEntity.id,
                                requestNumber = woEntity.requestNumber ?: "",
                                title = woEntity.title,
                                requestClass = woEntity.requestClass,
                                requestDate = woEntity.requestDate,
                                requester = woEntity.requester,
                                approver = woEntity.approver ?: "",
                                unit = woEntity.unit,
                                subUnit = woEntity.subUnit,
                                equipmentName = woEntity.equipmentName,
                                description = woEntity.description,
                                externalStatus = woEntity.externalStatus,
                                progress = newProgress,
                                source = woEntity.source
                            )
                            OutboxHelper.enqueueUpdateWorkOrder(this@AddLogActivity, woModel)
                        }
                    }
                }

                autoRestoreTaskStatusIfAwaiting()

                OutboxHelper.enqueueAddTaskLog(this@AddLogActivity, log, progresses)
                ReportSyncWorker.enqueue(this@AddLogActivity)

                enableButton(btnSubmit)
                Toast.makeText(this@AddLogActivity, "گزارش ثبت شد", Toast.LENGTH_SHORT).show()

                // ✅ setResult با TASK_ID (برای QuickLogActivity)
                setResult(RESULT_OK, Intent().apply {
                    putExtra("TASK_ID", task?.id ?: "")
                })

                val intent = Intent(this@AddLogActivity, EvaluationActivity::class.java).apply {
                    putExtra("TASK_ID", task?.id ?: "")
                    putExtra("LOG_ID", newId)
                    putExtra("TASK_TITLE", task?.title ?: "")
                    putExtra("TASK_DATE", selectedDate)
                    putExtra("TASK_START_TIME", etStartTime.text.toString())
                    putExtra("TASK_END_TIME", etEndTime.text.toString())
                    putExtra("TASK_DESCRIPTION", log.actionDescription)
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

    /**
     * اگر تسک در وضعیت 21/31 بود (منتظر گزارش)، بعد از ثبت گزارش
     * به وضعیت پایه (2 یا 3) برگردانده شود و در اختیار مسئول قرار گیرد.
     *
     * نکته: چون در فرم گزارش امکان انتخاب newStatus وجود ندارد،
     * همیشه به وضعیت پایه برمی‌گردیم (در حال انجام یا متوقف).
     */
    private fun autoRestoreTaskStatusIfAwaiting() {
        val taskId = task?.id ?: return

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@AddLogActivity)
                // ✅ getById (نه getByIdOnce)
                val currentStatus = db.taskDao().getById(taskId)?.status ?: return@launch

                if (!Config.StatusCode.isAwaitingReport(currentStatus)) return@launch

                val baseStatus = Config.StatusCode.unwrapAwaitingReport(currentStatus)

                // ✅ updateStatus (متد جدید TaskDao)
                db.taskDao().updateStatus(taskId, baseStatus)
                OutboxHelper.enqueueUpdateTaskStatus(this@AddLogActivity, taskId, baseStatus)
                ReportSyncWorker.enqueue(this@AddLogActivity)

                Log.d("AddLog", "Auto-restored task $taskId: $currentStatus -> $baseStatus")
            } catch (e: Exception) {
                Log.e("AddLog", "Error auto-restoring task status: ${e.message}")
            }
        }
    }

    // ============================================================
    // Update Log (Edit)
    // ============================================================
    private fun updateLog() {
        val log = createTaskLogModel("")
        val logId = editingLogId ?: ""

        // ✅ اگه actionDescription خالی شد (یعنی progressها تغییر نکردن)،
        //    از متن گزارش قبلی استفاده کن
        val finalLog = if (log.actionDescription.isBlank() && editingLog != null) {
            log.copy(actionDescription = editingLog!!.actionDescription)
        } else {
            log
        }

        lifecycleScope.launch {
            try {
                val reportLocalRepo = ReportLocalRepository(this@AddLogActivity)

                reportLocalRepo.saveReportLocally(finalLog)   // ← finalLog

                val progresses = buildWorkOrderProgressEntities(logId)
                val db = AppDatabase.getInstance(this@AddLogActivity)

                if (progresses.isNotEmpty()) {
                    // ۱. DELETE + INSERT تاریخچه
                    db.taskLogWorkOrderProgressDao().deleteByLog(logId)
                    db.taskLogWorkOrderProgressDao().insertAll(progresses)

                    // ۲. UPDATE work_orders.progress (Room) + Outbox (سرور)
                    progresses.forEach { p ->
                        // ... بدون تغییر
                    }
                }

                autoRestoreTaskStatusIfAwaiting()

                OutboxHelper.enqueueUpdateTaskLog(this@AddLogActivity, finalLog)   // ← finalLog
                ReportSyncWorker.enqueue(this@AddLogActivity)

                enableButton(btnSubmit)
                Toast.makeText(this@AddLogActivity, "گزارش ویرایش شد", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                enableButton(btnSubmit)
                Toast.makeText(this@AddLogActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================================
    // Helpers
    // ============================================================
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