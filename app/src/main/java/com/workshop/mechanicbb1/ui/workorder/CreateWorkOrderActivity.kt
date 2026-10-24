package com.workshop.mechanicbb1.ui.workorder

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
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
import kotlinx.coroutines.launch
import java.util.Calendar

class CreateWorkOrderActivity : AppCompatActivity() {

    private lateinit var etRequestNumber: TextInputEditText
    private lateinit var etTitle: TextInputEditText
    private lateinit var spinnerClass: Spinner
    private lateinit var spinnerUnit: Spinner
    private lateinit var etSubUnit: TextInputEditText
    private lateinit var etRequestDate: TextInputEditText
    private lateinit var etRequester: TextInputEditText
    private lateinit var etEquipment: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnSubmit: com.google.android.material.button.MaterialButton
    private lateinit var progressBar: ProgressBar

    private lateinit var localRepository: WorkOrderLocalRepository

    private var isEdit = false
    private var editWorkOrderId: String = ""
    private var currentWorkOrder: WorkOrderModel? = null

    // کلاس‌ها (نمایش → مقدار دیتابیس)
    private val classes = listOf(
        ClassItem("بدون کلاس", ""),
        ClassItem("کلاس ۱۴ (غیرمترقبه)", "14"),
        ClassItem("کلاس ۱۲ (برنامه‌ای)", "12"),
        ClassItem("پیشگیرانه (۱۲-P)", "12P")
    )

    // واحدها (نمایش → مقدار دیتابیس)
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
        spinnerUnit = findViewById(R.id.spinnerUnit)
        etSubUnit = findViewById(R.id.etSubUnit)
        etRequestDate = findViewById(R.id.etRequestDate)
        etRequester = findViewById(R.id.etRequester)
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
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classes.map { it.displayName })
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerClass.adapter = classAdapter

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units.map { it.displayName })
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerUnit.adapter = unitAdapter
    }

    private fun setupDatePicker() {
        etRequestDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val dialog = DatePickerDialog(this, { _, y, m, d ->
            val cal = Calendar.getInstance().apply { set(y, m, d) }
            etRequestDate.setText(DateUtils.gregorianToShamsi(cal))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        dialog.show()
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
                // ۱. اول به Room بنویس
                localRepository.saveWorkOrder(workOrder.toEntity())

                // ۲. به Outbox اضافه کن
                OutboxHelper.enqueueCreateWorkOrder(this@CreateWorkOrderActivity, workOrder)

                // ۳. Worker رو فعال کن
                ReportSyncWorker.enqueue(this@CreateWorkOrderActivity)

                showLoading(false)
                Toast.makeText(
                    this@CreateWorkOrderActivity,
                    "درخواست ثبت شد",
                    Toast.LENGTH_SHORT
                ).show()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@CreateWorkOrderActivity,
                    "خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ============ ویرایش (Offline-First) ============
    private fun updateWorkOrder() {
        val workOrder = buildWorkOrderModel().copy(id = editWorkOrderId)

        showLoading(true)

        lifecycleScope.launch {
            try {
                // ۱. اول به Room بنویس
                localRepository.updateWorkOrder(workOrder.toEntity())

                // ۲. به Outbox اضافه کن
                OutboxHelper.enqueueUpdateWorkOrder(this@CreateWorkOrderActivity, workOrder)

                // ۳. Worker رو فعال کن
                ReportSyncWorker.enqueue(this@CreateWorkOrderActivity)

                showLoading(false)
                Toast.makeText(
                    this@CreateWorkOrderActivity,
                    "تغییرات ذخیره شد",
                    Toast.LENGTH_SHORT
                ).show()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@CreateWorkOrderActivity,
                    "خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildWorkOrderModel(): WorkOrderModel {
        val classPos = spinnerClass.selectedItemPosition
        val unitPos = spinnerUnit.selectedItemPosition

        // برای ایجاد، ID جدید بساز (WO-requestNumber)
        val requestNumber = etRequestNumber.text.toString().trim()
        val newId = if (isEdit) editWorkOrderId else "WO-$requestNumber"

        return WorkOrderModel(
            id = newId,
            requestNumber = requestNumber,
            title = etTitle.text.toString().trim(),
            requestClass = classes[classPos].dbValue.takeIf { it.isNotEmpty() },
            requestDate = etRequestDate.text.toString().trim(),
            requester = etRequester.text.toString().trim(),
            unit = units[unitPos].dbName,
            subUnit = etSubUnit.text.toString().trim(),
            equipmentName = etEquipment.text.toString().trim(),
            description = etDescription.text.toString().trim()
        )
    }

    // ============ بارگذاری برای ویرایش (از Room) ============
    private fun loadWorkOrderForEdit() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val wo = localRepository.getWorkOrderByIdOnce(editWorkOrderId)

                if (wo != null) {
                    currentWorkOrder = wo
                    fillForm(wo)
                } else {
                    Toast.makeText(
                        this@CreateWorkOrderActivity,
                        "درخواست یافت نشد",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@CreateWorkOrderActivity,
                    "خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun fillForm(wo: WorkOrderModel) {
        etRequestNumber.setText(wo.requestNumber)
        etRequestNumber.isEnabled = false   // شماره قابل ویرایش نیست

        etTitle.setText(wo.title)
        etSubUnit.setText(wo.subUnit)
        etRequestDate.setText(wo.requestDate)
        etRequester.setText(wo.requester)
        etEquipment.setText(wo.equipmentName)
        etDescription.setText(wo.description)

        // انتخاب کلاس
        val classPos = classes.indexOfFirst { it.dbValue == (wo.requestClass ?: "") }
        if (classPos >= 0) spinnerClass.setSelection(classPos)

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
    data class UnitItem(val displayName: String, val dbName: String)
}