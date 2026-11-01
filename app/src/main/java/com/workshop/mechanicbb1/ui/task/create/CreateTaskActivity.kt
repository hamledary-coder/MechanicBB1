package com.workshop.mechanicbb1.ui.task.create

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.TaskLocalRepository
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.data.sync.TaskMapper
import com.workshop.mechanicbb1.utils.DateUtils
import TaskModel
import ir.hamsaa.persiandatepicker.PersianDatePickerDialog
import ir.hamsaa.persiandatepicker.api.PersianPickerDate
import ir.hamsaa.persiandatepicker.api.PersianPickerListener
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.flow.first

@Suppress("DEPRECATION")
class CreateTaskActivity : AppCompatActivity() {

    private var isEdit = false
    private var taskId: String? = null
    private lateinit var taskLocalRepository: TaskLocalRepository

    // ویوها
    private lateinit var btnSubmit: Button
    private lateinit var etTitle: EditText
    private lateinit var etTaskDescription: EditText
    private lateinit var etScheduledDate: EditText
    private lateinit var rgUrgency: RadioGroup
    private lateinit var sliderPriority: Slider
    private lateinit var tvPriorityValue: TextView
    private lateinit var tvWorkOrdersSummary: TextView
    private lateinit var btnPickWorkOrders: Button

