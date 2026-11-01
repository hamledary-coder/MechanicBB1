package com.workshop.mechanicbb1.ui.archive

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.ui.task.detail.TaskDetailActivity
import com.workshop.mechanicbb1.ui.task.list.ExpandableTaskAdapter
import com.workshop.mechanicbb1.utils.UserCache
import TaskModel
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.workshop.mechanicbb1.data.local.TaskLocalRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class ArchiveActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExpandableTaskAdapter
    private val tasksList = mutableListOf<TaskModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "بایگانی"

        recyclerView = findViewById(R.id.recyclerViewArchive)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadArchivedTasks()
    }

    private fun loadArchivedTasks() {
        lifecycleScope.launch {
            val taskLocalRepo = TaskLocalRepository(this@ArchiveActivity)
            taskLocalRepo.getArchivedTasks().collectLatest { tasks ->
                tasksList.clear()
                tasksList.addAll(tasks)

                Log.d("Archive", "Tasks loaded: ${tasksList.size}")

                if (tasksList.isNotEmpty()) {
                    UserCache.preloadFromTasks(tasksList)
                }

                if (tasksList.isEmpty()) {
                    Toast.makeText(this@ArchiveActivity, "هیچ کار بایگانی شده‌ای یافت نشد", Toast.LENGTH_SHORT).show()
                }

                setupAdapter()
            }
        }
    }

    private fun setupAdapter() {
        Log.d("Archive", "setupAdapter called, tasksList size: ${tasksList.size}")

        adapter = ExpandableTaskAdapter(
            tasks = tasksList,
            tabType = "archived",
        ) { task -> openTaskDetail(task) }
        recyclerView.adapter = adapter
    }

    private fun openTaskDetail(task: TaskModel) {
        val intent = Intent(this, TaskDetailActivity::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TITLE", task.title)
            putExtra("STATUS", task.status)
            putExtra("DESC", task.description)
            putExtra("CREATOR", task.creator)
            putExtra("DATE", task.createDate)
            putExtra("RESPONSIBLE", task.responsible)
            putExtra("ASSIGNED_TO", task.assignedTo)
            //putExtra("UNIT", task.unit)
            putExtra("PRIORITY", task.priority)
            //putExtra("SUB_UNIT", task.sub_unit)
            //putExtra("DECLARATION_METHOD", task.declaration_method)
            //putExtra("REQUESTER", task.requester)
            //putExtra("REQUEST_DATE", task.request_date)
            //putExtra("INITIAL_REVIEW", task.initial_review)
            //putExtra("SYSTEM_REQUEST_NUMBER", task.system_request_number)
            putExtra("URGENCY", task.urgency)
            putExtra("REFERRED_BY", task.referredBy)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // هر بار که صفحه نمایش داده می‌شود، داده‌ها را دوباره بارگیری کن
        loadArchivedTasks()
    }
}