package com.workshop.mechanicbb1.ui.task.log.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.ui.task.log.WorkOrderProgressAdapter
import com.workshop.mechanicbb1.utils.UserCache

class ConfirmReportDialog(
    context: Context,
    private val data: ReportPreviewData,
    private val onConfirm: () -> Unit
) : Dialog(context) {

    data class ReportPreviewData(
        val date: String,
        val startTime: String,
        val endTime: String,
        val durationMinutes: Int,
        val groupUserIds: String,
        val workOrders: List<WorkOrderModel>,
        val progressData: List<WorkOrderProgressAdapter.ProgressData>,
        val notes: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = LayoutInflater.from(context)
            .inflate(R.layout.add_log_confirm_report_dialog, null)

        setContentView(view)

        // ✅ محدود کردن عرض/ارتفاع دیالوگ
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        bindViews(view)
        setupButtons(view)
    }

    private fun bindViews(root: View) {
        // تاریخ / ساعت / مدت
        root.findViewById<TextView>(R.id.tvReportDate).text = data.date
        root.findViewById<TextView>(R.id.tvReportTime).text =
            "${data.startTime} - ${data.endTime}"
        root.findViewById<TextView>(R.id.tvReportDuration).text =
            formatDuration(data.durationMinutes)

        // گروه
        root.findViewById<TextView>(R.id.tvReportGroup).text =
            formatGroup(data.groupUserIds)

        // ============ بخش گزارش (ادغام‌شده) ============
        val workOrdersSection = root.findViewById<LinearLayout>(R.id.containerWorkOrders)
        val notesSection = root.findViewById<LinearLayout>(R.id.sectionNotes)
        val reportHeader = root.findViewById<LinearLayout>(R.id.reportHeader)  // ← LinearLayout نه View

        // ۱. کارت‌های WorkOrder
        workOrdersSection.removeAllViews()
        if (data.progressData.isEmpty()) {
            workOrdersSection.visibility = View.GONE
        } else {
            workOrdersSection.visibility = View.VISIBLE
            data.progressData.forEach { p ->
                val wo = data.workOrders.find { it.id == p.workOrderId } ?: return@forEach
                addWorkOrderRow(workOrdersSection, wo, p)
            }
        }

        // ۲. بخش notes (کارهای بدون درخواست)
        if (data.notes.isBlank()) {
            notesSection.visibility = View.GONE
        } else {
            notesSection.visibility = View.VISIBLE
            root.findViewById<TextView>(R.id.tvReportNotes).text = data.notes
        }

        // ۳. اگه هیچی نبود، هدر گزارش رو هم مخفی کن
        if (data.progressData.isEmpty() && data.notes.isBlank()) {
            reportHeader.visibility = View.GONE
        } else {
            reportHeader.visibility = View.VISIBLE
        }
    }

    private fun addWorkOrderRow(
        container: LinearLayout,
        wo: WorkOrderModel,
        progress: WorkOrderProgressAdapter.ProgressData
    ) {
        val rowView = LayoutInflater.from(context)
            .inflate(R.layout.add_log_confirm_report_workorder_item, container, false)

        val tvStatus = rowView.findViewById<TextView>(R.id.tvWoStatusText)
        val tvDetails = rowView.findViewById<TextView>(R.id.tvWoDetails)

        val (text, color) = when (progress.status) {
            WorkOrderProgressAdapter.Status.COMPLETED ->
                "✅ ${wo.title}: انجام شد" to "#2E7D32"
            WorkOrderProgressAdapter.Status.IN_PROGRESS ->
                "⏳ ${progress.progress} درصد از درخواست مربوط به «${wo.title}» انجام شده (ادامه دارد)" to "#FF9800"
            WorkOrderProgressAdapter.Status.NOT_COMPLETED ->
                "❌ ${wo.title}: انجام نشد" to "#C62828"
        }

        tvStatus.text = text
        tvStatus.setTextColor(Color.parseColor(color))

        if (progress.hasDetails && progress.detailsNotes.isNotBlank()) {
            tvDetails.visibility = View.VISIBLE
            tvDetails.text = "📝 ${progress.detailsNotes}"
        } else {
            tvDetails.visibility = View.GONE
        }

        container.addView(rowView)
    }

    private fun setupButtons(root: View) {
        root.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            dismiss()
            onConfirm()
        }
        root.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }
    }

    private fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h ساعت و $m دقیقه"
            h > 0 -> "$h ساعت"
            m > 0 -> "$m دقیقه"
            else -> "کمتر از یک دقیقه"
        }
    }

    private fun formatGroup(userIds: String): String {
        if (userIds.isBlank()) return "تعیین نشده"
        return userIds.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { UserCache.getName(it) }
            .joinToString("، ")
    }
}