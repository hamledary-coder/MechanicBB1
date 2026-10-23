package com.workshop.mechanicbb1.ui.reports

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.PersonnelReport
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.TimeUtils
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCalculator
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationRepository
import com.github.mikephil.charting.charts.BarChart
import com.workshop.mechanicbb1.utils.chart.ChartHelper
import com.github.mikephil.charting.formatter.ValueFormatter

@Suppress("DEPRECATION")
class PersonnelReportActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var containerReports: LinearLayout
    private val reportList = mutableListOf<PersonnelReport>()
    private val viewStates = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personnel_report)

        setupToolbar()
        initViews()
        loadReport()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "📊 گزارش کارکرد پرسنل"
        }
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        containerReports = findViewById(R.id.containerReports)
    }

    private fun loadReport() {
        showLoading(true)
        containerReports.removeAllViews()

        EvaluationCache.getCoefficients(this) { coefficients ->
            if (coefficients == null) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, "خطا در دریافت ضرایب از سرور", Toast.LENGTH_LONG).show()
                }
                return@getCoefficients
            }

            val repository = EvaluationRepository(this)

            repository.getAllTasks { tasks ->
                repository.getAllTaskLogs { allLogs ->
                    val indicators = EvaluationCalculator.aggregateIndicators(allLogs, tasks, coefficients)
                    val displayData = EvaluationCalculator.toDisplayFormat(indicators)

                    val reports = displayData.map { display ->
                        PersonnelReport(
                            userId = display.userId,
                            userName = display.userName,
                            rawResponsibility = display.rawResponsibility,
                            rawTechnical = display.rawTechnical,
                            rawPhysical = display.rawPhysical,
                            rawInspection = display.rawInspection,
                            finalResponsibility = display.finalResponsibility,
                            finalTechnical = display.finalTechnical,
                            finalPhysical = display.finalPhysical,
                            finalInspection = display.finalInspection
                        )
                    }

                    runOnUiThread {
                        showLoading(false)
                        reportList.clear()
                        reportList.addAll(reports)

                        if (reportList.isEmpty()) {
                            Toast.makeText(this, "هیچ داده‌ای یافت نشد", Toast.LENGTH_SHORT).show()
                        } else {
                            displaySections(reportList)
                        }
                    }
                }
            }
        }
    }

    private fun displaySections(reports: List<PersonnelReport>) {
        // 1. حجم کار با مسئولیت مستقیم
        addSectionWithToggle(
            sectionKey = "مسئولیت مستقیم",
            title = "📋 حجم کار با مسئولیت مستقیم",
            reports = reports,
            rawGetter = { it.rawResponsibility },
            finalGetter = { it.finalResponsibility }
        )

        // 2. حجم کار فیزیکی
        addSectionWithToggle(
            sectionKey = "کار فیزیکی",
            title = "💪 حجم کار فیزیکی",
            reports = reports,
            rawGetter = { it.rawPhysical },
            finalGetter = { it.finalPhysical }
        )

        // 3. حجم کار فنی
        addSectionWithToggle(
            sectionKey = "کار فنی",
            title = "🔧 حجم کار فنی",
            reports = reports,
            rawGetter = { it.rawTechnical },
            finalGetter = { it.finalTechnical }
        )

        // 4. کار بررسی (غیرفیزیکی)
        addSectionWithToggle(
            sectionKey = "کار بررسی",
            title = "🔍 کار بررسی (غیرفیزیکی)",
            reports = reports,
            rawGetter = { it.rawInspection },
            finalGetter = { it.finalInspection }
        )
    }

    // ========== فقط این نسخه رو نگه دار ==========
    @SuppressLint("SetTextI18n")
    private fun addSectionWithToggle(
        sectionKey: String,
        title: String,
        reports: List<PersonnelReport>,
        rawGetter: (PersonnelReport) -> String,
        finalGetter: (PersonnelReport) -> String
    ) {
        val filteredReports = reports
            .filter {
                val raw = rawGetter(it)
                raw != "00:00" && raw != "0:00" && raw != "0"
            }
            .sortedByDescending { TimeUtils.timeToDecimalHour(finalGetter(it)) }

        if (filteredReports.isEmpty()) return

        // ✅ گرفتن ID کاربر لاگین‌شده
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""

        // ✅ شماره‌گذاری بر اساس جایگاه واقعی (index)
        val maskedReports = filteredReports.mapIndexed { index, report ->
            if (report.userId == currentUserId) {
                report  // خودم → اسم واقعی
            } else {
                // جایگاه واقعی = index + 1
                val rank = index + 1
                report.copy(userName = "نفر ${ordinalPersian(rank)}")
            }
        }

        val isChartMode = viewStates[sectionKey] ?: true

        val sectionView = LayoutInflater.from(this)
            .inflate(R.layout.item_section_report, containerReports, false)

        val tvTitle = sectionView.findViewById<TextView>(R.id.tvSectionTitle)
        val btnToggle = sectionView.findViewById<Button>(R.id.btnToggleView)
        val recyclerView = sectionView.findViewById<RecyclerView>(R.id.rvSectionItems)
        val barChart = sectionView.findViewById<BarChart>(R.id.barChart)
        val tableHeader = sectionView.findViewById<LinearLayout>(R.id.tableHeader)

        tvTitle.text = "$title (${maskedReports.size} نفر)"

        val sectionColor = when {
            sectionKey.contains("مسئولیت") -> ContextCompat.getColor(this, R.color.primary_blue)
            sectionKey.contains("فیزیکی") -> ContextCompat.getColor(this, R.color.primary_green_dark)
            sectionKey.contains("فنی") -> ContextCompat.getColor(this, R.color.priority_high)
            sectionKey.contains("بررسی") -> ContextCompat.getColor(this, R.color.priority_medium)
            else -> ContextCompat.getColor(this, R.color.primary_blue)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = SectionAdapter(maskedReports, rawGetter, finalGetter)
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.setHasFixedSize(false)

        setupChart(barChart, maskedReports, rawGetter, finalGetter, sectionColor)

        applyMode(isChartMode, barChart, tableHeader, recyclerView, btnToggle)

        btnToggle.setOnClickListener {
            val newMode = !viewStates.getOrDefault(sectionKey, true)
            viewStates[sectionKey] = newMode
            applyMode(newMode, barChart, tableHeader, recyclerView, btnToggle)
        }

        containerReports.addView(sectionView)
    }

    private fun applyMode(
        isChartMode: Boolean,
        barChart: BarChart,
        tableHeader: LinearLayout,
        recyclerView: RecyclerView,
        btnToggle: Button
    ) {
        if (isChartMode) {
            barChart.visibility = View.VISIBLE
            tableHeader.visibility = View.GONE
            recyclerView.visibility = View.GONE
            btnToggle.text = "📋 جدول"
        } else {
            barChart.visibility = View.GONE
            tableHeader.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE
            btnToggle.text = "📊 نمودار"
        }
    }

    private fun setupChart(
        barChart: BarChart,
        reports: List<PersonnelReport>,
        rawGetter: (PersonnelReport) -> String,
        finalGetter: (PersonnelReport) -> String,
        sectionColor: Int
    ) {
        val names = reports.map { it.userName }
        val rawValues = reports.map { TimeUtils.timeToDecimalHour(rawGetter(it)).toFloat() }
        val finalValues = reports.map { TimeUtils.timeToDecimalHour(finalGetter(it)).toFloat() }

        ChartHelper.applyBaseSettings(barChart, sectionColor)

        val data = ChartHelper.buildGroupedData(names, rawValues, finalValues, sectionColor)
        barChart.data = data

        // ✅ گروه‌بندی
        val groupSpace = 0.3f
        val barSpace = 0.05f

        barChart.xAxis.axisMinimum = 0f
        barChart.xAxis.axisMaximum = 0f + barChart.barData.getGroupWidth(groupSpace, barSpace) * names.size
        barChart.groupBars(0f, groupSpace, barSpace)

        // ✅ XAxis formatter
        barChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index in names.indices) names[index] else ""
            }
        }

        barChart.axisLeft.axisMinimum = 0f
        barChart.invalidate()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun ordinalPersian(n: Int): String {
        return when (n) {
            1 -> "اول"
            2 -> "دوم"
            3 -> "سوم"
            4 -> "چهارم"
            5 -> "پنجم"
            6 -> "ششم"
            7 -> "هفتم"
            8 -> "هشتم"
            9 -> "نهم"
            10 -> "دهم"
            else -> n.toString()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    inner class SectionAdapter(
        private val reports: List<PersonnelReport>,
        private val rawGetter: (PersonnelReport) -> String,
        private val finalGetter: (PersonnelReport) -> String
    ) : RecyclerView.Adapter<SectionAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_section_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            holder.tvName.text = report.userName
            holder.tvRaw.text = rawGetter(report)
            holder.tvFinal.text = finalGetter(report)
        }

        override fun getItemCount(): Int = reports.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvName)
            val tvRaw: TextView = itemView.findViewById(R.id.tvRaw)
            val tvFinal: TextView = itemView.findViewById(R.id.tvFinal)
        }
    }
}