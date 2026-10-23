package com.workshop.mechanicbb1.ui.reports

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.ReportGroup
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.detail.TaskDetailActivity
import com.workshop.mechanicbb1.utils.DateUtils  // ← تغییر از ShamsiDateConverter به DateUtils
import com.workshop.mechanicbb1.utils.UserCache
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.util.Calendar
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.workshop.mechanicbb1.data.local.BossReportsLocalRepository

@Suppress("DEPRECATION")
class ReportsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: View

    // نویگیشن روزانه
    private lateinit var dayNavigationLayout: View
    private lateinit var tvDayRange: TextView
    private lateinit var btnPrevDay: View
    private lateinit var btnNextDay: View
    private var currentDay: Calendar = Calendar.getInstance()
    private var isFirstTimeEntering = true

    // نویگیشن هفتگی
    private lateinit var weekNavigationLayout: View
    private lateinit var tvWeekRange: TextView
    private lateinit var btnPrevWeek: View
    private lateinit var btnNextWeek: View
    private var currentWeekStart: Calendar = Calendar.getInstance()
    private var currentWeekEnd: Calendar = Calendar.getInstance()

    private val reportGroups = mutableListOf<ReportGroup>()
    private var currentUserId: String = ""
    private var currentTab: Int = 0
    private var isExporting: Boolean = false
    private var currentAdapter: RecyclerView.Adapter<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)
        initViews()
        setupToolbar()
        setupNavigations()
        setupTabLayout()
        setupRecyclerView()
        setupSwipeRefresh()

        // تنظیم اولیه تب روزانه با تاریخ امروز
        currentTab = 0
        resetDayToCurrent()
        updateNavigationVisibility()
        tabLayout.getTabAt(0)?.select()

        loadUserData()
    }

    override fun onResume() {
        super.onResume()
        if (isFirstTimeEntering) {
            isFirstTimeEntering = false
            if (currentTab == 0) {
                resetDayToCurrent()
                loadReports()
            }
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.recyclerViewReports)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        dayNavigationLayout = findViewById(R.id.dayNavigationLayout)
        tvDayRange = findViewById(R.id.tvDayRange)
        btnPrevDay = findViewById(R.id.btnPrevDay)
        btnNextDay = findViewById(R.id.btnNextDay)

        weekNavigationLayout = findViewById(R.id.weekNavigationLayout)
        tvWeekRange = findViewById(R.id.tvWeekRange)
        btnPrevWeek = findViewById(R.id.btnPrevWeek)
        btnNextWeek = findViewById(R.id.btnNextWeek)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        toolbar.navigationIcon?.setTint(Color.WHITE)

        val llExportPdf = toolbar.findViewById<View>(R.id.llExportPdf)
        llExportPdf.setOnClickListener {
            exportToPdf()
        }
    }

    private fun setupNavigations() {
        setupDayNavigation()
        setupWeekNavigation()
    }

    private fun setupDayNavigation() {
        currentDay = Calendar.getInstance()
        updateDayRangeDisplay()

        btnPrevDay.setOnClickListener {
            currentDay.add(Calendar.DAY_OF_YEAR, -1)
            updateDayRangeDisplay()
            loadReports()
        }

        btnNextDay.setOnClickListener {
            val today = Calendar.getInstance()
            val nextDay = currentDay.clone() as Calendar
            nextDay.add(Calendar.DAY_OF_YEAR, 1)

            if (nextDay.before(today) || nextDay == today) {
                currentDay.add(Calendar.DAY_OF_YEAR, 1)
                updateDayRangeDisplay()
                loadReports()
            } else {
                Toast.makeText(this, "نمی‌توانید به روز آینده بروید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ فقط یک تابع updateDayRangeDisplay وجود داره
    @SuppressLint("SetTextI18n")
    private fun updateDayRangeDisplay() {
        val dayName = DateUtils.getDayName(currentDay)  // ← تغییر
        val shamsiDate = DateUtils.gregorianToShamsi(currentDay)  // ← تغییر
        tvDayRange.text = "$dayName $shamsiDate"
    }

    private fun setupWeekNavigation() {
        resetWeekToCurrent()

        btnPrevWeek.setOnClickListener {
            currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1)
            currentWeekEnd.add(Calendar.WEEK_OF_YEAR, -1)
            updateWeekRangeDisplay()
            loadReports()
        }

        btnNextWeek.setOnClickListener {
            val today = Calendar.getInstance()
            val nextWeekStart = currentWeekStart.clone() as Calendar
            nextWeekStart.add(Calendar.WEEK_OF_YEAR, 1)

            if (nextWeekStart.before(today) || nextWeekStart == today) {
                currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1)
                currentWeekEnd.add(Calendar.WEEK_OF_YEAR, 1)
                updateWeekRangeDisplay()
                loadReports()
            } else {
                Toast.makeText(this, "نمی‌توانید به هفته آینده بروید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateWeekRangeDisplay() {
        val startShamsi = DateUtils.gregorianToShamsi(currentWeekStart)  // ← تغییر
        val endShamsi = DateUtils.gregorianToShamsi(currentWeekEnd)  // ← تغییر
        tvWeekRange.text = "$startShamsi - $endShamsi"
    }

    private fun resetWeekToCurrent() {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        currentWeekStart = calendar.clone() as Calendar

        val endCalendar = calendar.clone() as Calendar
        endCalendar.add(Calendar.DAY_OF_YEAR, 6)
        currentWeekEnd = endCalendar.clone() as Calendar

        updateWeekRangeDisplay()
    }

    private fun setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("روزانه"))
        tabLayout.addTab(tabLayout.newTab().setText("هفتگی"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateNavigationVisibility()
                loadReports()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateNavigationVisibility() {
        when (currentTab) {
            0 -> {
                dayNavigationLayout.visibility = View.VISIBLE
                weekNavigationLayout.visibility = View.GONE
                updateDayRangeDisplay()
            }
            1 -> {
                dayNavigationLayout.visibility = View.GONE
                weekNavigationLayout.visibility = View.VISIBLE
                resetWeekToCurrent()
            }
        }
    }

    private fun resetDayToCurrent() {
        currentDay = Calendar.getInstance()
        updateDayRangeDisplay()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadReports()
        }
    }

    private fun loadUserData() {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "خطا در دریافت اطلاعات کاربر", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        UserCache.loadAllUsers {
            runOnUiThread {
                loadReports()
            }
        }
    }

    private fun getDateRange(): Pair<String, String> {

        when (currentTab) {
            0 -> {
                val startShamsi = DateUtils.gregorianToShamsi(currentDay)  // ← تغییر
                return Pair(startShamsi, startShamsi)
            }
            else -> {
                val startShamsi = DateUtils.gregorianToShamsi(currentWeekStart)  // ← تغییر
                val endShamsi = DateUtils.gregorianToShamsi(currentWeekEnd)  // ← تغییر
                return Pair(startShamsi, endShamsi)
            }
        }
    }

    private fun loadReports() {
        val (startDate, endDate) = getDateRange()

        showLoading(true)
        hideEmptyState()

        lifecycleScope.launch {
            val repo = BossReportsLocalRepository(this@ReportsActivity)
            repo.getGroupedReports(startDate, endDate).collectLatest { groups ->
                showLoading(false)
                swipeRefreshLayout.isRefreshing = false
                reportGroups.clear()
                reportGroups.addAll(groups)
                setupAdapter()

                if (reportGroups.isEmpty()) {
                    showEmptyState()
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupAdapter() {
        val selectedDate = when (currentTab) {
            0 -> DateUtils.gregorianToShamsi(currentDay)  // ← تغییر
            else -> ""
        }

        currentAdapter = when (currentTab) {
            0 -> DailyReportsAdapter(
                reportGroups = reportGroups,
                onItemClick = { reportGroup ->
                    val intent = Intent(this, TaskDetailActivity::class.java).apply {
                        putExtra("TASK_ID", reportGroup.taskId)
                        putExtra("TITLE", reportGroup.taskTitle)
                        putExtra("RESPONSIBLE", reportGroup.taskResponsible)
                        putExtra("ASSIGNED_TO", reportGroup.taskAssignedTo)
                        putExtra("UNIT", reportGroup.taskUnit)
                        putExtra("URGENCY", reportGroup.taskUrgency)
                        putExtra("STATUS", reportGroup.taskStatus)
                    }
                    startActivity(intent)
                },
                selectedDate = selectedDate
            )
            else -> WeeklyReportsAdapter(reportGroups) { reportGroup ->
                val intent = Intent(this, TaskDetailActivity::class.java).apply {
                    putExtra("TASK_ID", reportGroup.taskId)
                    putExtra("TITLE", reportGroup.taskTitle)
                    putExtra("RESPONSIBLE", reportGroup.taskResponsible)
                    putExtra("ASSIGNED_TO", reportGroup.taskAssignedTo)
                    putExtra("UNIT", reportGroup.taskUnit)
                    putExtra("URGENCY", reportGroup.taskUrgency)
                    putExtra("STATUS", reportGroup.taskStatus)
                }
                startActivity(intent)
            }
        }
        recyclerView.adapter = currentAdapter
        currentAdapter?.notifyDataSetChanged()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmptyState() {
        emptyStateLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun getCurrentDate(): String {
        return DateUtils.gregorianToShamsi()  // ← تغییر
    }




    // ========== توابع PDF (ساده‌شده) ==========
    private fun exportToPdf() {
        if (reportGroups.isEmpty()) {
            Toast.makeText(this, "هیچ داده‌ای برای خروجی وجود ندارد", Toast.LENGTH_SHORT).show()
            return
        }

        if (isExporting) {
            Toast.makeText(this, "در حال تولید PDF، لطفاً صبر کنید...", Toast.LENGTH_SHORT).show()
            return
        }

        isExporting = true
        val llExportPdf = toolbar.findViewById<View>(R.id.llExportPdf)
        llExportPdf.isEnabled = false
        llExportPdf.alpha = 0.5f

        // دریافت بازه زمانی
        val (startDate, endDate) = getDateRange()

        // تولید PDF در پس‌زمینه
        Thread {
            val pdfFile = ReportsPdfGenerator.generateReportPdf(
                context = this,
                reportGroups = reportGroups,
                currentTab = currentTab,
                startDate = startDate,
                endDate = endDate
            )

            runOnUiThread {
                isExporting = false
                llExportPdf.isEnabled = true
                llExportPdf.alpha = 1.0f

                if (pdfFile != null) {
                    // ✅ نمایش دیالوگ انتخاب
                    showPdfActionDialog(pdfFile)
                } else {
                    Toast.makeText(this, "خطا در تولید PDF", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ✅ دیالوگ انتخاب عملکرد
    private fun showPdfActionDialog(pdfFile: File) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pdf_actions, null)
        val btnView = dialogView.findViewById<Button>(R.id.btnViewPdf)
        val btnShare = dialogView.findViewById<Button>(R.id.btnSharePdf)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelPdf)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // نمایش PDF
        btnView.setOnClickListener {
            dialog.dismiss()
            viewPdf(pdfFile)
        }

        // اشتراک‌گذاری PDF
        btnShare.setOnClickListener {
            dialog.dismiss()
            ReportsPdfGenerator.sharePdf(this, pdfFile)
        }

        // انصراف
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ✅ نمایش PDF با Intent
    // ✅ نمایش PDF با انتخاب برنامه از لیست
    private fun viewPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // ✅ نمایش لیست برنامه‌ها (مثل حالت اشتراک‌گذاری)
            startActivity(Intent.createChooser(intent, "انتخاب برنامه برای نمایش PDF"))

        } catch (e: Exception) {
            Toast.makeText(this, "خطا در نمایش PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}