package com.workshop.mechanicbb1.ui.task.detail

import TaskModel
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.entity.TaskEstimateEntity
import com.workshop.mechanicbb1.data.local.repository.ReportLocalRepository
import com.workshop.mechanicbb1.data.local.repository.TaskEstimateLocalRepository
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.data.sync.TaskSyncer
import com.workshop.mechanicbb1.ui.cartable.CartableActivity
import com.workshop.mechanicbb1.ui.referral.ReferDialog
import com.workshop.mechanicbb1.ui.task.complete.CompleteTaskActivity
import com.workshop.mechanicbb1.ui.task.create.CreateTaskActivity
import com.workshop.mechanicbb1.ui.task.detail.util.TaskDetailUiUtils
import com.workshop.mechanicbb1.ui.task.detail.viewholder.WorkOrderMiniAdapter
import com.workshop.mechanicbb1.ui.task.estimate.EstimateActivity
import com.workshop.mechanicbb1.ui.task.log.AddLogActivity
import com.workshop.mechanicbb1.ui.task.repository.TaskRepository
import com.workshop.mechanicbb1.ui.workorder.WorkOrderDetailActivity
import com.workshop.mechanicbb1.utils.ActivityLogger
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.SeenItem
import com.workshop.mechanicbb1.utils.SeenManager
import com.workshop.mechanicbb1.utils.UserCache
import com.workshop.mechanicbb1.utils.VolleySingleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Suppress("DEPRECATION")
class TaskDetailActivity : AppCompatActivity() {

    // Views — Hero
    private lateinit var tvTaskId: TextView
    private lateinit var tvTaskTitle: TextView
    private lateinit var tvHeroStatus: TextView
    private lateinit var tvHeroUrgency: TextView
    private lateinit var tvHeroPriority: TextView

    // Quick Info
    private lateinit var rowScheduledDate: LinearLayout
    private lateinit var dividerScheduledDate: View
    private lateinit var tvScheduledDate: TextView
    private lateinit var rowResponsible: LinearLayout
    private lateinit var dividerResponsible: View
    private lateinit var tvResponsible: TextView
    private lateinit var btnReferTask: ImageButton
    private lateinit var rowAssignees: LinearLayout
    private lateinit var dividerAssignees: View
    private lateinit var tvAssignees: TextView
    private lateinit var rowReferredBy: LinearLayout
    private lateinit var tvReferredBy: TextView

    // Status
    private lateinit var cardStatus: MaterialCardView
    private lateinit var sliderStatus: Slider
    private lateinit var tvStatusLabel: TextView
    private lateinit var cbAwaitingReport: MaterialCheckBox
    private lateinit var layoutStatusLabels: LinearLayout

    // Description
    private lateinit var cardDescription: MaterialCardView
    private lateinit var tvTaskDescription: TextView

    // WorkOrders
    private lateinit var cardWorkOrders: MaterialCardView
    private lateinit var headerWorkOrders: LinearLayout
    private lateinit var tvWorkOrdersTitle: TextView
    private lateinit var ivExpandWorkOrders: ImageView
    private lateinit var rvWorkOrders: RecyclerView

    // Estimate
    private lateinit var headerEstimate: LinearLayout
    private lateinit var tvEstimateStatus: TextView
    private lateinit var ivExpandEstimate: ImageView
    private lateinit var contentEstimate: LinearLayout
    private lateinit var layoutEstimateEmpty: LinearLayout
    private lateinit var btnAddEstimateEmpty: Button
    private lateinit var layoutEstimateData: LinearLayout
    private lateinit var tvEstimateManpower: TextView
    private lateinit var tvEstimateParts: TextView
    private lateinit var tvEstimateTotalHours: TextView
    private lateinit var tvEstimateManHours: TextView
    private lateinit var tvEstimateWorkType: TextView
    private lateinit var tvEstimatePhysical: TextView
    private lateinit var tvEstimateConditions: TextView
    private lateinit var tvEstimateNotes: TextView
    private lateinit var tvEstimateMeta: TextView
    private lateinit var btnAddEstimate: Button
    private lateinit var btnDeleteEstimate: Button

    // SeenBy
    private lateinit var cardSeenBy: MaterialCardView
    private lateinit var seenByContainer: LinearLayout

    // AddLog
    // AddLog
    private lateinit var bottomBar: View           // ← جدید
    private lateinit var btnAddLog: Button         // ← تغییر نوع (Button نه ExtendedFAB)
    // Logs
    private lateinit var logsHeader: LinearLayout
    private lateinit var tvLogsTitle: TextView
    private lateinit var rvLogs: RecyclerView
    private lateinit var tvEmptyLogs: TextView
    private lateinit var progressBar: View

    // Data
    private lateinit var task: TaskModel
    private val logsList = mutableListOf<TaskLogModel>()
    private val workOrdersList = mutableListOf<WorkOrderModel>()
    private var latestEstimate: TaskEstimateEntity? = null

    private lateinit var userRole: String
    private lateinit var taskLocalRepository: TaskLocalRepository
    private lateinit var reportLocalRepository: ReportLocalRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskEstimateRepository: TaskEstimateLocalRepository
    private lateinit var taskLogAdapter: TaskLogAdapter

    private var taskJob: Job? = null
    private var logsJob: Job? = null
    private var workOrdersJob: Job? = null
    private var estimateJob: Job? = null

    private var isEstimateExpanded = false
    private var isWorkOrdersExpanded = true

