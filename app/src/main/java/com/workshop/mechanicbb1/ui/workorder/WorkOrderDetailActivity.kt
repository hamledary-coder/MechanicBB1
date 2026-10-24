package com.workshop.mechanicbb1.ui.workorder

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
import androidx.lifecycle.lifecycleScope
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.WorkOrderLocalRepository
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
    private lateinit var tvUnit: TextView
    private lateinit var tvSubUnit: TextView
    private lateinit var tvEquipment: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var localRepository: WorkOrderLocalRepository
    private var workOrderId: String = ""
    private var currentWorkOrder: WorkOrderModel? = null
    private var workOrderJob: Job? = null

    // Launcher برای ویرایش
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Room خودکار آپدیت می‌شه (Flow)
            // ولی برای اطمینان، sync کن
            WorkOrderSyncer.syncSingleWorkOrder(this, workOrderId) { ok ->
                Log.d("WorkOrderDetail", "Sync after edit: $ok")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        tvUnit = findViewById(R.id.tvUnit)
        tvSubUnit = findViewById(R.id.tvSubUnit)
        tvEquipment = findViewById(R.id.tvEquipment)
        tvDescription = findViewById(R.id.tvDescription)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
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
        // ۱. اول از Room بخون (فوری)
        workOrderJob?.cancel()
        workOrderJob = lifecycleScope.launch {
            localRepository.getWorkOrderById(workOrderId).collectLatest { wo ->
                if (wo == null) {
                    // تو Room نیست → از سرور sync کن
                    WorkOrderSyncer.syncSingleWorkOrder(this@WorkOrderDetailActivity, workOrderId) { ok ->
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

    private fun bindWorkOrder(wo: WorkOrderModel) {
        tvWoNumber.text = wo.id
        tvTitle.text = wo.title.ifEmpty { "بدون عنوان" }
        tvClass.text = wo.classText
        tvClass.setTextColor(wo.classColor)
        tvDate.text = wo.requestDate.ifEmpty { "—" }
        tvRequester.text = wo.requester.ifEmpty { "—" }
        tvUnit.text = wo.unit.ifEmpty { "—" }
        tvSubUnit.text = wo.subUnit.ifEmpty { "—" }
        tvEquipment.text = wo.equipmentName.ifEmpty { "—" }
        tvDescription.text = wo.description.ifEmpty { "توضیحاتی ثبت نشده" }

        tvProgress.text = wo.progressText
        tvProgress.setTextColor(wo.progressColor)
    }

    // ============ منو ============
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_work_order_detail, menu)
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

        AlertDialog.Builder(this)
            .setTitle("حذف درخواست")
            .setMessage("آیا از حذف «${wo.id} - ${wo.title}» مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ ->
                deleteWorkOrder(wo)
            }
            .setNegativeButton("انصراف", null)
            .show()
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
}