    // انتخاب WorkOrderها
    private val selectedWorkOrderIds = mutableSetOf<String>()
    private var availableWorkOrders: List<WorkOrderModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.taskcreate_activity)

        // ✅ Toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        taskLocalRepository = TaskLocalRepository(this)

        initViews()
        setupDatePicker()
        setupWorkOrderPicker()
        loadWorkOrders()
        loadEditDataIfNeeded()

        btnSubmit.setOnClickListener {
            if (validateFields()) {
                saveTask()
            }
        }
    }

    private fun initViews() {
        btnSubmit = findViewById(R.id.btnSubmitTask)
        etTitle = findViewById(R.id.etTaskTitle)
        etTaskDescription = findViewById(R.id.etTaskDescription)
        etScheduledDate = findViewById(R.id.etScheduledDate)
        rgUrgency = findViewById(R.id.rgUrgency)
        sliderPriority = findViewById(R.id.sliderPriority)
        tvPriorityValue = findViewById(R.id.tvPriorityValue)
        tvWorkOrdersSummary = findViewById(R.id.tvWorkOrdersSummary)
        btnPickWorkOrders = findViewById(R.id.btnPickWorkOrders)
        btnPickWorkOrders.isEnabled = false

        sliderPriority.addOnChangeListener { _, value, _ ->
            tvPriorityValue.text = value.toInt().toString()
        }

        // پیش‌فرض: امروز
        etScheduledDate.setText(DateUtils.gregorianToShamsi())
    }

    private fun setupDatePicker() {
        val btnCalendar = findViewById<ImageButton>(R.id.btnScheduledDatePicker)
        btnCalendar.setOnClickListener { showPersianDatePicker() }
        etScheduledDate.setOnClickListener { showPersianDatePicker() }
    }

    @SuppressLint("SetTextI18n")
    private fun showPersianDatePicker() {
        val currentDate = etScheduledDate.text.toString().split("/")
        val initYear = currentDate.getOrNull(0)?.toIntOrNull() ?: 1405
        val initMonth = currentDate.getOrNull(1)?.toIntOrNull() ?: 1
        val initDay = currentDate.getOrNull(2)?.toIntOrNull() ?: 1

        PersianDatePickerDialog(this)
            .setPositiveButtonString("تأیید")
            .setNegativeButton("انصراف")
            .setTodayButton("امروز")
            .setTodayButtonVisible(true)
            .setInitDate(initYear, initMonth, initDay)
            .setMinYear(1400)
            .setMaxYear(PersianDatePickerDialog.THIS_YEAR)
            .setListener(object : PersianPickerListener {
                override fun onDateSelected(persianPickerDate: PersianPickerDate) {
                    val year = persianPickerDate.persianYear
                    val month = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianMonth)
                    val day = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianDay)
                    etScheduledDate.setText("$year/$month/$day")
                }
                override fun onDismissed() { }
            })
            .show()
    }

    private fun setupWorkOrderPicker() {
        btnPickWorkOrders.setOnClickListener {
            if (availableWorkOrders.isEmpty()) {
                Toast.makeText(
                    this,
                    "هیچ درخواست بازی وجود نداره. لطفاً از صفحه‌ی درخواست‌ها sync کنید.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            TaskCreateWorkOrderPickerDialog(
                context = this,
                availableWorkOrders = availableWorkOrders,
                preselectedIds = selectedWorkOrderIds,
                onConfirm = { newIds ->
                    selectedWorkOrderIds.clear()
                    selectedWorkOrderIds.addAll(newIds)
                    updateWorkOrdersSummary()
                }
            ).show()
        }
    }

    private fun loadWorkOrders() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@CreateTaskActivity)
                val entities = db.workOrderDao().getOpenForPicker()
                availableWorkOrders = entities.map { entity ->
                    WorkOrderModel(
                        id = entity.id,
                        requestNumber = entity.requestNumber,
                        title = entity.title,
                        requestClass = entity.requestClass,
                        requestDate = entity.requestDate,
                        requester = entity.requester,
                        approver = entity.approver ?: "",
                        unit = entity.unit,
                        subUnit = entity.subUnit,
                        equipmentName = entity.equipmentName,
                        description = entity.description,
                        externalStatus = entity.externalStatus,
                        progress = entity.progress,
                        source = entity.source
                    )
                }
                btnPickWorkOrders.isEnabled = true   // ← این خط
            } catch (e: Exception) {
                btnPickWorkOrders.isEnabled = true   // ← این خط
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun updateWorkOrdersSummary() {
        if (selectedWorkOrderIds.isEmpty()) {
            tvWorkOrdersSummary.text = "هیچ درخواستی انتخاب نشده"
        } else {
            tvWorkOrdersSummary.text = "${selectedWorkOrderIds.size} درخواست انتخاب شده"
        }
    }

    private fun loadEditDataIfNeeded() {
        isEdit = intent.getBooleanExtra("IS_EDIT", false)
        taskId = intent.getStringExtra("TASK_ID")

        if (isEdit) {
            btnSubmit.text = "بروزرسانی کار"
            supportActionBar?.title = "ویرایش کار"

            etTitle.setText(intent.getStringExtra("TITLE"))
            etTaskDescription.setText(intent.getStringExtra("DESC"))
            etScheduledDate.setText(intent.getStringExtra("SCHEDULED_DATE") ?: "")

            // فوریت
            val urgency = intent.getStringExtra("URGENCY") ?: "عادی"
            when (urgency) {
                "خیلی زیاد" -> rgUrgency.check(R.id.rbUrgencyVeryHigh)
                "زیاد" -> rgUrgency.check(R.id.rbUrgencyHigh)
                else -> rgUrgency.check(R.id.rbUrgencyNormal)
            }

            // اولویت
            val priority = intent.getIntExtra("PRIORITY", 50)
            sliderPriority.value = priority.toFloat()
            tvPriorityValue.text = priority.toString()

            // بارگذاری WorkOrderهای فعلی
            loadSelectedWorkOrders()
        }
    }

    private fun loadSelectedWorkOrders() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@CreateTaskActivity)
                val mappings = db.taskWorkOrderMappingDao().getByTaskOnce(taskId ?: "")
                selectedWorkOrderIds.clear()
                selectedWorkOrderIds.addAll(mappings.map { it.workOrderId })
                updateWorkOrdersSummary()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun validateFields(): Boolean {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "عنوان کار الزامی است", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveTask() {
        if (isEdit) {
            updateTaskOffline()
        } else {
            createTaskOffline()
        }
    }
    private fun createTaskOffline() {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val creatorRowId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""

        if (creatorRowId.isEmpty()) {
            Toast.makeText(this, "خطا: شناسه کاربر یافت نشد", Toast.LENGTH_LONG).show()
            return
        }

        btnSubmit.isEnabled = false
        btnSubmit.text = "در حال ذخیره..."

        val title = etTitle.text.toString().trim()
        val description = etTaskDescription.text.toString().trim()
        val scheduledDate = etScheduledDate.text.toString().trim().takeIf { it.isNotEmpty() }
        val urgency = when (rgUrgency.checkedRadioButtonId) {
            R.id.rbUrgencyVeryHigh -> "خیلی زیاد"
            R.id.rbUrgencyHigh -> "زیاد"
            else -> "عادی"
        }
        val priority = sliderPriority.value.toInt()

        lifecycleScope.launch {
            try {
                // ✅ id موقت: tmp-{userId}-{max+1}
                val newTaskId = taskLocalRepository.generateTempTaskId(creatorRowId)

                val newTask = TaskModel(
                    id = newTaskId,
                    createDate = DateUtils.gregorianToShamsi(),
                    title = title,
                    description = description,
                    creator = creatorRowId,
                    status = "1",
                    assignedTo = "",
                    responsible = "",
                    priority = priority,
                    urgency = urgency,
                    referredBy = "",
                    seenBy = emptyList(),
                    hasUnseenReport = false,
                    scheduledDate = scheduledDate
                )

                // ۱. Room.insert(TaskEntity)
                val taskEntity = TaskMapper.toEntity(newTask)
                taskLocalRepository.insertTaskDirect(taskEntity)

                // ۲. Room.insert(mapping)
                selectedWorkOrderIds.forEach { woId ->
                    val mapping = TaskWorkOrderMappingEntity(
                        id = "m_${newTaskId}_${woId}",
                        taskId = newTaskId,
                        workOrderId = woId,
                        createdAt = System.currentTimeMillis()
                    )
                    taskLocalRepository.insertMappingDirect(mapping)
                }

                // ۳. Outbox
                OutboxHelper.enqueueCreateTask(this@CreateTaskActivity, newTask, selectedWorkOrderIds.toList())
                ReportSyncWorker.enqueue(this@CreateTaskActivity)

                Toast.makeText(this@CreateTaskActivity, "کار با موفقیت ثبت شد", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                btnSubmit.isEnabled = true
                btnSubmit.text = "ثبت کار"
                Toast.makeText(this@CreateTaskActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun updateTaskOffline() {
        val title = etTitle.text.toString().trim()
        val description = etTaskDescription.text.toString().trim()
        val scheduledDate = etScheduledDate.text.toString().trim().takeIf { it.isNotEmpty() }
        val urgency = when (rgUrgency.checkedRadioButtonId) {
            R.id.rbUrgencyVeryHigh -> "خیلی زیاد"
            R.id.rbUrgencyHigh -> "زیاد"
            else -> "عادی"
        }
        val priority = sliderPriority.value.toInt()

        btnSubmit.isEnabled = false
        btnSubmit.text = "در حال ذخیره..."

        lifecycleScope.launch {
            try {
                val updatedTask = TaskModel(
                    id = taskId ?: "",
                    createDate = "",
                    title = title,
                    description = description,
                    creator = "",
                    status = "",
                    assignedTo = "",
                    responsible = "",
                    priority = priority,
                    urgency = urgency,
                    referredBy = "",
                    scheduledDate = scheduledDate
                )
                taskLocalRepository.updateTaskFromEdit(updatedTask)

                // آپدیت mappingها
                val db = AppDatabase.getInstance(this@CreateTaskActivity)
                db.taskWorkOrderMappingDao().deleteByTask(taskId ?: "")
                selectedWorkOrderIds.forEach { woId ->
                    val mapping = TaskWorkOrderMappingEntity(
                        id = "m_${taskId}_${woId}",
                        taskId = taskId ?: "",
                        workOrderId = woId,
                        createdAt = System.currentTimeMillis()
                    )
                    taskLocalRepository.insertMappingDirect(mapping)
                }

                OutboxHelper.enqueueUpdateTask(this@CreateTaskActivity, updatedTask)
                OutboxHelper.enqueueUpdateTaskWorkOrders(
                    this@CreateTaskActivity,
                    taskId ?: "",
                    selectedWorkOrderIds.toList()
                )
                ReportSyncWorker.enqueue(this@CreateTaskActivity)

                Toast.makeText(this@CreateTaskActivity, "تغییرات ذخیره شد", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                btnSubmit.isEnabled = true
                btnSubmit.text = "بروزرسانی کار"
                Toast.makeText(this@CreateTaskActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}