package com.workshop.mechanicbb1.ui.task.create

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.local.entity.TaskWorkOrderMappingEntity
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.data.sync.TaskMapper
import com.workshop.mechanicbb1.ui.task.detail.viewholder.WorkOrderMiniAdapter
import com.workshop.mechanicbb1.ui.workorder.WorkOrderDetailActivity
import com.workshop.mechanicbb1.utils.DateUtils
import TaskModel
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import ir.hamsaa.persiandatepicker.PersianDatePickerDialog
import ir.hamsaa.persiandatepicker.api.PersianPickerDate
import ir.hamsaa.persiandatepicker.api.PersianPickerListener
import kotlinx.coroutines.launch
import java.util.Locale

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
    private lateinit var btnClearScheduledDate: ImageButton
    private lateinit var rgUrgency: RadioGroup
    private lateinit var sliderPriority: Slider
    private lateinit var tvPriorityValue: TextView
    private lateinit var tvWorkOrdersSummary: TextView
    private lateinit var btnPickWorkOrders: Button
    private lateinit var selectedWorkOrdersSection: LinearLayout
    private lateinit var tvSelectedWorkOrdersTitle: TextView
    private lateinit var rvSelectedWorkOrders: RecyclerView

    // انتخاب WorkOrderها
    private val selectedWorkOrderIds = mutableSetOf<String>()
    private val selectedWorkOrders = mutableListOf<WorkOrderModel>()
    private var availableWorkOrders: List<WorkOrderModel> = emptyList()
    private lateinit var selectedWorkOrdersAdapter: WorkOrderMiniAdapter

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

        // ✅ بارگذاری WorkOrderها، بعدش Edit Data (اگه هست)
        loadWorkOrders {
            loadEditDataIfNeeded()
        }

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
        selectedWorkOrdersSection = findViewById(R.id.selectedWorkOrdersSection)
        tvSelectedWorkOrdersTitle = findViewById(R.id.tvSelectedWorkOrdersTitle)
        rvSelectedWorkOrders = findViewById(R.id.rvSelectedWorkOrders)
        btnPickWorkOrders.isEnabled = false
        btnClearScheduledDate = findViewById(R.id.btnClearScheduledDate)

        // ✅ آداپتر WorkOrderهای انتخاب‌شده
        rvSelectedWorkOrders.layoutManager = LinearLayoutManager(this)
        selectedWorkOrdersAdapter = WorkOrderMiniAdapter(
            items = emptyList(),
            onClick = { wo -> openWorkOrderDetail(wo) },
            showRemoveButton = true,
            onRemove = { wo -> removeWorkOrder(wo) }
        )
        rvSelectedWorkOrders.adapter = selectedWorkOrdersAdapter

        sliderPriority.addOnChangeListener { _, value, _ ->
            tvPriorityValue.text = value.toInt().toString()
        }
    }

    private fun setupDatePicker() {
        val btnCalendar = findViewById<ImageButton>(R.id.btnScheduledDatePicker)
        btnCalendar.setOnClickListener { showPersianDatePicker() }
        etScheduledDate.setOnClickListener { showPersianDatePicker() }

        btnClearScheduledDate.setOnClickListener {
            etScheduledDate.setText("")
            updateClearDateButtonVisibility()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showPersianDatePicker() {
        val dateText = etScheduledDate.text.toString().trim()

        val dialog = PersianDatePickerDialog(this)
            .setPositiveButtonString("تأیید")
            .setNegativeButton("انصراف")
            .setTodayButton("امروز")
            .setTodayButtonVisible(true)
            .setMinYear(1400)
            .setMaxYear(PersianDatePickerDialog.THIS_YEAR)
            .setListener(object : PersianPickerListener {
                override fun onDateSelected(persianPickerDate: PersianPickerDate) {
                    val year = persianPickerDate.persianYear
                    val month = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianMonth)
                    val day = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianDay)
                    etScheduledDate.setText("$year/$month/$day")
                    updateClearDateButtonVisibility()
                }
                override fun onDismissed() { }
            })

        // ✅ اگه فیلد تاریخ داره (ویرایش) → همون تاریخ رو نشون بده
        // ✅ اگه فیلد خالیه (ثبت جدید) → امروز رو نشون بده
        if (dateText.isNotEmpty()) {
            val parts = dateText.split("/")
            val initYear = parts.getOrNull(0)?.toIntOrNull()
            val initMonth = parts.getOrNull(1)?.toIntOrNull()
            val initDay = parts.getOrNull(2)?.toIntOrNull()

            if (initYear != null && initMonth != null && initDay != null) {
                dialog.setInitDate(initYear, initMonth, initDay)
            }
        }
        // اگه خالیه، setInitDate صدا زده نمی‌شه → پیش‌فرض کتابخانه (امروز) استفاده می‌شه

        dialog.show()
    }

    private fun updateClearDateButtonVisibility() {
        val hasDate = etScheduledDate.text.toString().isNotBlank()
        btnClearScheduledDate.visibility = if (hasDate) View.VISIBLE else View.GONE
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

                    // ✅ selectedWorkOrders رو از availableWorkOrders بازسازی کن
                    selectedWorkOrders.clear()
                    newIds.forEach { id ->
                        availableWorkOrders.find { it.id == id }?.let { selectedWorkOrders.add(it) }
                    }

                    updateWorkOrdersUI()
                }
            ).show()
        }
    }

    /**
     * بارگذاری WorkOrderهای باز
     * @param onLoaded callback بعد از اتمام (برای loadEditDataIfNeeded)
     */
    private fun loadWorkOrders(onLoaded: (() -> Unit)? = null) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@CreateTaskActivity)
                val entities = db.workOrderDao().getOpenForPicker()
                availableWorkOrders = entities.map { entity ->
                    WorkOrderModel(
                        id = entity.id,
                        requestNumber = entity.requestNumber ?: "",
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
                btnPickWorkOrders.isEnabled = true
            } catch (e: Exception) {
                btnPickWorkOrders.isEnabled = true
            } finally {
                onLoaded?.invoke()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateWorkOrdersUI() {
        // خلاصه
        tvWorkOrdersSummary.text = if (selectedWorkOrderIds.isEmpty()) {
            "هیچ درخواستی انتخاب نشده"
        } else {
            "${selectedWorkOrderIds.size} درخواست انتخاب شده"
        }

        // کارت‌ها
        if (selectedWorkOrders.isEmpty()) {
            selectedWorkOrdersSection.visibility = View.GONE
        } else {
            selectedWorkOrdersSection.visibility = View.VISIBLE
            tvSelectedWorkOrdersTitle.text = "🔗 درخواست‌های انتخاب‌شده (${selectedWorkOrders.size})"
            selectedWorkOrdersAdapter.updateList(selectedWorkOrders)
        }
    }

    private fun removeWorkOrder(wo: WorkOrderModel) {
        selectedWorkOrderIds.remove(wo.id)
        selectedWorkOrders.removeAll { it.id == wo.id }
        updateWorkOrdersUI()
    }

    private fun openWorkOrderDetail(wo: WorkOrderModel) {
        val intent = Intent(this, WorkOrderDetailActivity::class.java).apply {
            putExtra("WORK_ORDER_ID", wo.id)
        }
        startActivity(intent)
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
            updateClearDateButtonVisibility()

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

            // بارگذاری WorkOrderهای فعلی (بعد از loadWorkOrders)
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

                // ✅ selectedWorkOrders رو از availableWorkOrders پر کن
                selectedWorkOrders.clear()
                selectedWorkOrderIds.forEach { id ->
                    availableWorkOrders.find { it.id == id }?.let { selectedWorkOrders.add(it) }
                }

                updateWorkOrdersUI()
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