package com.workshop.mechanicbb1.ui.task.list

import TaskModel
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.TaskLocalRepository
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

        // ۱. sync
        com.workshop.mechanicbb1.data.sync.TaskSyncer.syncTasksFromServer(
            context = requireContext(),
            userId = currentUserId
        ) { count -> android.util.Log.d("MyTasksFragment", "Synced $count tasks") }

        // ۲. بخش ۱: در حال انجام (همه‌ی نقش‌ها: responsible=من)
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getMyInProgress(currentUserId).collectLatest { tasks ->
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

        // ۳. بخش ۲: متوقف — بسته به نقش
        val blockedFlow = if (userRole == Config.RoleCode.EMPLOYEE) {
            taskLocalRepository.getMyBlocked(currentUserId)
        } else {
            taskLocalRepository.getAllBlocked()   // سرشیفت/مدیر: همه‌ی تیم
        }

        viewLifecycleOwner.lifecycleScope.launch {
            blockedFlow.collectLatest { tasks ->
                blockedList.clear()
                blockedList.addAll(tasks)
                val label = if (userRole == Config.RoleCode.EMPLOYEE) "متوقف شده" else "متوقف (تیم)"
                setupSection(
                    section = blockedSection,
                    recycler = blockedRecycler,
                    countLabel = tvBlockedCount,
                    tasks = tasks,
                    label = label
                )
                checkAllEmpty()
            }
        }

        // ۴. بخش ۳: اتمام — بسته به نقش
        val completedFlow = if (userRole == Config.RoleCode.EMPLOYEE) {
            taskLocalRepository.getMyCompleted(currentUserId)
        } else {
            taskLocalRepository.getAllCompleted()   // سرشیفت/مدیر: همه‌ی تیم
        }

        viewLifecycleOwner.lifecycleScope.launch {
            completedFlow.collectLatest { tasks ->
                completedList.clear()
                completedList.addAll(tasks)
                val label = if (userRole == Config.RoleCode.EMPLOYEE) "به اتمام رسیده" else "اتمام (تیم)"
                setupSection(
                    section = completedSection,
                    recycler = completedRecycler,
                    countLabel = tvCompletedCount,
                    tasks = tasks,
                    label = label
                )
                checkAllEmpty()
            }
        }

        loadingLayout.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
    }

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
                onItemClick = { task -> openTaskDetail(task) }
            )
        }
    }

    private fun checkAllEmpty() {
        val isEmpty = inProgressList.isEmpty() && blockedList.isEmpty() && completedList.isEmpty()
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