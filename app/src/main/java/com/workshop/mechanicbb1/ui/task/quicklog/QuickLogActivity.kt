package com.workshop.mechanicbb1.ui.task.quicklog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.log.AddLogActivity
import TaskModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class QuickLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: QuickLogTaskAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var tvSubtitle: TextView

    private val taskList = mutableListOf<TaskModel>()
    private var currentUserId = ""

    private lateinit var taskLocalRepository: TaskLocalRepository
    private var tasksJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_log)

        initViews()
        setupToolbar()
        getCurrentUserInfo()

        taskLocalRepository = TaskLocalRepository(this)

        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        // Flow خودکار آپدیت می‌کنه، ولی برای اطمینان
        if (::taskLocalRepository.isInitialized) {
            loadTasks()
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewTasks)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvSubtitle = findViewById(R.id.tvSubtitle)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuickLogTaskAdapter { task ->
            openAddLogForTask(task)
        }
        recyclerView.adapter = adapter
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "ثبت گزارش سریع"
        }
    }

    private fun getCurrentUserInfo() {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
    }

    private fun loadTasks() {
        if (currentUserId.isEmpty()) {
            tvEmptyState.text = "کاربر شناسایی نشد"
            tvEmptyState.visibility = View.VISIBLE
            return
        }

        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE

        tasksJob?.cancel()
        tasksJob = lifecycleScope.launch {
            taskLocalRepository.getResponsibleTasks(currentUserId).collectLatest { tasks ->
                progressBar.visibility = View.GONE
                taskList.clear()
                taskList.addAll(tasks)
                adapter.submitList(taskList)
                updateEmptyState()

                Log.d("QuickLog", "Responsible tasks: ${tasks.size}")
            }
        }
    }

    private fun openAddLogForTask(task: TaskModel) {
        val intent = Intent(this, AddLogActivity::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TITLE", task.title)
            putExtra("DESC", task.description)
            putExtra("CREATOR", task.creator)
            putExtra("DATE", task.createDate)
            putExtra("RESPONSIBLE", task.responsible)
            putExtra("ASSIGNED_TO", task.assignedTo)
            putExtra("UNIT", task.unit)
            putExtra("PRIORITY", task.priority)
            putExtra("SUB_UNIT", task.sub_unit)
            putExtra("DECLARATION_METHOD", task.declaration_method)
            putExtra("REQUESTER", task.requester)
            putExtra("REQUEST_DATE", task.request_date)
            putExtra("INITIAL_REVIEW", task.initial_review)
            putExtra("SYSTEM_REQUEST_NUMBER", task.system_request_number)
            putExtra("URGENCY", task.urgency)
            putExtra("FROM_QUICK_LOG", true)
        }
        startActivity(intent)
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            tvEmptyState.text = "شما مسئول هیچ کاری در حال انجام نیستید"
            tvEmptyState.visibility = View.VISIBLE
        } else {
            tvEmptyState.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}