package com.workshop.mechanicbb1.ui.task.list

import TaskModel
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.TaskDetailActivity
import com.workshop.mechanicbb1.utils.UserCache
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MyTasksFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var loadingLayout: View
    private lateinit var emptyState: TextView

    private lateinit var inProgressSection: View
    private lateinit var inProgressRecycler: RecyclerView
    private lateinit var tvInProgressCount: TextView

    private lateinit var blockedSection: View
    private lateinit var blockedRecycler: RecyclerView
    private lateinit var tvBlockedCount: TextView

    private lateinit var completedSection: View
    private lateinit var completedRecycler: RecyclerView
    private lateinit var tvCompletedCount: TextView

    private lateinit var taskLocalRepository: TaskLocalRepository

    private val inProgressList = mutableListOf<TaskModel>()
    private val blockedList = mutableListOf<TaskModel>()
    private val completedList = mutableListOf<TaskModel>()

    private lateinit var awaitingReportSection: View
    private lateinit var awaitingReportRecycler: RecyclerView
    private lateinit var tvAwaitingReportCount: TextView

    private val awaitingReportList = mutableListOf<TaskModel>()

    private var currentUserId = ""
    private var userRole = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskLocalRepository = TaskLocalRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        emptyState = view.findViewById(R.id.emptyStateText)

        inProgressSection = view.findViewById(R.id.inProgressSection)
        inProgressRecycler = view.findViewById(R.id.rvInProgress)
        tvInProgressCount = view.findViewById(R.id.tvInProgressCount)

        blockedSection = view.findViewById(R.id.blockedSection)
        blockedRecycler = view.findViewById(R.id.rvBlocked)
        tvBlockedCount = view.findViewById(R.id.tvBlockedCount)

        completedSection = view.findViewById(R.id.completedSection)
        completedRecycler = view.findViewById(R.id.rvCompleted)
        tvCompletedCount = view.findViewById(R.id.tvCompletedCount)

        inProgressRecycler.layoutManager = LinearLayoutManager(context)
        blockedRecycler.layoutManager = LinearLayoutManager(context)
        completedRecycler.layoutManager = LinearLayoutManager(context)

        awaitingReportSection = view.findViewById(R.id.awaitingReportSection)
        awaitingReportRecycler = view.findViewById(R.id.rvAwaitingReport)
        tvAwaitingReportCount = view.findViewById(R.id.tvAwaitingReportCount)

        awaitingReportRecycler.layoutManager = LinearLayoutManager(context)

        swipeRefreshLayout.setOnRefreshListener { fetchTasks() }

        fetchTasks()
    }

    override fun onResume() {
        super.onResume()
        fetchTasks()
    }

    fun refreshTasks() {
        fetchTasks()
    }

    private fun fetchTasks() {
        val sharedPref = requireContext().getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
        currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        if (currentUserId.isEmpty()) {
            emptyState.text = "کاربر شناسایی نشد"
            emptyState.visibility = View.VISIBLE
            return
        }

        loadingLayout.visibility = View.VISIBLE

        // sync
        com.workshop.mechanicbb1.data.sync.TaskSyncer.syncTasksFromServer(
            context = requireContext(),
            userId = currentUserId
        ) { count -> android.util.Log.d("MyTasksFragment", "Synced $count tasks") }

        if (userRole == Config.RoleCode.EMPLOYEE) {
            // ============================================
            // نقش کارمند: ۴ بخش
            // ============================================
            bindEmployeeSections()
        } else {
            // ============================================
            // نقش سرشیفت/مدیر: ۳ بخش (بدون تغییر)
            // ============================================
            bindSupervisorSections()
        }

        loadingLayout.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
    }

    /**
     * ۴ بخش برای کارمند — بر اساس assignedTo
     */
    private fun bindEmployeeSections() {
        // ۱. در حال انجام (status=2)
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getEmployeeInProgress(currentUserId).collectLatest { tasks ->
                inProgressList.clear()
                inProgressList.addAll(tasks)
                if (tasks.isNotEmpty()) UserCache.preloadFromTasks(tasks)
                setupSection(
                    section = inProgressSection,
                    recycler = inProgressRecycler,
                    countLabel = tvInProgressCount,
                    tasks = tasks,
                    label = "در حال انجام"
                )
                checkAllEmpty()
            }
        }

        // ۲. منتظر گزارش (status=21, 31)
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getEmployeeAwaitingReport(currentUserId).collectLatest { tasks ->
                awaitingReportList.clear()
                awaitingReportList.addAll(tasks)
                setupSection(
                    section = awaitingReportSection,
                    recycler = awaitingReportRecycler,
                    countLabel = tvAwaitingReportCount,
                    tasks = tasks,
                    label = "⏳ منتظر گزارش"
                )
                checkAllEmpty()
            }
        }

        // ۳. متوقف (status=3)
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getEmployeeBlocked(currentUserId).collectLatest { tasks ->
                blockedList.clear()
                blockedList.addAll(tasks)
                setupSection(
                    section = blockedSection,
                    recycler = blockedRecycler,
                    countLabel = tvBlockedCount,
                    tasks = tasks,
                    label = "متوقف شده"
                )
                checkAllEmpty()
            }
        }

        // ۴. اتمام (status=41)
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getEmployeeCompleted(currentUserId).collectLatest { tasks ->
                completedList.clear()
                completedList.addAll(tasks)
                setupSection(
                    section = completedSection,
                    recycler = completedRecycler,
                    countLabel = tvCompletedCount,
                    tasks = tasks,
                    label = "به اتمام رسیده (منتظر تأیید)"
                )
                checkAllEmpty()
            }
        }
    }

    /**
     * ۴ بخش برای سرشیفت/مدیر — تیم + خودم
     */
    private fun bindSupervisorSections() {
        // ۱. در حال انجام (خودم فقط) — status=2
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getSupervisorMyInProgress(currentUserId).collectLatest { tasks ->
                inProgressList.clear()
                inProgressList.addAll(tasks)
                if (tasks.isNotEmpty()) UserCache.preloadFromTasks(tasks)
                setupSection(
                    section = inProgressSection,
                    recycler = inProgressRecycler,
                    countLabel = tvInProgressCount,
                    tasks = tasks,
                    label = "در حال انجام (خودم)"
                )
                checkAllEmpty()
            }
        }

        // ۲. منتظر گزارش (تیم) — status=21, 31
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getTeamAwaitingReport().collectLatest { tasks ->
                awaitingReportList.clear()
                awaitingReportList.addAll(tasks)
                setupSection(
                    section = awaitingReportSection,
                    recycler = awaitingReportRecycler,
                    countLabel = tvAwaitingReportCount,
                    tasks = tasks,
                    label = "⏳ منتظر گزارش (تیم)"
                )
                checkAllEmpty()
            }
        }

        // ۳. متوقف (تیم) — status=3
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getTeamBlocked().collectLatest { tasks ->
                blockedList.clear()
                blockedList.addAll(tasks)
                setupSection(
                    section = blockedSection,
                    recycler = blockedRecycler,
                    countLabel = tvBlockedCount,
                    tasks = tasks,
                    label = "متوقف (تیم)"
                )
                checkAllEmpty()
            }
        }

        // ۴. منتظر تأیید (تیم) — status=41
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getTeamCompleted().collectLatest { tasks ->
                completedList.clear()
                completedList.addAll(tasks)
                setupSection(
                    section = completedSection,
                    recycler = completedRecycler,
                    countLabel = tvCompletedCount,
                    tasks = tasks,
                    label = "منتظر تأیید (تیم)"
                )
                checkAllEmpty()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupSection(
        section: View,
        recycler: RecyclerView,
        countLabel: TextView,
        tasks: List<TaskModel>,
        label: String
    ) {
        if (tasks.isEmpty()) {
            section.visibility = View.GONE
        } else {
            section.visibility = View.VISIBLE
            countLabel.text = "$label (${tasks.size})"
            recycler.adapter = ExpandableTaskAdapter(
                tasks = tasks,
                tabType = "myCartable",
                scope = viewLifecycleOwner.lifecycleScope,
                onItemClick = { task -> openTaskDetail(task) }
            )
        }
    }

    private fun checkAllEmpty() {
        val isEmpty = if (userRole == Config.RoleCode.EMPLOYEE) {
            inProgressList.isEmpty() && awaitingReportList.isEmpty() &&
                    blockedList.isEmpty() && completedList.isEmpty()
        } else {
            inProgressList.isEmpty() && blockedList.isEmpty() && completedList.isEmpty()
        }
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            emptyState.text = "کاری برای نمایش وجود ندارد"
        }
    }

    private fun openTaskDetail(task: TaskModel) {
        val intent = Intent(requireContext(), TaskDetailActivity::class.java)
        intent.putExtra("TASK_ID", task.id)
        intent.putExtra("TITLE", task.title)
        intent.putExtra("STATUS", task.status)
        intent.putExtra("DESC", task.description)
        intent.putExtra("CREATOR", task.creator)
        intent.putExtra("DATE", task.createDate)
        intent.putExtra("RESPONSIBLE", task.responsible)
        intent.putExtra("ASSIGNED_TO", task.assignedTo)
        intent.putExtra("PRIORITY", task.priority)
        intent.putExtra("URGENCY", task.urgency)
        intent.putExtra("REFERRED_BY", task.referredBy)
        intent.putExtra("SCHEDULED_DATE", task.scheduledDate ?: "")
        startActivity(intent)
    }
}