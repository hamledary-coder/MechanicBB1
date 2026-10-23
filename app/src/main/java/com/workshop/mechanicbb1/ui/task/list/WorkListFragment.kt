package com.workshop.mechanicbb1.ui.task.list

import TaskModel
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.TaskDetailActivity
import com.workshop.mechanicbb1.ui.task.repository.TaskRepository
import com.workshop.mechanicbb1.utils.UserCache
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class WorkListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecyclerView.Adapter<*>
    private var status: String? = null
    private var taskList = mutableListOf<TaskModel>()
    private lateinit var loadingLayout: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskLocalRepository: TaskLocalRepository

    companion object {
        @JvmStatic
        fun newInstance(status: String) = WorkListFragment().apply {
            arguments = Bundle().apply {
                putString("status", status)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = arguments?.getString("status")
        taskRepository = TaskRepository(requireContext())
        taskLocalRepository = TaskLocalRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_work_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        loadingLayout = view.findViewById(R.id.loadingLayout)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        recyclerView = view.findViewById(R.id.recyclerViewTasks)
        recyclerView.layoutManager = LinearLayoutManager(context)

        swipeRefreshLayout.setOnRefreshListener {
            fetchTasks()   // فقط refresh از Room + sync جدید
        }

        fetchTasks()   // بار اول
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_work_list, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "تاریخ (جدیدترین اول)",
            "تاریخ (قدیمی‌ترین اول)",
            "اولویت (بالا به پایین)",
            "عنوان (الفبا)"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("مرتب‌سازی بر اساس")
            .setItems(sortOptions) { _, which ->
                when (which) {
                    0 -> sortTasksByDateNewest()
                    1 -> sortTasksByDateOldest()
                    2 -> sortTasksByPriority()
                    3 -> sortTasksByTitle()
                }
            }
            .show()
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
        val currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        val userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        hideEmptyState()
        if (!swipeRefreshLayout.isRefreshing) {
            loadingLayout.visibility = View.VISIBLE
        }

        // ۱. اول sync کن (آپدیت از سرور به Room)
        com.workshop.mechanicbb1.data.sync.TaskSyncer.syncTasksFromServer(
            context = requireContext(),
            userId = currentUserId
        ) { count ->
            Log.d("WorkListFragment", "Synced $count tasks")
        }

        // ۲. همزمان از Room بخون (Flow خودش آپدیت می‌کنه)
        viewLifecycleOwner.lifecycleScope.launch {
            taskLocalRepository.getTasksForTab(status ?: "", currentUserId, userRole)
                .collectLatest { tasks ->
                    // این بلاک هر بار که Room آپدیت می‌شه اجرا می‌شه
                    taskList.clear()
                    taskList.addAll(tasks)

                    if (taskList.isNotEmpty()) {
                        UserCache.preloadFromTasks(taskList)
                    }

                    loadingLayout.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false

                    if (taskList.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        setupAdapter()
                    }
                }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupAdapter() {
        adapter = ExpandableTaskAdapter(
            tasks = taskList,
            tabType = when (status?.trim()) {
                "اقدام نشده" -> "unassigned"
                "در حال انجام" -> "inProgress"
                "کارتابل من" -> "myCartable"
                else -> "unassigned"
            },
            onItemClick = { task -> openTaskDetail(task) }
        )
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
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
        intent.putExtra("UNIT", task.unit)
        intent.putExtra("PRIORITY", task.priority)
        intent.putExtra("SUB_UNIT", task.sub_unit)
        intent.putExtra("DECLARATION_METHOD", task.declaration_method)
        intent.putExtra("REQUESTER", task.requester)
        intent.putExtra("REQUEST_DATE", task.request_date)
        intent.putExtra("INITIAL_REVIEW", task.initial_review)
        intent.putExtra("SYSTEM_REQUEST_NUMBER", task.system_request_number)
        intent.putExtra("URGENCY", task.urgency)
        intent.putExtra("REFERRED_BY", task.referredBy)
        startActivity(intent)
    }

    private fun showEmptyState() {
        val emptyTextView = view?.findViewById<TextView>(R.id.emptyStateText)
        if (emptyTextView == null) {
            val emptyView = LayoutInflater.from(context).inflate(R.layout.empty_state, recyclerView.parent as ViewGroup, false)
            (recyclerView.parent as ViewGroup).addView(emptyView)
        } else {
            emptyTextView.visibility = View.VISIBLE
        }
        recyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        view?.findViewById<TextView>(R.id.emptyStateText)?.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sortTasksByDateNewest() {
        taskList.sortByDescending { it.createDate }
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sortTasksByDateOldest() {
        taskList.sortBy { it.createDate }
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sortTasksByPriority() {
        taskList.sortByDescending { it.priority }
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sortTasksByTitle() {
        taskList.sortBy { it.title }
        adapter.notifyDataSetChanged()
    }
}