package com.workshop.mechanicbb1.ui.workorder

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.WorkOrderLocalRepository
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.sync.WorkOrderSyncer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkOrderListActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var filterBar: View
    private lateinit var tvFilterLabel: TextView
    private lateinit var rvWorkOrders: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rgClassFilter: RadioGroup

    private lateinit var adapter: WorkOrderAdapter
    private lateinit var localRepository: WorkOrderLocalRepository   // ← Room
    private var workOrdersJob: Job? = null

    private var currentUnit: String = "بهره‌برداری"
    private var currentClass: String = ""

    private val units = listOf(
        UnitItem("بهره‌برداری", "بهره‌برداری"),
        UnitItem("نمکزدایی", "نمکزدایی"),
        UnitItem("گاز", "تقویت فشار گاز")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.workorder_activity_list)

        localRepository = WorkOrderLocalRepository(this)

        initViews()
        setupToolbar()
        setupTabs()
        loadWorkOrders()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        tabLayout = findViewById(R.id.tabLayout)
        filterBar = findViewById(R.id.filterBar)
        tvFilterLabel = findViewById(R.id.tvFilterLabel)
        rvWorkOrders = findViewById(R.id.rvWorkOrders)
        tvEmpty = findViewById(R.id.tvEmpty)
        progressBar = findViewById(R.id.progressBar)
        rgClassFilter = findViewById(R.id.rgClassFilter)

        rvWorkOrders.layoutManager = LinearLayoutManager(this)

        adapter = WorkOrderAdapter(
            workOrders = emptyList(),
            showClassHeaders = true,
            onItemClick = { workOrder -> openDetail(workOrder) }
        )
        rvWorkOrders.adapter = adapter

        filterBar.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        rgClassFilter.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbClassAll -> currentClass = ""
                R.id.rbClass14 -> currentClass = "14"
                R.id.rbClass12 -> currentClass = "12"
                R.id.rbClass12P -> currentClass = "12P"
            }
            updateFilterLabel()
            drawerLayout.closeDrawers()
            loadWorkOrders()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupTabs() {
        units.forEach { unit ->
            tabLayout.addTab(tabLayout.newTab().setText(unit.displayName))
        }

        tabLayout.getTabAt(0)?.select()
        currentUnit = units[0].dbName

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentUnit = units[tab.position].dbName
                loadWorkOrders()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateFilterLabel() {
        val classText = when (currentClass) {
            "14" -> "کلاس ۱۴"
            "12" -> "کلاس ۱۲"
            "12P" -> "پیشگیرانه"
            else -> "همه‌ی کلاس‌ها"
        }
        tvFilterLabel.text = "🔖 کلاس: $classText"
    }

    // ============ بارگذاری (Offline-First) ============
    private fun loadWorkOrders() {
        // ۱. اول sync از سرور (پس‌زمینه)
        WorkOrderSyncer.syncWorkOrdersFromServer(this, currentUnit) { count ->
            Log.d("WorkOrderList", "Synced $count work orders")
        }

        // ۲. از Room بخون (Flow خودکار آپدیت می‌شه)
        workOrdersJob?.cancel()
        workOrdersJob = lifecycleScope.launch {
            localRepository.getWorkOrders(currentUnit, currentClass).collectLatest { list ->
                if (list.isEmpty()) {
                    rvWorkOrders.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    rvWorkOrders.visibility = View.VISIBLE
                    tvEmpty.visibility = View.GONE
                    adapter.updateData(list, showHeaders = currentClass.isEmpty())
                }
            }
        }
    }

    private fun openDetail(workOrder: WorkOrderModel) {
        val intent = Intent(this, WorkOrderDetailActivity::class.java).apply {
            putExtra("WORK_ORDER_ID", workOrder.id)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_work_order_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_work_order -> {
                startActivity(Intent(this, CreateWorkOrderActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    data class UnitItem(
        val displayName: String,
        val dbName: String
    )
}