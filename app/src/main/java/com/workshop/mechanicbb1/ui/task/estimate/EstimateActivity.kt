package com.workshop.mechanicbb1.ui.task.estimate

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.repository.TaskEstimateLocalRepository
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class EstimateActivity : AppCompatActivity() {

    // ===== ویوها =====
    private lateinit var tvTaskTitle: TextView
    private lateinit var tvTaskDate: TextView
    private lateinit var tvTaskDescription: TextView

    private lateinit var etManpower: EditText
    private lateinit var etParts: EditText
    private lateinit var etEstimatedHours: EditText
    private lateinit var tvTotalHours: TextView
    private lateinit var tvManHours: TextView

    private lateinit var rgWorkType: RadioGroup
    private lateinit var rbFixedEquipment: RadioButton
    private lateinit var rbRotatingEquipment: RadioButton
    private lateinit var rbInspection: RadioButton

    private lateinit var cardEvaluation: View
    private lateinit var tvEvaluationTitle: TextView
    private lateinit var sliderEvaluation: Slider
    private lateinit var tvSliderMinLabel: TextView
    private lateinit var tvSliderMidLabel: TextView
    private lateinit var tvSliderMaxLabel: TextView
    private lateinit var tvSelectedValue: TextView

    private lateinit var layoutTechnical: LinearLayout
    private lateinit var sliderTechnical: Slider
    private lateinit var tvTechMinLabel: TextView
    private lateinit var tvTechMidLabel: TextView
    private lateinit var tvTechMaxLabel: TextView
    private lateinit var tvTechSelected: TextView

    private lateinit var seekBarHeat: SeekBar
    private lateinit var seekBarPollution: SeekBar
    private lateinit var tvHeatValue: TextView
    private lateinit var tvPollutionValue: TextView

    private lateinit var etNotes: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    // ===== داده‌ها =====
    private var taskId: String = ""
    private var taskTitle: String = ""
    private var taskDate: String = ""
    private var taskDescription: String = ""

    private var selectedWorkType: String = "1"
    private var selectedEvaluationValue: Int = 1
    private var technicalComplexityValue: Int = 1

    private lateinit var taskLocalRepository: TaskLocalRepository
    private lateinit var estimateLocalRepository: TaskEstimateLocalRepository

    companion object {
        private const val WORK_TYPE_FIXED = "1"
        private const val WORK_TYPE_ROTATING = "2"
        private const val WORK_TYPE_INSPECTION = "3"
        private const val TAG = "EstimateActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // پدینگ بالا و پایین رو به اندازه نوارها اضافه میکنیم
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            // به سیستم میگیم که این Insets رو مصرف کردیم و به بچه‌ها نده
            WindowInsetsCompat.CONSUMED
        }
        setContentView(R.layout.estimate_activity)

        setupToolbar()
        getIntentData()
        initViews()
        setupListeners()
        loadTaskInfo()
        updateUI()
        updateComputedValues()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "📋 برآورد اولیه"
        }
    }

    private fun getIntentData() {
        taskId = intent.getStringExtra("TASK_ID") ?: ""
        taskTitle = intent.getStringExtra("TASK_TITLE") ?: ""
        taskDate = intent.getStringExtra("TASK_DATE") ?: ""
        taskDescription = intent.getStringExtra("TASK_DESC") ?: ""
    }

    private fun initViews() {
        taskLocalRepository = TaskLocalRepository(this)
        estimateLocalRepository = TaskEstimateLocalRepository(this)

        tvTaskTitle = findViewById(R.id.tvTaskTitle)
        tvTaskDate = findViewById(R.id.tvTaskDate)
        tvTaskDescription = findViewById(R.id.tvTaskDescription)

        etManpower = findViewById(R.id.etManpower)
        etParts = findViewById(R.id.etParts)
        etEstimatedHours = findViewById(R.id.etEstimatedHours)
        tvTotalHours = findViewById(R.id.tvTotalHours)
        tvManHours = findViewById(R.id.tvManHours)

        rgWorkType = findViewById(R.id.rgWorkType)
        rbFixedEquipment = findViewById(R.id.rbFixedEquipment)
        rbRotatingEquipment = findViewById(R.id.rbRotatingEquipment)
        rbInspection = findViewById(R.id.rbInspection)

        cardEvaluation = findViewById(R.id.cardEvaluation)
        tvEvaluationTitle = findViewById(R.id.tvEvaluationTitle)
        sliderEvaluation = findViewById(R.id.sliderEvaluation)
        tvSliderMinLabel = findViewById(R.id.tvSliderMinLabel)
        tvSliderMidLabel = findViewById(R.id.tvSliderMidLabel)
        tvSliderMaxLabel = findViewById(R.id.tvSliderMaxLabel)
        tvSelectedValue = findViewById(R.id.tvSelectedValue)

        layoutTechnical = findViewById(R.id.layoutTechnical)
        sliderTechnical = findViewById(R.id.sliderTechnical)
        tvTechMinLabel = findViewById(R.id.tvTechMinLabel)
        tvTechMidLabel = findViewById(R.id.tvTechMidLabel)
        tvTechMaxLabel = findViewById(R.id.tvTechMaxLabel)
        tvTechSelected = findViewById(R.id.tvTechSelected)

        seekBarHeat = findViewById(R.id.seekBarHeat)
        seekBarPollution = findViewById(R.id.seekBarPollution)
        tvHeatValue = findViewById(R.id.tvHeatValue)
        tvPollutionValue = findViewById(R.id.tvPollutionValue)

        etNotes = findViewById(R.id.etNotes)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnCancel = findViewById(R.id.btnCancel)
    }

    @SuppressLint("SetTextI18n")
    private fun loadTaskInfo() {
        tvTaskTitle.text = taskTitle
        tvTaskDate.text = "📅 تاریخ ایجاد: $taskDate"
        tvTaskDescription.text = "📄 توضیحات: $taskDescription"

        // اگه taskTitle از Intent نیومد، از Room بگیر
        if (taskTitle.isEmpty() && taskId.isNotEmpty()) {
            lifecycleScope.launch {
                taskLocalRepository.getTaskById(taskId).collect { task ->
                    if (task != null) {
                        tvTaskTitle.text = task.title
                        tvTaskDate.text = "📅 تاریخ ایجاد: ${task.createDate}"
                        tvTaskDescription.text = "📄 توضیحات: ${task.description}"
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        // نوع کار
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
        }

        // سختی فیزیکی
        sliderEvaluation.addOnChangeListener { _, value, _ ->
            selectedEvaluationValue = value.toInt()
            updateSliderLabels()
        }

        // پیچیدگی فنی
        sliderTechnical.addOnChangeListener { _, value, _ ->
            technicalComplexityValue = value.toInt()
            updateTechnicalSliderLabels()
        }

        // دما
        seekBarHeat.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val heat = 30 + progress
                tvHeatValue.text = "$heat درجه"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // آلودگی
        seekBarPollution.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPollutionValue.text = "$progress ppm"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // منابع — محاسبه خودکار
        etManpower.doAfterTextChanged { updateComputedValues() }
        etParts.doAfterTextChanged { updateComputedValues() }
        etEstimatedHours.doAfterTextChanged { updateComputedValues() }

        btnSubmit.setOnClickListener { submitEstimate() }
        btnCancel.setOnClickListener { finish() }
    }

    @SuppressLint("SetTextI18n")
    private fun updateComputedValues() {
        val manpower = etManpower.text.toString().toIntOrNull() ?: 1
        val parts = etParts.text.toString().toIntOrNull() ?: 1
        val hours = etEstimatedHours.text.toString().toIntOrNull() ?: 1

        val totalHours = parts * hours
        val manHours = manpower * totalHours

        tvTotalHours.text = "📊 کل بازه: $totalHours ساعت"
        tvManHours.text = "🧮 نفر-ساعت: $manHours"
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
        rbFixedEquipment.isChecked = true
        selectedWorkType = WORK_TYPE_FIXED
        selectedEvaluationValue = 1
        sliderEvaluation.value = 1f
        showPhysicalEvaluation()

        // پیش‌فرض‌های منابع
        etManpower.setText("1")
        etParts.setText("1")
        etEstimatedHours.setText("1")
    }

    private fun submitEstimate() {
        // اعتبارسنجی
        val manpower = etManpower.text.toString().toIntOrNull() ?: 0
        val parts = etParts.text.toString().toIntOrNull() ?: 0
        val hours = etEstimatedHours.text.toString().toIntOrNull() ?: 0

        if (manpower <= 0) {
            Toast.makeText(this, "تعداد نفرات باید بیشتر از ۰ باشد", Toast.LENGTH_SHORT).show()
            return
        }
        if (parts <= 0) {
            Toast.makeText(this, "تعداد قسمت‌ها باید بیشتر از ۰ باشد", Toast.LENGTH_SHORT).show()
            return
        }
        if (hours <= 0) {
            Toast.makeText(this, "مدت هر قسمت باید بیشتر از ۰ باشد", Toast.LENGTH_SHORT).show()
            return
        }

        val heat = 30 + seekBarHeat.progress
        val pollution = seekBarPollution.progress
        val notes = etNotes.text.toString().trim()

        val physicalDifficulty = when (selectedWorkType) {
            WORK_TYPE_FIXED, WORK_TYPE_ROTATING -> selectedEvaluationValue
            else -> 0
        }
        val technicalComplexity = when (selectedWorkType) {
            WORK_TYPE_ROTATING -> technicalComplexityValue
            else -> 0
        }

        // کاربر جاری
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"

        lifecycleScope.launch {
            try {
                estimateLocalRepository.addEstimate(
                    taskId = taskId,
                    manpower = manpower,
                    parts = parts,
                    estimatedHours = hours,
                    workType = selectedWorkType,
                    heatLevel = heat,
                    pollutionLevel = pollution,
                    physicalDifficulty = physicalDifficulty,
                    technicalComplexity = technicalComplexity,
                    notes = notes,
                    estimatorId = userId,
                    estimatorName = userName
                )

                Toast.makeText(this@EstimateActivity, "✅ برآورد ثبت شد", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                Toast.makeText(this@EstimateActivity, "❌ خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}