    private val estimateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    companion object {
        private const val STATUS_IN_PROGRESS = "2"
        private const val STATUS_BLOCKED = "3"
        private const val STATUS_COMPLETE_REQUEST = "4"
        private const val STATUS_SENT_TO_SUPERVISOR = "41"
        private const val STATUS_ARCHIVED = "5"
    }

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
        setContentView(R.layout.taskdetail_activity)

        if (UserCache.getSize() == 0) {
            showLoading(true)
            UserCache.loadAllUsers {
                runOnUiThread {
                    showLoading(false)
                    setupToolbar()
                    initViews()
                    loadTaskData()
                }
            }
        } else {
            setupToolbar()
            initViews()
            loadTaskData()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::task.isInitialized) {
            fetchFullTaskFromServer()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // ============================================================
    // Toolbar Menu
    // ============================================================
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_task_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val canManageTask = userRole == Config.RoleCode.MANAGER || userRole == Config.RoleCode.SUPERVISOR
        val isArchived = ::task.isInitialized && task.status == STATUS_ARCHIVED

        menu.findItem(R.id.action_edit)?.isVisible = canManageTask && !isArchived
        menu.findItem(R.id.action_delete)?.isVisible = canManageTask && !isArchived
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> { editTask(); true }
            R.id.action_delete -> { deleteTask(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ============================================================
    // Setup
    // ============================================================
    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "جزئیات کار"
        }
        toolbar.navigationIcon?.setTint(Color.WHITE)
    }

    private fun initViews() {
        // Hero
        tvTaskId = findViewById(R.id.tvTaskId)
        tvTaskTitle = findViewById(R.id.tvTaskTitle)
        tvHeroStatus = findViewById(R.id.tvHeroStatus)
        tvHeroUrgency = findViewById(R.id.tvHeroUrgency)
        tvHeroPriority = findViewById(R.id.tvHeroPriority)

        // Quick Info
        rowScheduledDate = findViewById(R.id.rowScheduledDate)
        dividerScheduledDate = findViewById(R.id.dividerScheduledDate)
        tvScheduledDate = findViewById(R.id.tvScheduledDate)
        rowResponsible = findViewById(R.id.rowResponsible)
        dividerResponsible = findViewById(R.id.dividerResponsible)
        tvResponsible = findViewById(R.id.tvResponsible)
        btnReferTask = findViewById(R.id.btnReferTask)
        rowAssignees = findViewById(R.id.rowAssignees)
        dividerAssignees = findViewById(R.id.dividerAssignees)
        tvAssignees = findViewById(R.id.tvAssignees)
        rowReferredBy = findViewById(R.id.rowReferredBy)
        tvReferredBy = findViewById(R.id.tvReferredBy)

        // Status
        cardStatus = findViewById(R.id.cardStatus)
        sliderStatus = findViewById(R.id.sliderStatus)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        cbAwaitingReport = findViewById(R.id.cbAwaitingReport)
        layoutStatusLabels = findViewById(R.id.layoutStatusLabels)

        // Description
        cardDescription = findViewById(R.id.cardDescription)
        tvTaskDescription = findViewById(R.id.tvTaskDescription)

        // WorkOrders
        cardWorkOrders = findViewById(R.id.cardWorkOrders)
        headerWorkOrders = findViewById(R.id.headerWorkOrders)
        tvWorkOrdersTitle = findViewById(R.id.tvWorkOrdersTitle)
        ivExpandWorkOrders = findViewById(R.id.ivExpandWorkOrders)
        rvWorkOrders = findViewById(R.id.rvWorkOrders)

        // Estimate
        headerEstimate = findViewById(R.id.headerEstimate)
        tvEstimateStatus = findViewById(R.id.tvEstimateStatus)
        ivExpandEstimate = findViewById(R.id.ivExpandEstimate)
        contentEstimate = findViewById(R.id.contentEstimate)
        layoutEstimateEmpty = findViewById(R.id.layoutEstimateEmpty)
        btnAddEstimateEmpty = findViewById(R.id.btnAddEstimateEmpty)
        layoutEstimateData = findViewById(R.id.layoutEstimateData)
        tvEstimateManpower = findViewById(R.id.tvEstimateManpower)
        tvEstimateParts = findViewById(R.id.tvEstimateParts)
        tvEstimateTotalHours = findViewById(R.id.tvEstimateTotalHours)
        tvEstimateManHours = findViewById(R.id.tvEstimateManHours)
        tvEstimateWorkType = findViewById(R.id.tvEstimateWorkType)
        tvEstimatePhysical = findViewById(R.id.tvEstimatePhysical)
        tvEstimateConditions = findViewById(R.id.tvEstimateConditions)
        tvEstimateNotes = findViewById(R.id.tvEstimateNotes)
        tvEstimateMeta = findViewById(R.id.tvEstimateMeta)
        btnAddEstimate = findViewById(R.id.btnAddEstimate)
        btnDeleteEstimate = findViewById(R.id.btnDeleteEstimate)

        // SeenBy
        cardSeenBy = findViewById(R.id.cardSeenBy)
        seenByContainer = findViewById(R.id.seenByContainer)

        // AddLog
        bottomBar = findViewById(R.id.bottomBar)
        btnAddLog = findViewById(R.id.btnAddLog)

        // Logs
        logsHeader = findViewById(R.id.logsHeader)
        tvLogsTitle = findViewById(R.id.tvLogsTitle)
        rvLogs = findViewById(R.id.rvLogs)
        tvEmptyLogs = findViewById(R.id.tvEmptyLogs)
        progressBar = findViewById(R.id.progressBar)

        // Adapter
        rvLogs.layoutManager = LinearLayoutManager(this)
        taskLogAdapter = TaskLogAdapter(
            task = TaskModel(
                id = "", createDate = "", title = "", description = "",
                creator = "", status = "", assignedTo = "", responsible = "",
                priority = 50, urgency = "", referredBy = ""
            ),
            logs = emptyList(),
            onDeleteLogClick = { log -> deleteLog(log) },
            onRefreshLogs = { loadTaskLogs() }
        )
        rvLogs.adapter = taskLogAdapter

        // Repos
        taskLocalRepository = TaskLocalRepository(this)
        reportLocalRepository = ReportLocalRepository(this)
        taskRepository = TaskRepository(this)
        taskEstimateRepository = TaskEstimateLocalRepository(this)

        // Listeners
        setupCollapsibleListeners()
        setupActionListeners()
    }

    private fun setupCollapsibleListeners() {
        headerWorkOrders.setOnClickListener {
            isWorkOrdersExpanded = !isWorkOrdersExpanded
            rvWorkOrders.visibility = if (isWorkOrdersExpanded) View.VISIBLE else View.GONE
            ivExpandWorkOrders.rotation = if (isWorkOrdersExpanded) 180f else 0f
        }

        headerEstimate.setOnClickListener {
            isEstimateExpanded = !isEstimateExpanded
            contentEstimate.visibility = if (isEstimateExpanded) View.VISIBLE else View.GONE
            ivExpandEstimate.rotation = if (isEstimateExpanded) 180f else 0f
        }
    }

    private fun setupActionListeners() {
        btnReferTask.setOnClickListener { referTask() }
        btnAddEstimateEmpty.setOnClickListener { openEstimateActivity() }
        btnAddEstimate.setOnClickListener { openEstimateActivity() }
        btnDeleteEstimate.setOnClickListener {
            latestEstimate?.let { deleteEstimate(it.id) }
        }
    }

    private fun showLoading(show: Boolean) {
        if (::progressBar.isInitialized) {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    // ============================================================
    // Load Data
    // ============================================================
    private fun loadTaskData() {
        task = TaskModel(
            id = intent.getStringExtra("TASK_ID") ?: "",
            createDate = intent.getStringExtra("DATE") ?: "",
            title = intent.getStringExtra("TITLE") ?: "",
            description = intent.getStringExtra("DESC") ?: "",
            creator = intent.getStringExtra("CREATOR") ?: "",
            status = intent.getStringExtra("STATUS") ?: "1",
            assignedTo = intent.getStringExtra("ASSIGNED_TO") ?: "",
            responsible = intent.getStringExtra("RESPONSIBLE") ?: "",
            priority = intent.getIntExtra("PRIORITY", 50),
            urgency = intent.getStringExtra("URGENCY") ?: "",
            referredBy = intent.getStringExtra("REFERRED_BY") ?: "",
            scheduledDate = intent.getStringExtra("SCHEDULED_DATE")?.takeIf { it.isNotEmpty() }
        )
        fetchFullTaskFromServer()
    }

    private fun fetchFullTaskFromServer() {
        taskJob?.cancel()
        taskJob = lifecycleScope.launch {
            taskLocalRepository.getTaskById(task.id).collectLatest { roomTask ->
                if (roomTask != null) {
                    val currentSeenBy = task.seenBy
                    task = roomTask.copy(seenBy = currentSeenBy)
                    bindTask()
                    loadTaskLogs()
                    loadWorkOrders()
                    loadLatestEstimate()
                }
            }
        }

        val url = "${Config.BASE_URL}?action=getTask&taskId=${task.id}"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val seenByArray = response.optJSONArray("seenBy") ?: JSONArray()
                    val seenBy = (0 until seenByArray.length()).map { seenByArray.getString(it) }
                    val hasUnseenReport = response.optBoolean("hasUnseenReport", false)
                    val status = response.optString("status", task.status)

                    task = task.copy(
                        status = status,
                        seenBy = seenBy,
                        hasUnseenReport = hasUnseenReport
                    )

                    val currentUserId = getCurrentUserId()
                    val alreadySeen = task.seenBy.contains(currentUserId)
                    if (currentUserId.isNotEmpty() && !alreadySeen) {
                        SeenManager.markAsSeen(this, currentUserId, listOf(SeenItem("TASK", task.id)))
                    }

                    bindTask()
                    loadTaskLogs()
                    loadWorkOrders()
                    loadLatestEstimate()

                } catch (e: Exception) {
                    Log.e("TaskDetail", "Error parsing task: ${e.message}")
                    bindTask()
                    loadTaskLogs()
                    loadWorkOrders()
                    loadLatestEstimate()
                }
            },
            { error ->
                Log.e("TaskDetail", "Error fetching task: ${error.message}")
                bindTask()
                loadTaskLogs()
                loadWorkOrders()
                loadLatestEstimate()
            }
        )
        VolleySingleton.getInstance(this).add(request)
    }

