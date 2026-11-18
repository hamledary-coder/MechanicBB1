package com.workshop.mechanicbb1.ui.workorder

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.repository.WorkOrderLocalRepository
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.data.sync.WorkOrderSyncer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkOrderDetailActivity : AppCompatActivity() {

    private lateinit var tvWoNumber: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvClass: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvRequester: TextView
    private lateinit var tvApprover: TextView
    private lateinit var tvUnit: TextView
    private lateinit var tvSubUnit: TextView
    private lateinit var tvEquipment: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvStage: TextView

    private lateinit var layoutSubUnit: View
    private lateinit var layoutEquipment: View

    private lateinit var localRepository: WorkOrderLocalRepository
    private var workOrderId: String = ""
    private var currentWorkOrder: WorkOrderModel? = null
    private var workOrderJob: Job? = null

    // Launcher برای ویرایش
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // ✅ id همون (چون id عوض نمی‌شه)
            loadWorkOrder()
        }
    }

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
        setContentView(R.layout.workorder_activity_detail)

        localRepository = WorkOrderLocalRepository(this)
        workOrderId = intent.getStringExtra("WORK_ORDER_ID") ?: ""

        if (workOrderId.isEmpty()) {
            Toast.makeText(this, "شناسه درخواست یافت نشد", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupToolbar()
        loadWorkOrder()
    }

    private fun initViews() {
        tvWoNumber = findViewById(R.id.tvWoNumber)
        tvTitle = findViewById(R.id.tvTitle)
        tvClass = findViewById(R.id.tvClass)
        tvDate = findViewById(R.id.tvDate)
        tvRequester = findViewById(R.id.tvRequester)
        tvApprover = findViewById(R.id.tvApprover)
        tvUnit = findViewById(R.id.tvUnit)
        tvSubUnit = findViewById(R.id.tvSubUnit)
        tvEquipment = findViewById(R.id.tvEquipment)
        tvDescription = findViewById(R.id.tvDescription)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvStage = findViewById(R.id.tvStage)
        layoutSubUnit = findViewById(R.id.layoutSubUnit)
        layoutEquipment = findViewById(R.id.layoutEquipment)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "جزئیات درخواست"

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    // ============ بارگذاری (Offline-First) ============
    private fun loadWorkOrder() {
        workOrderJob?.cancel()
        workOrderJob = lifecycleScope.launch {
            localRepository.getWorkOrderById(workOrderId).collectLatest { wo ->
                if (wo == null) {
                    // تو Room نیست → از سرور sync کن
                    // ❌ کد قبلی: WorkOrderSyncer.syncSingleWorkOrder(...)
                    // ✅ کد جدید:
                    WorkOrderSyncer.singleSyncForWorker(this@WorkOrderDetailActivity, workOrderId) { ok ->
                        if (!ok) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@WorkOrderDetailActivity,
                                    "درخواست یافت نشد",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }
                    }
                } else {
                    currentWorkOrder = wo
                    bindWorkOrder(wo)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindWorkOrder(wo: WorkOrderModel) {
        // هدر
        tvWoNumber.text = if (wo.requestNumber.isNotEmpty()) {
            if (wo.requestClass != null) {
                "WO-${wo.requestClass}-${wo.requestNumber}"
            } else {
                "WO-${wo.requestNumber}"
            }
        } else {
            wo.id
        }

        tvTitle.text = wo.title.ifEmpty { "بدون عنوان" }

        // کلاس
        tvClass.text = wo.classText
        tvClass.setTextColor(wo.classColor)

        // تاریخ
        tvDate.text = wo.requestDate.ifEmpty { "—" }

        // صادرکننده
        tvRequester.text = wo.requester.ifEmpty { "—" }

        // تاییدکننده
        tvApprover.text = wo.approver.ifEmpty { "—" }


        // واحد
        tvUnit.text = wo.unit.ifEmpty { "—" }

        // ============ زیرواحد (مخفی اگه خالی) ============
        if (wo.subUnit.isNotEmpty() && wo.subUnit != "null") {
            layoutSubUnit.visibility = View.VISIBLE
            tvSubUnit.text = wo.subUnit
        } else {
            layoutSubUnit.visibility = View.GONE
        }

        // ============ تجهیز (مخفی اگه خالی) ============
        if (wo.equipmentName.isNotEmpty() && wo.equipmentName != "null") {
            layoutEquipment.visibility = View.VISIBLE
            tvEquipment.text = wo.equipmentName
        } else {
            layoutEquipment.visibility = View.GONE
        }

        // مرحله
        tvStage.text = "${wo.stage}. ${wo.stageText}"

        // پیشرفت
        tvProgress.text = wo.progressText
        tvProgress.setTextColor(wo.progressColor)

        // توضیحات
        tvDescription.text = wo.description.ifEmpty { "توضیحاتی ثبت نشده" }
    }

    // ============ منو ============
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        // فقط سرشیفت
        if (userRole == Config.RoleCode.SUPERVISOR) {
            menuInflater.inflate(R.menu.menu_work_order_detail, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                editWorkOrder()
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ============ ویرایش ============
    private fun editWorkOrder() {
        val intent = Intent(this, CreateWorkOrderActivity::class.java).apply {
            putExtra("IS_EDIT", true)
            putExtra("WORK_ORDER_ID", workOrderId)
        }
        editLauncher.launch(intent)
    }

    // ============ حذف ============
    private fun confirmDelete() {
        val wo = currentWorkOrder ?: return

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@WorkOrderDetailActivity)
                val mappings = db.taskWorkOrderMappingDao().getByWorkOrderOnce(wo.id)

                if (mappings.isNotEmpty()) {
                    val taskIds = mappings.map { it.taskId }.joinToString("، ")
                    AlertDialog.Builder(this@WorkOrderDetailActivity)
                        .setTitle("هشدار حذف")
                        .setMessage("این درخواست به کار(های) «$taskIds» متصل است.\n\nآیا از حذف درخواست و لینک‌های مرتبط مطمئن هستید؟")
                        .setPositiveButton("بله، حذف کن") { _, _ ->
                            deleteWorkOrderWithMappings(wo)
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@WorkOrderDetailActivity)
                        .setTitle("حذف درخواست")
                        .setMessage("آیا از حذف «${wo.id} - ${wo.title}» مطمئن هستید؟")
                        .setPositiveButton("حذف") { _, _ ->
                            deleteWorkOrder(wo)
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WorkOrderDetailActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteWorkOrder(wo: WorkOrderModel) {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: "0"

        lifecycleScope.launch {
            try {
                // ۱. حذف از Room
                localRepository.deleteWorkOrder(wo.id)

                // ۲. اضافه به Outbox
                OutboxHelper.enqueueDeleteWorkOrder(this@WorkOrderDetailActivity, wo.id)

                // ۳. trigger sync
                ReportSyncWorker.enqueue(this@WorkOrderDetailActivity)

                Toast.makeText(
                    this@WorkOrderDetailActivity,
                    "درخواست حذف شد",
                    Toast.LENGTH_SHORT
                ).show()

                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@WorkOrderDetailActivity,
                    "خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteWorkOrderWithMappings(wo: WorkOrderModel) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@WorkOrderDetailActivity)

                // ۱. حذف Mappingها (Room + Outbox)
                val mappings = db.taskWorkOrderMappingDao().getByWorkOrderOnce(wo.id)
                mappings.forEach { mapping ->
                    OutboxHelper.enqueueDeleteTaskWorkOrderMapping(this@WorkOrderDetailActivity, mapping.id)
                }
                db.taskWorkOrderMappingDao().deleteByWorkOrder(wo.id)

                // ۲. حذف WorkOrder (Room + Outbox)
                localRepository.deleteWorkOrder(wo.id)
                OutboxHelper.enqueueDeleteWorkOrder(this@WorkOrderDetailActivity, wo.id)
                ReportSyncWorker.enqueue(this@WorkOrderDetailActivity)

                Toast.makeText(this@WorkOrderDetailActivity, "درخواست و لینک‌ها حذف شد", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@WorkOrderDetailActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}