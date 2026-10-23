package com.workshop.mechanicbb1.ui.task.evaluation

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.utils.UserCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCache
import com.workshop.mechanicbb1.utils.evaluation.EvaluationCalculator
import com.workshop.mechanicbb1.utils.evaluation.EvaluationModels
import androidx.lifecycle.lifecycleScope
import com.workshop.mechanicbb1.data.local.ReportLocalRepository
import com.workshop.mechanicbb1.data.sync.OutboxHelper
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class EvaluationActivity : AppCompatActivity() {

    // ===== ویوها =====
    private lateinit var tvTaskTitle: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvActionDescription: TextView

    private lateinit var rgWorkType: RadioGroup
    private lateinit var rbFixedEquipment: RadioButton
    private lateinit var rbRotatingEquipment: RadioButton
    private lateinit var rbInspection: RadioButton

    private lateinit var cardTeam: View
    private lateinit var rvTeamMembers: RecyclerView

    private lateinit var cardEvaluation: View
    private lateinit var tvEvaluationTitle: TextView
    private lateinit var sliderEvaluation: com.google.android.material.slider.Slider
    private lateinit var tvSliderMinLabel: TextView
    private lateinit var tvSliderMidLabel: TextView
    private lateinit var tvSliderMaxLabel: TextView
    private lateinit var tvSelectedValue: TextView

    private lateinit var seekBarHeat: SeekBar
    private lateinit var seekBarPollution: SeekBar
    private lateinit var tvHeatValue: TextView
    private lateinit var tvPollutionValue: TextView

    private lateinit var etNotes: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnSkip: Button

    // ===== ویوهای اسلایدر فنی =====
    private lateinit var layoutTechnical: LinearLayout
    private lateinit var sliderTechnical: com.google.android.material.slider.Slider
    private lateinit var tvTechMinLabel: TextView
    private lateinit var tvTechMidLabel: TextView
    private lateinit var tvTechMaxLabel: TextView
    private lateinit var tvTechSelected: TextView

    // ===== داده‌ها =====
    private var taskId: String = ""
    private var logId: String = ""
    private var taskTitle: String = ""
    private var taskDate: String = ""
    private var taskStartTime: String = ""
    private var taskEndTime: String = ""
    private var taskDescription: String = ""
    private var assignedUsers: String = ""
    private var durationMinutes: Int = 0
    private var isEditMode = false
    private var existingPhysicalDifficulty = 0
    private var existingTechnicalComplexity = 0
    private var existingHeatLevel = 30
    private var existingPollutionLevel = 0
    private var existingWorkType = "1"
    private var existingNotes = ""

    private var selectedWorkType: String = "1"
    private var selectedEvaluationValue: Int = 1
    private var technicalComplexityValue: Int = 1

    // ===== ضرایب از کش =====
    private var coefficients: EvaluationModels.Coefficients? = null

    private var teamMembers = mutableListOf<TeamMember>()

    companion object {
        private const val WORK_TYPE_FIXED = "1"
        private const val WORK_TYPE_ROTATING = "2"
        private const val WORK_TYPE_INSPECTION = "3"
    }

    data class TeamMember(
        val userId: String,
        val name: String,
        val rawTimeMinutes: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evaluation)

        // ===== اول initViews رو صدا بزن =====
        setupToolbar()
        getIntentData()
        initViews()  // ← اینجا rvTeamMembers مقداردهی میشه
        loadTeamMembers()


        // ===== بعد ضرایب رو بگیر =====
        EvaluationCache.getCoefficients(this) { coeffs ->
            coefficients = coeffs
            if (coefficients != null) {
                updateTeamTable()
            } else {
                Toast.makeText(this, "خطا در دریافت ضرایب از سرور", Toast.LENGTH_SHORT).show()
            }
        }

        checkExistingEvaluation()
        setupListeners()
        updateUI()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "🔍 ارزیابی کار"
        }
    }

    private fun getIntentData() {
        taskId = intent.getStringExtra("TASK_ID") ?: ""
        logId = intent.getStringExtra("LOG_ID") ?: ""
        taskTitle = intent.getStringExtra("TASK_TITLE") ?: ""
        taskDate = intent.getStringExtra("TASK_DATE") ?: ""
        taskStartTime = intent.getStringExtra("TASK_START_TIME") ?: ""
        taskEndTime = intent.getStringExtra("TASK_END_TIME") ?: ""
        taskDescription = intent.getStringExtra("TASK_DESCRIPTION") ?: ""
        assignedUsers = intent.getStringExtra("ASSIGNED_USERS") ?: ""
        durationMinutes = intent.getIntExtra("DURATION_MINUTES", 0)
    }

    @SuppressLint("SetTextI18n")
    private fun initViews() {
        layoutTechnical = findViewById(R.id.layoutTechnical)
        sliderTechnical = findViewById(R.id.sliderTechnical)
        tvTechMinLabel = findViewById(R.id.tvTechMinLabel)
        tvTechMidLabel = findViewById(R.id.tvTechMidLabel)
        tvTechMaxLabel = findViewById(R.id.tvTechMaxLabel)
        tvTechSelected = findViewById(R.id.tvTechSelected)

        tvTaskTitle = findViewById(R.id.tvTaskTitle)
        tvDate = findViewById(R.id.tvDate)
        tvTime = findViewById(R.id.tvTime)
        tvDuration = findViewById(R.id.tvDuration)
        tvActionDescription = findViewById(R.id.tvActionDescription)

        rgWorkType = findViewById(R.id.rgWorkType)
        rbFixedEquipment = findViewById(R.id.rbFixedEquipment)
        rbRotatingEquipment = findViewById(R.id.rbRotatingEquipment)
        rbInspection = findViewById(R.id.rbInspection)

        cardTeam = findViewById(R.id.cardTeam)
        rvTeamMembers = findViewById(R.id.rvTeamMembers)

        cardEvaluation = findViewById(R.id.cardEvaluation)
        tvEvaluationTitle = findViewById(R.id.tvEvaluationTitle)
        sliderEvaluation = findViewById(R.id.sliderEvaluation)
        tvSliderMinLabel = findViewById(R.id.tvSliderMinLabel)
        tvSliderMidLabel = findViewById(R.id.tvSliderMidLabel)
        tvSliderMaxLabel = findViewById(R.id.tvSliderMaxLabel)
        tvSelectedValue = findViewById(R.id.tvSelectedValue)

        seekBarHeat = findViewById(R.id.seekBarHeat)
        seekBarPollution = findViewById(R.id.seekBarPollution)
        tvHeatValue = findViewById(R.id.tvHeatValue)
        tvPollutionValue = findViewById(R.id.tvPollutionValue)

        etNotes = findViewById(R.id.etNotes)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnSkip = findViewById(R.id.btnSkip)

        tvTaskTitle.text = taskTitle
        tvDate.text = "تاریخ: $taskDate"
        tvTime.text = "ساعت: $taskStartTime - $taskEndTime"
        tvDuration.text = "⏱ مدت زمان: ${EvaluationCalculator.formatDuration(durationMinutes)}"
        tvActionDescription.text = "شرح اقدام: $taskDescription"
    }

    private fun setupListeners() {
        rgWorkType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbFixedEquipment -> {
                    selectedWorkType = WORK_TYPE_FIXED
                    showPhysicalEvaluation()
                }
                R.id.rbRotatingEquipment -> {
                    selectedWorkType = WORK_TYPE_ROTATING
                    showTechnicalEvaluation()
                }
                R.id.rbInspection -> {
                    selectedWorkType = WORK_TYPE_INSPECTION
                    showInspectionInfo()
                }
            }
            updateTeamTable()
        }

        sliderEvaluation.addOnChangeListener { _, value, _ ->
            selectedEvaluationValue = value.toInt()
            updateSliderLabels()
            updateTeamTable()
        }

        sliderTechnical.addOnChangeListener { _, value, _ ->
            technicalComplexityValue = value.toInt()
            updateTechnicalSliderLabels()
            updateTeamTable()
        }

        seekBarHeat.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val heat = 30 + progress
                tvHeatValue.text = "$heat درجه"
                updateTeamTable()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarPollution.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPollutionValue.text = "$progress ppm"
                updateTeamTable()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSubmit.setOnClickListener { submitEvaluation() }
        btnSkip.setOnClickListener { finish() }
    }

    private fun loadTeamMembers() {
        Log.d("Evaluation", "=== loadTeamMembers START ===")
        Log.d("Evaluation", "durationMinutes: $durationMinutes")
        Log.d("Evaluation", "assignedUsers: '$assignedUsers'")

        if (assignedUsers.isEmpty() || assignedUsers == "0" || assignedUsers == "null") {
            cardTeam.visibility = View.GONE
            return
        }

        val userIds = assignedUsers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        Log.d("Evaluation", "userIds after split: $userIds")

        if (userIds.isEmpty()) {
            cardTeam.visibility = View.GONE
            return
        }

        cardTeam.visibility = View.VISIBLE

        val perPersonMinutes = durationMinutes
        Log.d("Evaluation", "perPersonMinutes: $perPersonMinutes")

        teamMembers.clear()
        for (userId in userIds) {
            val name = UserCache.getName(userId)
            Log.d("Evaluation", "userId: '$userId' -> name: '$name'")
            teamMembers.add(TeamMember(userId, name, perPersonMinutes))
            Log.d("Evaluation", "✅ Added: $userId -> $name")
        }

        Log.d("Evaluation", "teamMembers size: ${teamMembers.size}")
        Log.d("Evaluation", "=== loadTeamMembers END ===")

        setupTeamAdapter()
        updateTeamTable()
    }

    private fun setupTeamAdapter() {
        rvTeamMembers.layoutManager = LinearLayoutManager(this)
        rvTeamMembers.setHasFixedSize(true)
        rvTeamMembers.isNestedScrollingEnabled = true
        rvTeamMembers.adapter = TeamAdapter(teamMembers)

        rvTeamMembers.post {
            rvTeamMembers.requestLayout()
            rvTeamMembers.invalidate()
        }
    }

    // ============================================================
    // محاسبات با ماژول ارزیابی
    // ============================================================

    private fun updateTeamTable() {
        val adapter = rvTeamMembers.adapter as? TeamAdapter
        if (adapter == null) return
        if (coefficients == null) {
            Log.d("Evaluation", "Coefficients not loaded yet")
            return
        }

        val coeffs = coefficients!!

        // دریافت مقادیر از ویوها
        val physicalValue = selectedEvaluationValue
        val technicalValue = technicalComplexityValue
        val heatProgress = seekBarHeat.progress
        val pollutionProgress = seekBarPollution.progress

        // محاسبه ضرایب با استفاده از EvaluationCalculator
        val physicalCoeff = EvaluationCalculator.calculateCoefficient(physicalValue, 5, coeffs.physicalDifficulty)
        val technicalCoeff = EvaluationCalculator.calculateCoefficient(technicalValue, 5, coeffs.technicalComplexity)
        val tempCoeff = EvaluationCalculator.calculateCoefficient(heatProgress, 25, coeffs.temperature)
        val pollutionCoeff = EvaluationCalculator.calculateCoefficient(pollutionProgress, 100, coeffs.pollution)

        // ضریب نهایی برای هر نفر
        val coefficientsList = teamMembers.mapIndexed { index, _ ->
            when (selectedWorkType) {
                WORK_TYPE_FIXED -> {
                    physicalCoeff + tempCoeff + pollutionCoeff
                }
                WORK_TYPE_ROTATING -> {
                    if (index == 0) {
                        physicalCoeff + technicalCoeff + tempCoeff + pollutionCoeff
                    } else {
                        physicalCoeff + tempCoeff + pollutionCoeff
                    }
                }
                else -> 0.0
            }
        }

        adapter.updateCoefficients(coefficientsList)
    }

    private fun showPhysicalEvaluation() {
        cardEvaluation.visibility = View.VISIBLE
        layoutTechnical.visibility = View.GONE

        tvEvaluationTitle.text = "💪 میزان سختی کار فیزیکی"
        tvSliderMinLabel.text = "خیلی سبک"
        tvSliderMidLabel.text = "متوسط"
        tvSliderMaxLabel.text = "خیلی سنگین"

        if (selectedEvaluationValue == 0) {
            selectedEvaluationValue = 1
            sliderEvaluation.value = 1f
        } else {
            sliderEvaluation.value = selectedEvaluationValue.toFloat()
        }

        updateSliderLabels()
        updateTeamTable()
    }

    private fun showTechnicalEvaluation() {
        cardEvaluation.visibility = View.VISIBLE
        layoutTechnical.visibility = View.VISIBLE

        tvTechMinLabel.text = "خیلی کم"
        tvTechMidLabel.text = "متوسط"
        tvTechMaxLabel.text = "خیلی زیاد"
        if (technicalComplexityValue <= 0) {
            technicalComplexityValue = 1
            sliderTechnical.value = 1f
        } else {
            sliderTechnical.value = technicalComplexityValue.toFloat()
        }
        updateTechnicalSliderLabels()

        tvEvaluationTitle.text = "💪 میزان سختی کار فیزیکی"
        tvSliderMinLabel.text = "خیلی سبک"
        tvSliderMidLabel.text = "متوسط"
        tvSliderMaxLabel.text = "خیلی سنگین"
        if (selectedEvaluationValue <= 0) {
            selectedEvaluationValue = 1
            sliderEvaluation.value = 1f
        } else {
            sliderEvaluation.value = selectedEvaluationValue.toFloat()
        }
        updateSliderLabels()

        updateTeamTable()
    }

    private fun showInspectionInfo() {
        cardEvaluation.visibility = View.VISIBLE
        layoutTechnical.visibility = View.GONE
        tvEvaluationTitle.text = "ℹ️ این کار از نوع بررسی است"
        sliderEvaluation.visibility = View.GONE
        tvSliderMinLabel.visibility = View.GONE
        tvSliderMidLabel.visibility = View.GONE
        tvSliderMaxLabel.visibility = View.GONE
        tvSelectedValue.text = "نیازی به ارزیابی تکمیلی ندارد"
        selectedEvaluationValue = 0
        technicalComplexityValue = 0
        updateTeamTable()
    }

    @SuppressLint("SetTextI18n")
    private fun updateSliderLabels() {
        val value = selectedEvaluationValue
        val labels = when {
            value == 1 -> "خیلی سبک"
            value == 2 -> "سبک"
            value == 3 -> "متوسط"
            value == 4 -> "سنگین"
            value == 5 -> "خیلی سنگین"
            else -> "متوسط"
        }
        tvSelectedValue.text = "مقدار انتخاب شده: $labels"
        sliderEvaluation.visibility = View.VISIBLE
        tvSliderMinLabel.visibility = View.VISIBLE
        tvSliderMidLabel.visibility = View.VISIBLE
        tvSliderMaxLabel.visibility = View.VISIBLE
    }

    @SuppressLint("SetTextI18n")
    private fun updateTechnicalSliderLabels() {
        val value = technicalComplexityValue
        val labels = when {
            value == 1 -> "خیلی کم"
            value == 2 -> "کم"
            value == 3 -> "متوسط"
            value == 4 -> "زیاد"
            value == 5 -> "خیلی زیاد"
            else -> "متوسط"
        }
        tvTechSelected.text = "مقدار انتخاب شده: $labels"
    }

    private fun updateUI() {
        if (existingPhysicalDifficulty == 0 && existingTechnicalComplexity == 0) {
            rbFixedEquipment.isChecked = true
            selectedWorkType = WORK_TYPE_FIXED
            selectedEvaluationValue = 1
            sliderEvaluation.value = 1f
            showPhysicalEvaluation()
        }
    }

    private fun submitEvaluation() {
        val heat = 30 + seekBarHeat.progress
        val pollution = seekBarPollution.progress
        val notes = etNotes.text.toString().trim()

        val physicalDifficulty = when (selectedWorkType) {
            WORK_TYPE_FIXED -> selectedEvaluationValue
            WORK_TYPE_ROTATING -> selectedEvaluationValue
            else -> 0
        }

        val technicalComplexity = when (selectedWorkType) {
            WORK_TYPE_ROTATING -> technicalComplexityValue
            else -> 0
        }

        lifecycleScope.launch {
            try {
                val reportLocalRepo = ReportLocalRepository(this@EvaluationActivity)

                // ۱. آپدیت توی Room
                reportLocalRepo.updateReportEvaluation(
                    reportId = logId,
                    notes = notes,
                    heatLevel = heat,
                    pollutionLevel = pollution,
                    workType = selectedWorkType,
                    physicalDifficulty = physicalDifficulty,
                    technicalComplexity = technicalComplexity
                )

                // ۲. Outbox
                OutboxHelper.enqueueUpdateEvaluation(
                    context = this@EvaluationActivity,
                    logId = logId,
                    notes = notes,
                    heatLevel = heat,
                    pollutionLevel = pollution,
                    workType = selectedWorkType,
                    physicalDifficulty = physicalDifficulty,
                    technicalComplexity = technicalComplexity
                )

                // ۳. Worker
                ReportSyncWorker.enqueue(this@EvaluationActivity)

                // ۴. UI
                Toast.makeText(this@EvaluationActivity, "✅ ارزیابی ثبت شد", Toast.LENGTH_SHORT).show()
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@EvaluationActivity, "❌ خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun checkExistingEvaluation() {
        lifecycleScope.launch {
            try {
                val reportLocalRepo = ReportLocalRepository(this@EvaluationActivity)
                val report = reportLocalRepo.getReportById(logId)

                if (report != null) {
                    existingPhysicalDifficulty = report.physicalDifficulty
                    existingTechnicalComplexity = report.technicalComplexity
                    existingHeatLevel = report.heatLevel
                    existingPollutionLevel = report.pollutionLevel
                    existingWorkType = report.workType
                    existingNotes = report.notes

                    if (existingPhysicalDifficulty > 0 || existingTechnicalComplexity > 0) {
                        isEditMode = true
                        supportActionBar?.title = "✏️ ویرایش ارزیابی"
                        btnSubmit.text = "✅ بروزرسانی ارزیابی"
                    }

                    applyExistingData()
                }
            } catch (e: Exception) {
                Log.e("Evaluation", "Error: ${e.message}")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun applyExistingData() {
        when (existingWorkType) {
            "1" -> {
                rbFixedEquipment.isChecked = true
                selectedWorkType = WORK_TYPE_FIXED
                if (existingPhysicalDifficulty > 0) {
                    selectedEvaluationValue = existingPhysicalDifficulty
                }
                showPhysicalEvaluation()
            }
            "2" -> {
                rbRotatingEquipment.isChecked = true
                selectedWorkType = WORK_TYPE_ROTATING
                technicalComplexityValue = if (existingTechnicalComplexity > 0) existingTechnicalComplexity else 1
                selectedEvaluationValue = if (existingPhysicalDifficulty > 0) existingPhysicalDifficulty else 1
                showTechnicalEvaluation()
            }
            "3" -> {
                rbInspection.isChecked = true
                selectedWorkType = WORK_TYPE_INSPECTION
                showInspectionInfo()
            }
        }

        seekBarHeat.progress = existingHeatLevel - 30
        tvHeatValue.text = "$existingHeatLevel درجه"

        seekBarPollution.progress = existingPollutionLevel
        tvPollutionValue.text = "$existingPollutionLevel ppm"

        etNotes.setText(existingNotes)

        updateTeamTable()
    }

    // ===== Adapter =====
    inner class TeamAdapter(private val members: List<TeamMember>) :
        RecyclerView.Adapter<TeamAdapter.ViewHolder>() {

        private var coefficients: List<Double> = members.map { 0.0 }

        @SuppressLint("NotifyDataSetChanged")
        fun updateCoefficients(newCoefficients: List<Double>) {
            this.coefficients = newCoefficients
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_evaluation_team, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val member = members[position]
            val coeff = coefficients.getOrElse(position) { 0.0 }
            val finalMinutes = (member.rawTimeMinutes * (1 + coeff)).toInt()

            holder.tvName.text = member.name
            holder.tvRawTime.text = EvaluationCalculator.formatDuration(member.rawTimeMinutes)
            holder.tvFinalTime.text = EvaluationCalculator.formatDuration(finalMinutes)
            holder.tvPercentage.text = "+${(coeff * 100).toInt()}%"
        }

        override fun getItemCount(): Int = members.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvMemberName)
            val tvRawTime: TextView = itemView.findViewById(R.id.tvRawTime)
            val tvFinalTime: TextView = itemView.findViewById(R.id.tvFinalTime)
            val tvPercentage: TextView = itemView.findViewById(R.id.tvPercentage)
        }
    }
}