    private fun loadTaskLogs() {
        logsJob?.cancel()
        logsJob = lifecycleScope.launch {
            reportLocalRepository.getReportsForTask(task.id).collectLatest { logs ->
                logsList.clear()
                logsList.addAll(logs)
                updateLogsUI()
            }
        }
    }

    private fun loadWorkOrders() {
        workOrdersJob?.cancel()
        workOrdersJob = lifecycleScope.launch {
            taskLocalRepository.getWorkOrdersForTask(task.id).collectLatest { list ->
                workOrdersList.clear()
                workOrdersList.addAll(list)
                bindWorkOrders()
            }
        }
    }

    private fun loadLatestEstimate() {
        if (!::task.isInitialized) return
        estimateJob?.cancel()
        estimateJob = lifecycleScope.launch {
            taskEstimateRepository.getLatestForTaskFlow(task.id).collectLatest { estimate ->
                latestEstimate = estimate
                bindEstimate()
            }
        }
    }

    // ============================================================
    // Bind Task
    // ============================================================
    @SuppressLint("SetTextI18n")
    private fun bindTask() {
        if (!::task.isInitialized) return

        val currentUserId = getCurrentUserId()
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        // Hero
        tvTaskId.text = "#${task.id}"
        tvTaskTitle.text = task.title
        tvHeroStatus.text = Config.StatusCode.getText(task.status)
        updateHeroStatusColor(task.status)

        if (task.urgency.isNotEmpty() && task.urgency != "عادی") {
            tvHeroUrgency.text = "⚡ ${task.urgency}"
            tvHeroUrgency.visibility = View.VISIBLE
        } else {
            tvHeroUrgency.visibility = View.GONE
        }

        if (task.priority != 50) {
            tvHeroPriority.text = "🎯 ${task.priority}"
            tvHeroPriority.visibility = View.VISIBLE
        } else {
            tvHeroPriority.visibility = View.GONE
        }

        bindScheduledDate()

        if (task.responsible.isNotEmpty() && task.responsible != "0") {
            tvResponsible.text = UserCache.getName(task.responsible)
            rowResponsible.visibility = View.VISIBLE
            dividerResponsible.visibility = View.VISIBLE
        } else {
            rowResponsible.visibility = View.GONE
            dividerResponsible.visibility = View.GONE
        }

        bindAssignees()

        if (task.referredBy.isNotEmpty()) {
            tvReferredBy.text = UserCache.getName(task.referredBy)
            rowReferredBy.visibility = View.VISIBLE
        } else {
            rowReferredBy.visibility = View.GONE
        }

        if (task.description.isNotEmpty()) {
            tvTaskDescription.text = task.description
            cardDescription.visibility = View.VISIBLE
        } else {
            cardDescription.visibility = View.GONE
        }

        // Refer button (فقط سرشیفت/مدیر)
        val canManageTask = userRole == Config.RoleCode.MANAGER || userRole == Config.RoleCode.SUPERVISOR
        btnReferTask.visibility = if (canManageTask && task.status != STATUS_ARCHIVED) View.VISIBLE else View.GONE

        setupStatusSection(currentUserId)
        displaySeenBy(task.seenBy)
        setupAddLogButton(currentUserId)

        // Toolbar menu (Edit/Delete visibility)
        invalidateOptionsMenu()

        // TaskLogAdapter (آپدیت task)
        taskLogAdapter = TaskLogAdapter(
            task = task,
            logs = logsList,
            onDeleteLogClick = { log -> deleteLog(log) },
            onRefreshLogs = { loadTaskLogs() }
        )
        rvLogs.adapter = taskLogAdapter
        updateLogsUI()
    }

