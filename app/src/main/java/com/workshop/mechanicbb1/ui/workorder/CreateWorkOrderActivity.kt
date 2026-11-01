package com.workshop.mechanicbb1.ui.workorder

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.WorkOrderLocalRepository
import com.workshop.mechanicbb1.data.local.toEntity
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.utils.DateUtils
import ir.hamsaa.persiandatepicker.PersianDatePickerDialog
import ir.hamsaa.persiandatepicker.api.PersianPickerDate
import ir.hamsaa.persiandatepicker.api.PersianPickerListener
import kotlinx.coroutines.launch
import java.util.Locale

class CreateWorkOrderActivity : AppCompatActivity() {

    private lateinit var etRequestNumber: TextInputEditText
    private lateinit var etTitle: TextInputEditText
    private lateinit var spinnerClass: Spinner
    private lateinit var spinnerStatus: Spinner
    private lateinit var spinnerUnit: Spinner
    private lateinit var etSubUnit: TextInputEditText
    private lateinit var etRequestDate: TextInputEditText
    private lateinit var etRequester: TextInputEditText
    private lateinit var etApprover: TextInputEditText
    private lateinit var etEquipment: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnSubmit: com.google.android.material.button.MaterialButton
    private lateinit var progressBar: ProgressBar

    private lateinit var localRepository: WorkOrderLocalRepository

    private var isEdit = false
    private var editWorkOrderId: String = ""
    private var currentWorkOrder: WorkOrderModel? = null

    // ============ کلاس‌ها ============
    private val classes = listOf(
        ClassItem("بدون کلاس", ""),
        ClassItem("کلاس ۱۴ (غیرمترقبه)", "14"),
        ClassItem("کلاس ۱۲ (برنامه‌ای)", "12"),
        ClassItem("پیشگیرانه (۱۲-P)", "12P"),
        ClassItem("کلاس ۱۵ (اقدام اصلاحی)", "15")
    )

    // ============ مراحل (۱۰ مرحله شرکت) ============
    private val statuses = listOf(
        StatusItem("ایجاد فرم", "1"),
        StatusItem("تایید اپراتور", "2"),
        StatusItem("تایید رییس واحد", "3"),
        StatusItem("شماره‌گذاری برنامه‌ریز", "4"),
        StatusItem("تایید رییس گروه", "5"),
        StatusItem("ثبت اطلاعات تکمیلی", "6"),
        StatusItem("تایید رییس گروه (۲)", "7"),
        StatusItem("تایید نهایی اپراتور", "8"),
        StatusItem("تایید نهایی رییس واحد", "9"),
        StatusItem("بایگانی", "10")
    )

    // ============ واحدها ============
    private val units = listOf(
        UnitItem("بهره‌برداری", "بهره‌برداری"),
        UnitItem("نمکزدایی", "نمکزدایی"),
        UnitItem("گاز", "تقویت فشار گاز")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.workorder_activity_create)

        localRepository = WorkOrderLocalRepository(this)

        isEdit = intent.getBooleanExtra("IS_EDIT", false)
        editWorkOrderId = intent.getStringExtra("WORK_ORDER_ID") ?: ""

        initViews()
        setupToolbar()
        setupSpinners()
        setupDatePicker()
        setupButtons()

