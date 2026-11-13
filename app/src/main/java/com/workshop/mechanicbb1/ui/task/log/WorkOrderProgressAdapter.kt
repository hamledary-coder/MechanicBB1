package com.workshop.mechanicbb1.ui.task.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.WorkOrderModel

class WorkOrderProgressAdapter(
    private val workOrders: List<WorkOrderModel>,
    existingProgresses: Map<String, ProgressData> = emptyMap()
) : RecyclerView.Adapter<WorkOrderProgressAdapter.VH>() {

    data class ProgressData(
        val workOrderId: String,
        var status: Status = Status.NOT_COMPLETED,
        var progress: Int = 0,
        var hasDetails: Boolean = false,
        var detailsNotes: String = "",
        var previousDetailsNotes: String = "",
        // ✅ جدید
        var previousProgress: Int = -1,        // -1 = اولین گزارش (هیچ progress قبلی نبود)
        var previousStatus: Status? = null     // null = اولین گزارش
    )

    enum class Status {
        COMPLETED,
        IN_PROGRESS,
        NOT_COMPLETED
    }

    private val progressDataMap = mutableMapOf<String, ProgressData>()

    init {
        workOrders.forEach { wo ->
            progressDataMap[wo.id] = existingProgresses[wo.id]?.copy()
                ?: ProgressData(workOrderId = wo.id)
        }
    }

    fun getProgressData(): List<ProgressData> {
        return workOrders.mapNotNull { progressDataMap[it.id] }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val woColorBullet: View = view.findViewById(R.id.woColorBullet)
        val tvWoTitle: TextView = view.findViewById(R.id.tvWoTitle)
        val tvWoInfo: TextView = view.findViewById(R.id.tvWoInfo)

        val layoutCompleted: LinearLayout = view.findViewById(R.id.layoutCompleted)
        val layoutInProgress: LinearLayout = view.findViewById(R.id.layoutInProgress)
        val layoutNotCompleted: LinearLayout = view.findViewById(R.id.layoutNotCompleted)

        val ivCompleted: ImageView = view.findViewById(R.id.ivCompleted)
        val ivInProgress: ImageView = view.findViewById(R.id.ivInProgress)
        val ivNotCompleted: ImageView = view.findViewById(R.id.ivNotCompleted)

        val layoutProgress: LinearLayout = view.findViewById(R.id.layoutProgress)
        val tvProgressLabel: TextView = view.findViewById(R.id.tvProgressLabel)
        val sliderProgress: Slider = view.findViewById(R.id.sliderProgress)

        val layoutPreviousDetails: LinearLayout = view.findViewById(R.id.layoutPreviousDetails)
        val tvPreviousDetails: TextView = view.findViewById(R.id.tvPreviousDetails)

        val cbHasDetails: CheckBox = view.findViewById(R.id.cbHasDetails)
        val layoutDetailsNotes: LinearLayout = view.findViewById(R.id.layoutDetailsNotes)
        val etDetailsNotes: EditText = view.findViewById(R.id.etDetailsNotes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_log_workorder_item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val wo = workOrders[position]
        val data = progressDataMap[wo.id] ?: ProgressData(workOrderId = wo.id)

        // عنوان
        holder.tvWoTitle.text = wo.title
        val currentProgressText = if (wo.progress > 0) " | ${wo.progress}٪ (قبلی)" else ""
        holder.tvWoInfo.text = "${wo.classText} | ${wo.unit}$currentProgressText"

        // بولت
        holder.woColorBullet.setBackgroundColor(getBulletColor(wo.requestClass))

        // ✅ تابع آپدیت UI
        fun updateUI() {
            holder.ivCompleted.setImageResource(
                if (data.status == Status.COMPLETED) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            )
            holder.ivInProgress.setImageResource(
                if (data.status == Status.IN_PROGRESS) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            )
            holder.ivNotCompleted.setImageResource(
                if (data.status == Status.NOT_COMPLETED) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            )

            holder.layoutProgress.visibility =
                if (data.status == Status.IN_PROGRESS) View.VISIBLE else View.GONE
            holder.sliderProgress.value = data.progress.toFloat().coerceIn(10f, 90f)
            holder.tvProgressLabel.text = "میزان پیشرفت: ${data.progress}٪"
        }

        updateUI()

        // ============================================
        // چک‌باکس و یادداشت (فقط یک‌بار)
        // ============================================
        holder.cbHasDetails.setOnCheckedChangeListener(null)
        holder.cbHasDetails.isChecked = data.hasDetails
        holder.layoutDetailsNotes.visibility =
            if (data.hasDetails) View.VISIBLE else View.GONE
        holder.etDetailsNotes.setText(data.detailsNotes)

        // ============================================
        // کلیک روی گزینه‌ها
        // ============================================
        holder.layoutCompleted.setOnClickListener {
            data.status = Status.COMPLETED
            data.progress = 100
            progressDataMap[wo.id] = data
            updateUI()
        }

        holder.layoutInProgress.setOnClickListener {
            data.status = Status.IN_PROGRESS
            if (data.progress <= 0) data.progress = 50
            progressDataMap[wo.id] = data
            updateUI()
        }

        holder.layoutNotCompleted.setOnClickListener {
            data.status = Status.NOT_COMPLETED
            data.progress = 0
            progressDataMap[wo.id] = data
            updateUI()
        }

        // ============================================
        // Slider
        // ============================================
        holder.sliderProgress.clearOnChangeListeners()
        holder.sliderProgress.addOnChangeListener { _, value, _ ->
            if (data.status == Status.IN_PROGRESS) {
                data.progress = value.toInt()
                holder.tvProgressLabel.text = "میزان پیشرفت: ${data.progress}٪"
                progressDataMap[wo.id] = data
            }
        }

        // ✅ شرح جزئیات قبلی (فقط‌خواندنی)
        if (data.previousDetailsNotes.isNotEmpty()) {
            holder.layoutPreviousDetails.visibility = View.VISIBLE
            holder.tvPreviousDetails.text = data.previousDetailsNotes
        } else {
            holder.layoutPreviousDetails.visibility = View.GONE
        }

        // ============================================
        // چک‌باکس جزئیات (listener)
        // ============================================
        holder.cbHasDetails.setOnCheckedChangeListener { _, isChecked ->
            data.hasDetails = isChecked
            holder.layoutDetailsNotes.visibility =
                if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                holder.etDetailsNotes.setText("")
                data.detailsNotes = ""
            }
            progressDataMap[wo.id] = data
        }

        // ✅ یادداشت — doAfterTextChanged
        holder.etDetailsNotes.doAfterTextChanged { text ->
            data.detailsNotes = text?.toString() ?: ""
            progressDataMap[wo.id] = data
        }
    }

    override fun getItemCount(): Int = workOrders.size

    private fun getBulletColor(requestClass: String?): Int {
        return when (requestClass) {
            "14" -> Color.parseColor("#F44336")
            "12" -> Color.parseColor("#FF9800")
            "12P" -> Color.parseColor("#2196F3")
            "15" -> Color.parseColor("#4CAF50")
            else -> Color.parseColor("#9E9E9E")
        }
    }
}