    private fun updateHeroStatusColor(status: String) {
        val bgColor = when (status) {
            Config.StatusCode.IN_PROGRESS -> "#1E88E5"
            Config.StatusCode.BLOCKED -> "#E53935"
            Config.StatusCode.AWAITING_REPORT_PROGRESS -> "#FF9800"  // نارنجی
            Config.StatusCode.AWAITING_REPORT_BLOCKED -> "#E65100"   // نارنجی تیره
            Config.StatusCode.SENT_TO_SUPERVISOR -> "#43A047"
            Config.StatusCode.ARCHIVED -> "#757575"
            else -> "#757575"
        }
        tvHeroStatus.background.setTint(Color.parseColor(bgColor))
    }

    private fun bindScheduledDate() {
        val scheduledDate = task.scheduledDate
        if (!scheduledDate.isNullOrEmpty()) {
            val today = DateUtils.gregorianToShamsi()
            val dayName = DateUtils.getDayNameFromDate(scheduledDate)
            val dateDisplay = if (dayName.isNotEmpty()) {
                "$scheduledDate ($dayName)"
            } else {
                scheduledDate
            }
            val color = when {
                scheduledDate == today -> "#2E7D32"
                scheduledDate < today -> "#FF9800"
                else -> "#2196F3"
            }
            tvScheduledDate.text = dateDisplay
            tvScheduledDate.setTextColor(Color.parseColor(color))
            rowScheduledDate.visibility = View.VISIBLE
            dividerScheduledDate.visibility = View.VISIBLE
        } else {
            rowScheduledDate.visibility = View.GONE
            dividerScheduledDate.visibility = View.GONE
        }
    }