        if (isEdit) {
            loadWorkOrderForEdit()
        } else {
            etRequestDate.setText(DateUtils.gregorianToShamsi())
        }
    }

    private fun initViews() {
        etRequestNumber = findViewById(R.id.etRequestNumber)
        etTitle = findViewById(R.id.etTitle)
        spinnerClass = findViewById(R.id.spinnerClass)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        spinnerUnit = findViewById(R.id.spinnerUnit)
        etSubUnit = findViewById(R.id.etSubUnit)
        etRequestDate = findViewById(R.id.etRequestDate)
        etRequester = findViewById(R.id.etRequester)
        etApprover = findViewById(R.id.etApprover)
        etEquipment = findViewById(R.id.etEquipment)
        etDescription = findViewById(R.id.etDescription)
        btnSubmit = findViewById(R.id.btnSubmit)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isEdit) "ویرایش درخواست" else "درخواست جدید"
        btnSubmit.text = if (isEdit) "ذخیره تغییرات" else "ثبت درخواست"

        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSpinners() {
        // کلاس
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classes.map { it.displayName })
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerClass.adapter = classAdapter

        // مرحله (۱۰ مرحله)
        val statusAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statuses.map { "${it.dbValue}. ${it.displayName}" }
        )
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = statusAdapter

        // واحد
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units.map { it.displayName })
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerUnit.adapter = unitAdapter
    }

    private fun setupDatePicker() {
        etRequestDate.setOnClickListener { showDatePicker() }
    }

    // ============ تقویم فارسی ============
    private fun showDatePicker() {
        // اگه در حال ویرایش هستیم و currentWorkOrder داره، ازش استفاده کن
        val dateStr = if (isEdit && currentWorkOrder != null && currentWorkOrder!!.requestDate.isNotEmpty()) {
            currentWorkOrder!!.requestDate
        } else {
            etRequestDate.text.toString()
        }

        val currentDate = dateStr.split("/")
        val initYear = if (currentDate.size == 3) currentDate[0].toIntOrNull() ?: 1404 else 1404
        val initMonth = if (currentDate.size == 3) currentDate[1].toIntOrNull() ?: 1 else 1
        val initDay = if (currentDate.size == 3) currentDate[2].toIntOrNull() ?: 1 else 1

        PersianDatePickerDialog(this)
            .setPositiveButtonString("تأیید")
            .setNegativeButton("انصراف")
            .setTodayButton("امروز")
            .setTodayButtonVisible(true)
            .setMinYear(1400)
            .setMaxYear(PersianDatePickerDialog.THIS_YEAR + 1)
            .setInitDate(initYear, initMonth, initDay)   // ← بعد از min/max
            .setListener(object : PersianPickerListener {
                override fun onDateSelected(persianPickerDate: PersianPickerDate) {
                    val year = persianPickerDate.persianYear
                    val month = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianMonth)
                    val day = String.format(Locale.ENGLISH, "%02d", persianPickerDate.persianDay)
                    etRequestDate.setText("$year/$month/$day")
                }
                override fun onDismissed() {}
            })
            .show()
    }

    private fun setupButtons() {
        btnSubmit.setOnClickListener {
            if (validateForm()) {
                if (isEdit) updateWorkOrder() else createWorkOrder()
            }
        }
    }

    private fun validateForm(): Boolean {
        if (etRequestNumber.text.isNullOrBlank()) {
            Toast.makeText(this, "شماره درخواست الزامی است", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etTitle.text.isNullOrBlank()) {
            Toast.makeText(this, "عنوان الزامی است", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    // ============ ایجاد (Offline-First) ============
    private fun createWorkOrder() {
        val workOrder = buildWorkOrderModel()

        showLoading(true)

        lifecycleScope.launch {
            try {
                localRepository.saveWorkOrder(workOrder.toEntity())
                OutboxHelper.enqueueCreateWorkOrder(this@CreateWorkOrderActivity, workOrder)
                ReportSyncWorker.enqueue(this@CreateWorkOrderActivity)

                showLoading(false)
                Toast.makeText(this@CreateWorkOrderActivity, "درخواست ثبت شد", Toast.LENGTH_SHORT).show()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@CreateWorkOrderActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============ ویرایش (Offline-First) ============
    private fun updateWorkOrder() {
        val workOrder = buildWorkOrderModel().copy(id = editWorkOrderId)

        showLoading(true)

        lifecycleScope.launch {
            try {
                localRepository.updateWorkOrder(workOrder.toEntity())
                OutboxHelper.enqueueUpdateWorkOrder(this@CreateWorkOrderActivity, workOrder)
                ReportSyncWorker.enqueue(this@CreateWorkOrderActivity)

                showLoading(false)
                Toast.makeText(this@CreateWorkOrderActivity, "تغییرات ذخیره شد", Toast.LENGTH_SHORT).show()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@CreateWorkOrderActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildWorkOrderModel(): WorkOrderModel {
        val classPos = spinnerClass.selectedItemPosition
        val statusPos = spinnerStatus.selectedItemPosition
        val unitPos = spinnerUnit.selectedItemPosition

        val requestNumber = etRequestNumber.text.toString().trim()
        val newId = if (isEdit) editWorkOrderId else "WO-$requestNumber"

        val selectedStatus = statuses[statusPos]
        val isArchived = selectedStatus.dbValue == "10"

        return WorkOrderModel(
            id = newId,
            requestNumber = requestNumber,
            title = etTitle.text.toString().trim(),
            requestClass = classes[classPos].dbValue.takeIf { it.isNotEmpty() },
            requestDate = etRequestDate.text.toString().trim(),
            requester = etRequester.text.toString().trim(),
            approver = etApprover.text.toString().trim(),
            unit = units[unitPos].dbName,
            subUnit = etSubUnit.text.toString().trim(),
            equipmentName = etEquipment.text.toString().trim(),
            description = etDescription.text.toString().trim(),
            externalStatus = selectedStatus.dbValue,     // "1" تا "10"
            progress = if (isArchived) 100 else 0
        )
    }

    // ============ بارگذاری برای ویرایش ============
    private fun loadWorkOrderForEdit() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val wo = localRepository.getWorkOrderByIdOnce(editWorkOrderId)

                if (wo != null) {
                    currentWorkOrder = wo
                    fillForm(wo)
                } else {
                    Toast.makeText(this@CreateWorkOrderActivity, "درخواست یافت نشد", Toast.LENGTH_SHORT).show()
                    finish()
                }
                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@CreateWorkOrderActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun fillForm(wo: WorkOrderModel) {
        etRequestNumber.setText(wo.requestNumber)
        etRequestNumber.isEnabled = false

        etTitle.setText(wo.title)
        etSubUnit.setText(wo.subUnit)
        etRequestDate.setText(wo.requestDate)
        etApprover.setText(wo.approver)
        etRequester.setText(wo.requester)
        etEquipment.setText(wo.equipmentName)
        etDescription.setText(wo.description)

        // انتخاب کلاس
        val classPos = classes.indexOfFirst { it.dbValue == (wo.requestClass ?: "") }
        if (classPos >= 0) spinnerClass.setSelection(classPos)

        // انتخاب مرحله
        val statusPos = statuses.indexOfFirst { it.dbValue == wo.externalStatus }
        if (statusPos >= 0) spinnerStatus.setSelection(statusPos)

        // انتخاب واحد
        val unitPos = units.indexOfFirst { it.dbName == wo.unit }
        if (unitPos >= 0) spinnerUnit.setSelection(unitPos)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !show
        btnSubmit.alpha = if (show) 0.5f else 1.0f
    }

    // ============ Data Classes ============
    data class ClassItem(val displayName: String, val dbValue: String)
    data class StatusItem(val displayName: String, val dbValue: String)
    data class UnitItem(val displayName: String, val dbName: String)
}