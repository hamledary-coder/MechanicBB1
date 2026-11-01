package com.workshop.mechanicbb1.ui.workorder

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
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
    private lateinit var rgClassFilter: RadioGroup
    private lateinit var rgStatusFilter: RadioGroup
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnClearFilters: MaterialButton

    private lateinit var adapter: WorkOrderAdapter
    private lateinit var localRepository: WorkOrderLocalRepository
    private var workOrdersJob: Job? = null

    // وضعیت فیلترها
    private var currentUnit: String = "بهره‌برداری"
    private var currentClass: String = ""
    private var currentStatus: String = ""
    private var currentSearch: String = ""

    // Debounce برای جستجو
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

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
        setupDrawerFilters()
        loadWorkOrders()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        tabLayout = findViewById(R.id.tabLayout)
        filterBar = findViewById(R.id.filterBar)
        tvFilterLabel = findViewById(R.id.tvFilterLabel)
        rvWorkOrders = findViewById(R.id.rvWorkOrders)
        tvEmpty = findViewById(R.id.tvEmpty)
        rgClassFilter = findViewById(R.id.rgClassFilter)
        rgStatusFilter = findViewById(R.id.rgStatusFilter)
        etSearch = findViewById(R.id.etSearch)
        btnClearFilters = findViewById(R.id.btnClearFilters)

        rvWorkOrders.layoutManager = LinearLayoutManager(this)

        adapter = WorkOrderAdapter(
            workOrders = emptyList(),
            showClassHeaders = true,
            onItemClick = { workOrder -> openDetail(workOrder) }
        )
        rvWorkOrders.adapter = adapter

        // کلیک روی نوار فیلتر → باز کردن Drawer
        filterBar.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
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

    private fun setupDrawerFilters() {
        // ============ فیلتر کلاس ============
        rgClassFilter.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbClassAll -> currentClass = ""
                R.id.rbClass14 -> currentClass = "14"
                R.id.rbClass12 -> currentClass = "12"
                R.id.rbClass12P -> currentClass = "12P"
                R.id.rbClass15 -> currentClass = "15"
            }
            updateFilterLabel()
            drawerLayout.closeDrawers()
            loadWorkOrders()
        }

        // ============ فیلتر وضعیت ============
        rgStatusFilter.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbStatusAll -> currentStatus = ""
                R.id.rbStatusOpen -> currentStatus = "open"
                R.id.rbStatusArchived -> currentStatus = "archived"
            }
            updateFilterLabel()
            drawerLayout.closeDrawers()
            loadWorkOrders()
        }

        // ============ جستجو (با debounce) ============
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    currentSearch = s?.toString()?.trim() ?: ""
                    loadWorkOrders()
                }
                searchHandler.postDelayed(searchRunnable!!, 300)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // ============ پاک کردن فیلترها ============
        btnClearFilters.setOnClickListener {
            rgClassFilter.check(R.id.rbClassAll)
            rgStatusFilter.check(R.id.rbStatusAll)
            etSearch.setText("")
            // اینا خودکار listener رو صدا می‌زنن
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateFilterLabel() {
        val classText = when (currentClass) {
            "14" -> "کلاس ۱۴"
            "12" -> "کلاس ۱۲"
            "12P" -> "پیشگیرانه"
            "15" -> "کلاس ۱۵"
            else -> "همه"
        }
        val statusText = when (currentStatus) {
            "open" -> "جاری"
            "archived" -> "بایگانی"
            else -> "همه"
        }
        tvFilterLabel.text = "🔖 کلاس: $classText | وضعیت: $statusText"
    }

    // ============ بارگذاری (Offline-First) ============
    private fun loadWorkOrders() {
        // ۱. sync از سرور
        WorkOrderSyncer.syncWorkOrdersFromServer(this, currentUnit) { count ->
            Log.d("WorkOrderList", "Synced $count work orders")
        }

        // ۲. از Room بخون
        workOrdersJob?.cancel()
        workOrdersJob = lifecycleScope.launch {
            localRepository.getWorkOrders(
                unit = currentUnit,
                requestClass = currentClass,
                status = currentStatus,
                search = currentSearch
            ).collectLatest { list ->
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