    private fun bindAssignees() {
        if (task.assignedTo.isNotEmpty() && task.assignedTo != "0") {
            val assigneeNames = task.assignedTo.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "0" }
                .map { UserCache.getName(it) }
            if (assigneeNames.isNotEmpty()) {
                tvAssignees.text = assigneeNames.joinToString("، ")
                rowAssignees.visibility = View.VISIBLE
                dividerAssignees.visibility = View.VISIBLE
            } else {
                rowAssignees.visibility = View.GONE
                dividerAssignees.visibility = View.GONE
            }
        } else {
            rowAssignees.visibility = View.GONE
            dividerAssignees.visibility = View.GONE
        }
    }

    // ============================================================
    // Status Slider
    // ============================================================
    private fun setupStatusSection(currentUserId: String) {
        val status = task.status
        val isArchived = status == STATUS_ARCHIVED
        val isQueued = status == Config.StatusCode.NOT_ACTIONED
        val isSentToSupervisor = status == STATUS_SENT_TO_SUPERVISOR
        val isAwaitingReport = Config.StatusCode.isAwaitingReport(status)

        val canChangeStatus = (task.responsible == currentUserId) ||
                (userRole == Config.RoleCode.SUPERVISOR) ||
                (userRole == Config.RoleCode.MANAGER)

        val isSupervisor = userRole == Config.RoleCode.SUPERVISOR

        if (isArchived || isQueued) {
            cardStatus.visibility = View.GONE
            return
        }

        cardStatus.visibility = View.VISIBLE

        // ============ چک‌باکس «منتظر گزارش» ============
        // فقط سرشیفت + فقط در وضعیت‌های 2، 3، 21، 31 قابل مشاهده است
        val showCheckbox = isSupervisor &&
                (status == Config.StatusCode.IN_PROGRESS ||
                        status == Config.StatusCode.BLOCKED ||
                        isAwaitingReport)

        if (showCheckbox) {
            cbAwaitingReport.visibility = View.VISIBLE
            // ست کردن مقدار بدون trigger کردن listener
            cbAwaitingReport.setOnCheckedChangeListener(null)
            cbAwaitingReport.isChecked = isAwaitingReport
            cbAwaitingReport.isEnabled = !isSentToSupervisor
            cbAwaitingReport.setOnCheckedChangeListener { _, checked ->
                handleAwaitingReportToggle(checked)
            }
        } else {
            cbAwaitingReport.visibility = View.GONE
            cbAwaitingReport.setOnCheckedChangeListener(null)
        }

        // ============ اسلایدر وضعیت ============
        // در حالت «منتظر گزارش» اسلایدر مخفی می‌شود
        if (isAwaitingReport) {
            sliderStatus.visibility = View.GONE
            layoutStatusLabels.visibility = View.GONE
            tvStatusLabel.visibility = View.VISIBLE
            updateStatusUI(status)
            return
        }

        sliderStatus.visibility = View.VISIBLE
        layoutStatusLabels.visibility = View.VISIBLE

        sliderStatus.isEnabled = canChangeStatus
        sliderStatus.alpha = if (canChangeStatus) 1.0f else 0.5f

        sliderStatus.clearOnChangeListeners()
        sliderStatus.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val newStatus = valueToStatus(value.toInt())
            updateStatusUI(newStatus)
            changeTaskStatus(newStatus)
        }

        sliderStatus.post {
            sliderStatus.value = statusToValue(status)
            updateStatusUI(status)
        }
    }

    private fun handleAwaitingReportToggle(checked: Boolean) {
        val currentStatus = task.status
        val newStatus = when {
            checked && currentStatus == Config.StatusCode.IN_PROGRESS ->
                Config.StatusCode.AWAITING_REPORT_PROGRESS
            checked && currentStatus == Config.StatusCode.BLOCKED ->
                Config.StatusCode.AWAITING_REPORT_BLOCKED
            !checked && currentStatus == Config.StatusCode.AWAITING_REPORT_PROGRESS ->
                Config.StatusCode.IN_PROGRESS
            !checked && currentStatus == Config.StatusCode.AWAITING_REPORT_BLOCKED ->
                Config.StatusCode.BLOCKED
            else -> return  // هیچ تغییری لازم نیست
        }
        changeTaskStatus(newStatus)
    }

    private fun statusToValue(status: String): Float = when (status) {
        "3" -> 0f
        "2" -> 1f
        "41" -> 2f
        else -> 1f
    }

    private fun valueToStatus(value: Int): String = when (value) {
        0 -> "3"
        1 -> "2"
        2 -> "41"
        else -> "2"
    }

    private fun updateStatusUI(status: String) {
        val (text, color) = when (status) {
            Config.StatusCode.IN_PROGRESS ->
                "ادامه دارد" to Color.parseColor("#1565C0")
            Config.StatusCode.BLOCKED ->
                "متوقف" to Color.parseColor("#C62828")
            Config.StatusCode.AWAITING_REPORT_PROGRESS ->
                "⏳ منتظر گزارش" to Color.parseColor("#FF9800")
            Config.StatusCode.AWAITING_REPORT_BLOCKED ->
                "⏳ منتظر گزارش (متوقف)" to Color.parseColor("#E65100")
            Config.StatusCode.SENT_TO_SUPERVISOR ->
                "اتمام کار" to Color.parseColor("#2E7D32")
            else ->
                "نامشخص" to Color.parseColor("#78909C")
        }

        tvStatusLabel.text = text
        tvStatusLabel.setTextColor(color)
        tvStatusLabel.background.setTint(lightenColor(color, 0.9f))

        // اسلایدر در حالت منتظر-گزارش مخفی است؛ ست کردن tint مشکلی ندارد
        sliderStatus.thumbTintList = ColorStateList.valueOf(color)
        sliderStatus.trackActiveTintList = ColorStateList.valueOf(color)
        sliderStatus.tickTintList = ColorStateList.valueOf(color)
        sliderStatus.haloTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // ============================================================
    // WorkOrders
    // ============================================================
    @SuppressLint("SetTextI18n")
    private fun bindWorkOrders() {
        if (workOrdersList.isEmpty()) {
            cardWorkOrders.visibility = View.GONE
            return
        }
        cardWorkOrders.visibility = View.VISIBLE
        tvWorkOrdersTitle.text = "🔗 درخواست‌های مرتبط (${workOrdersList.size})"

        rvWorkOrders.layoutManager = LinearLayoutManager(this)
        rvWorkOrders.adapter = WorkOrderMiniAdapter(
            items = workOrdersList,
            onClick = { wo -> openWorkOrderDetail(wo) }
        )

        rvWorkOrders.visibility = if (isWorkOrdersExpanded) View.VISIBLE else View.GONE
        ivExpandWorkOrders.rotation = if (isWorkOrdersExpanded) 180f else 0f
    }

    private fun openWorkOrderDetail(wo: WorkOrderModel) {
        val intent = Intent(this, WorkOrderDetailActivity::class.java).apply {
            putExtra("WORK_ORDER_ID", wo.id)
        }
        startActivity(intent)
    }

    // ============================================================
    // Estimate
    // ============================================================
    @SuppressLint("SetTextI18n")
    private fun bindEstimate() {
        val canManageEstimate = userRole == Config.RoleCode.SUPERVISOR ||
                userRole == Config.RoleCode.MANAGER

        if (latestEstimate == null) {
            tvEstimateStatus.text = "ثبت نشده"
            tvEstimateStatus.setTextColor(Color.parseColor("#FF9800"))
            layoutEstimateEmpty.visibility = View.VISIBLE
            layoutEstimateData.visibility = View.GONE
            btnAddEstimateEmpty.visibility = if (canManageEstimate) View.VISIBLE else View.GONE
        } else {
            tvEstimateStatus.text = "آخرین برآورد"
            tvEstimateStatus.setTextColor(Color.parseColor("#2E7D32"))
            layoutEstimateEmpty.visibility = View.GONE
            layoutEstimateData.visibility = View.VISIBLE

            val est = latestEstimate!!
            val totalHours = est.parts * est.estimatedHours
            val manHours = est.manpower * totalHours

            tvEstimateManpower.text = "${est.manpower} نفر"
            tvEstimateParts.text = "${est.parts} × ${est.estimatedHours} ساعت"
            tvEstimateTotalHours.text = "$totalHours ساعت"
            tvEstimateManHours.text = "$manHours نفر-ساعت"

            tvEstimateWorkType.text = when (est.workType) {
                "1" -> "تجهیزات ثابت"
                "2" -> "عیب‌یابی تجهیزات دوار"
                "3" -> "بررسی"
                else -> "نامشخص"
            }

            val physicalText = if (est.physicalDifficulty > 0) "${est.physicalDifficulty}/5" else "—"
            val technicalText = if (est.technicalComplexity > 0) " | فنی: ${est.technicalComplexity}/5" else ""
            tvEstimatePhysical.text = "$physicalText$technicalText"
            tvEstimateConditions.text = "${est.heatLevel}°C / ${est.pollutionLevel} ppm"

            if (est.notes.isNotEmpty()) {
                tvEstimateNotes.text = "📝 ${est.notes}"
                tvEstimateNotes.visibility = View.VISIBLE
            } else {
                tvEstimateNotes.visibility = View.GONE
            }

            tvEstimateMeta.text = "📅 ${est.estimatedAt} — ${est.estimatorName}"

            btnAddEstimate.visibility = if (canManageEstimate) View.VISIBLE else View.GONE
            btnDeleteEstimate.visibility = if (canManageEstimate) View.VISIBLE else View.GONE
        }
    }

    private fun openEstimateActivity() {
        val intent = Intent(this, EstimateActivity::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_DATE", task.createDate)
            putExtra("TASK_DESC", task.description)
        }
        estimateLauncher.launch(intent)
    }

    private fun deleteEstimate(estimateId: String) {
        AlertDialog.Builder(this)
            .setTitle("حذف برآورد")
            .setMessage("آیا از حذف این برآورد مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val wasSynced = taskEstimateRepository.deleteEstimate(estimateId)
                        if (wasSynced) {
                            OutboxHelper.enqueueDeleteEstimate(this@TaskDetailActivity, estimateId)
                            ReportSyncWorker.enqueue(this@TaskDetailActivity)
                        }
                        Toast.makeText(this@TaskDetailActivity, "برآورد حذف شد", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@TaskDetailActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    // ============================================================
    // SeenBy
    // ============================================================
    @SuppressLint("SetTextI18n")
    private fun displaySeenBy(seenBy: List<String>) {
        seenByContainer.removeAllViews()

        if (seenBy.isEmpty()) {
            cardSeenBy.visibility = View.GONE
            return
        }

        cardSeenBy.visibility = View.VISIBLE

        val maxDisplay = 6
        val toShow = seenBy.take(maxDisplay)
        val remaining = seenBy.size - maxDisplay

        toShow.forEach { userId ->
            val userName = UserCache.getName(userId)
            val firstLetter = if (userName.isNotEmpty() && userName != "نامشخص")
                userName.firstOrNull()?.toString()?.uppercase() ?: "?" else "?"

            val circleView = TaskDetailUiUtils.createSeenByCircle(
                this, firstLetter, userId, userName, 26
            )
            seenByContainer.addView(circleView)
        }

        if (remaining > 0) {
            val moreView = TextView(this).apply {
                text = "+$remaining"
                textSize = 11f
                setTextColor(Color.parseColor("#78909C"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    marginStart = 4
                }
            }
            seenByContainer.addView(moreView)
        }
    }

    // ============================================================
    // AddLog button
    // ============================================================
    private fun setupAddLogButton(currentUserId: String) {
        val status = task.status
        val isArchived = status == STATUS_ARCHIVED
        val isResponsible = task.responsible == currentUserId
        val isSentToSupervisor = status == STATUS_SENT_TO_SUPERVISOR

        when {
            // سرشیفت → بررسی و تأیید نهایی
            isSentToSupervisor && userRole == Config.RoleCode.SUPERVISOR -> {
                bottomBar.visibility = View.VISIBLE
                btnAddLog.text = "✓ بررسی و تأیید نهایی"
                btnAddLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800"))
                btnAddLog.setOnClickListener { completeTask() }
            }

            // مسئول → ثبت گزارش جدید
            !isArchived && isResponsible &&
                    status != STATUS_SENT_TO_SUPERVISOR &&
                    status != Config.StatusCode.BLOCKED -> {
                bottomBar.visibility = View.VISIBLE
                btnAddLog.text = if (Config.StatusCode.isAwaitingReport(status))
                    "📝 ثبت گزارش (منتظر گزارش)"
                else
                    "📝 ثبت گزارش جدید"
                btnAddLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                btnAddLog.setOnClickListener { startAddLogActivity() }
            }

            else -> {
                bottomBar.visibility = View.GONE
            }
        }
    }

    // ============================================================
    // Edit / Delete / Refer
    // ============================================================
    private fun editTask() {
        val intent = Intent(this, CreateTaskActivity::class.java).apply {
            putExtra("IS_EDIT", true)
            putExtra("TASK_ID", task.id)
            putExtra("TITLE", task.title)
            putExtra("DESC", task.description)
            putExtra("URGENCY", task.urgency)
            putExtra("PRIORITY", task.priority)
            putExtra("SCHEDULED_DATE", task.scheduledDate ?: "")
        }
        startActivity(intent)
    }

    private fun deleteTask() {
        AlertDialog.Builder(this)
            .setTitle("حذف کار")
            .setMessage("آیا از حذف این کار مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        taskLocalRepository.deleteTaskLocally(task.id)
                        OutboxHelper.enqueueDeleteTask(this@TaskDetailActivity, task.id)
                        ReportSyncWorker.enqueue(this@TaskDetailActivity)

                        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
                        val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
                        val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"

                        ActivityLogger.log(
                            context = this@TaskDetailActivity,
                            userId = userId,
                            userName = userName,
                            action = "delete",
                            targetType = "TASK",
                            targetId = task.id,
                            description = "کار شماره ${task.id} با عنوان «${task.title}» حذف شد"
                        )

                        Toast.makeText(this@TaskDetailActivity, "کار حذف شد", Toast.LENGTH_SHORT).show()
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this@TaskDetailActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    private fun referTask() {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""

        if (latestEstimate == null) {
            AlertDialog.Builder(this)
                .setTitle("هشدار")
                .setMessage("برای این کار هنوز برآورد اولیه ثبت نشده است. آیا مطمئنید می‌خواهید ارجاع دهید؟")
                .setPositiveButton("ادامه ارجاع") { _, _ -> showReferDialog(currentUserId) }
                .setNegativeButton("انصراف", null)
                .show()
        } else {
            showReferDialog(currentUserId)
        }
    }

    private fun showReferDialog(currentUserId: String) {
        val referDialog = ReferDialog(
            context = this,
            taskTitle = task.title,
            currentAssignees = task.assignedTo,
            currentResponsible = task.responsible
        )

        referDialog.setOnReferSubmitListener { assigneeIds, referralType, responsibleId ->
            val oldAssignees = task.assignedTo
            val oldResponsible = task.responsible

            taskRepository.assignTask(
                taskId = task.id,
                assigneeIds = assigneeIds,
                referralType = referralType,
                responsibleId = responsibleId,
                referredBy = currentUserId,
                onSuccess = {
                    runOnUiThread {
                        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
                        val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
                        val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"

                        val assigneeNames = assigneeIds.split(",").mapNotNull { UserCache.getName(it) }.joinToString("، ")
                        val responsibleName = if (responsibleId != null) UserCache.getName(responsibleId) else "تعیین نشده"
                        val oldAssigneeNames = oldAssignees.split(",").mapNotNull { UserCache.getName(it) }.joinToString("، ")
                        val oldResponsibleName = UserCache.getName(oldResponsible)

                        val description = "کار شماره ${task.id} با عنوان «${task.title}» توسط کاربر $userName ارجاع شد.\n" +
                                "گروه قبلی: [$oldAssigneeNames] - گروه جدید: [$assigneeNames]\n" +
                                "مسئول قبلی: [$oldResponsibleName] - مسئول جدید: [$responsibleName]"

                        ActivityLogger.log(
                            context = this,
                            userId = userId,
                            userName = userName,
                            action = "REFER_TASK",
                            targetType = "TASK",
                            targetId = task.id,
                            description = description
                        )

                        Toast.makeText(this, "ارجاع با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                        TaskSyncer.syncTasksFromServer(this@TaskDetailActivity)
                        fetchFullTaskFromServer()
                    }
                },
                onError = { message ->
                    runOnUiThread {
                        Toast.makeText(this, "خطا: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        referDialog.show()
    }

    // ============================================================
    // Logs
    // ============================================================
    @SuppressLint("SetTextI18n")
    private fun updateLogsUI() {
        if (!::task.isInitialized) return

        tvLogsTitle.text = "📝 گزارش‌ها (${logsList.size})"
        logsHeader.visibility = if (logsList.isNotEmpty()) View.VISIBLE else View.GONE

        if (logsList.isEmpty()) {
            tvEmptyLogs.visibility = View.VISIBLE
            rvLogs.visibility = View.GONE
        } else {
            tvEmptyLogs.visibility = View.GONE
            rvLogs.visibility = View.VISIBLE
            taskLogAdapter.updateLogs(logsList)
        }
    }

    private fun deleteLog(log: TaskLogModel) {
        AlertDialog.Builder(this)
            .setTitle("حذف گزارش")
            .setMessage("آیا از حذف این گزارش مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val wasSynced = reportLocalRepository.deleteReportLocally(log.id)
                        if (wasSynced) {
                            OutboxHelper.enqueueDeleteTaskLog(this@TaskDetailActivity, log.id)
                            ReportSyncWorker.enqueue(this@TaskDetailActivity)
                        }
                        Toast.makeText(this@TaskDetailActivity, "گزارش حذف شد", Toast.LENGTH_SHORT).show()
                        updateTaskStatusFromLastLog()
                    } catch (e: Exception) {
                        Toast.makeText(this@TaskDetailActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    private fun updateTaskStatusFromLastLog() {
        if (logsList.isEmpty()) {
            val baseStatus = Config.StatusCode.unwrapAwaitingReport(task.status)
            updateTaskStatusDirectly(
                if (baseStatus == Config.StatusCode.BLOCKED) Config.StatusCode.BLOCKED
                else Config.StatusCode.IN_PROGRESS
            )
            return
        }
        // ✅ logsList قبلاً سورت شده (date DESC, startTime DESC)
        val lastLog = logsList.firstOrNull()
        lastLog?.let {
            val newStatus = when (it.newStatus) {
                Config.StatusCode.IN_PROGRESS -> Config.StatusCode.IN_PROGRESS
                Config.StatusCode.BLOCKED -> Config.StatusCode.BLOCKED
                Config.StatusCode.SENT_TO_SUPERVISOR -> Config.StatusCode.SENT_TO_SUPERVISOR
                else -> Config.StatusCode.IN_PROGRESS
            }
            updateTaskStatusDirectly(newStatus)
        }
    }

    // ============================================================
    // Add Log
    // ============================================================
    private fun startAddLogActivity(log: TaskLogModel? = null) {
        val intent = Intent(this, AddLogActivity::class.java).apply {
            if (log != null) {
                putExtra("IS_EDIT_MODE", true)
                putExtra("LOG_ID", log.id)
                putExtra("TASK_ID", log.taskId)
            } else {
                putExtra("TASK_ID", task.id)
            }
            putTaskExtras(this)
        }
        startActivity(intent)
    }

    private fun putTaskExtras(intent: Intent) {
        intent.apply {
            putExtra("TITLE", task.title)
            putExtra("DESC", task.description)
            putExtra("CREATOR", task.creator)
            putExtra("DATE", task.createDate)
            putExtra("RESPONSIBLE", task.responsible)
            putExtra("ASSIGNED_TO", task.assignedTo)
            putExtra("PRIORITY", task.priority)
            putExtra("URGENCY", task.urgency)
            putExtra("REFERRED_BY", task.referredBy)
            putExtra("SCHEDULED_DATE", task.scheduledDate ?: "")
        }
    }

    // ============================================================
    // Complete Task
    // ============================================================
    private fun completeTask() {
        val currentUserId = getCurrentUserId()
        val isResponsible = task.responsible == currentUserId

        when {
            isResponsible && task.status == STATUS_COMPLETE_REQUEST -> {
                updateTaskStatusDirectly(STATUS_SENT_TO_SUPERVISOR) { success ->
                    if (success) {
                        Toast.makeText(this, "درخواست تأیید به سرشیفت ارسال شد", Toast.LENGTH_SHORT).show()
                        navigateToCartable()
                    } else {
                        Toast.makeText(this, "خطا در ارسال درخواست", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            userRole == Config.RoleCode.SUPERVISOR && task.status == STATUS_SENT_TO_SUPERVISOR -> {
                startActivity(Intent(this, CompleteTaskActivity::class.java).apply {
                    putExtra("TASK_ID", task.id)
                    putExtra("TASK_TITLE", task.title)
                    putExtra("CURRENT_STATUS", task.status)
                })
            }
            else -> {
                Toast.makeText(this, "شما مجوز انجام این عملیات را ندارید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTaskStatusDirectly(newStatus: String, callback: ((Boolean) -> Unit)? = null) {
        val url = "${Config.BASE_URL}?action=updateTaskStatus"
        val jsonObject = JSONObject().apply {
            put("taskId", task.id)
            put("status", newStatus)
        }
        val request = JsonObjectRequest(
            Request.Method.POST, url, jsonObject,
            { _ ->
                task = task.copy(status = newStatus)
                bindTask()
                callback?.invoke(true)
            },
            { error ->
                Log.e("TaskDetail", "Error updating status: ${error.message}")
                callback?.invoke(false)
            }
        )
        VolleySingleton.getInstance(this).add(request)
    }

    private fun changeTaskStatus(newStatus: String) {
        if (task.status == newStatus) return

        task = task.copy(status = newStatus)

        // ✅ آپدیت فوری UI
        bindTask()

        lifecycleScope.launch {
            try {
                taskLocalRepository.updateTaskStatus(task.id, newStatus)
                OutboxHelper.enqueueUpdateTaskStatus(this@TaskDetailActivity, task.id, newStatus)
                ReportSyncWorker.enqueue(this@TaskDetailActivity)
            } catch (e: Exception) {
                Toast.makeText(this@TaskDetailActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToCartable() {
        val intent = Intent(this, CartableActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    // ============================================================
    // Helper
    // ============================================================
    private fun getCurrentUserId(): String {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        return sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
    }
}