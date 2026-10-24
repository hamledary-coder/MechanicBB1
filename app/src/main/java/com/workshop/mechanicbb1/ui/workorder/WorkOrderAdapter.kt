package com.workshop.mechanicbb1.ui.workorder

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.utils.DateUtils

class WorkOrderAdapter(
    private var workOrders: List<WorkOrderModel>,
    private val showClassHeaders: Boolean = true,   // اگه «همه‌ی کلاس‌ها» انتخاب شده
    private val onItemClick: (WorkOrderModel) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CARD = 1
    }

    // لیست نهایی (با هدرها)
    private val displayList = mutableListOf<DisplayItem>()

    init {
        rebuildDisplayList()
    }

    /**
     * ساخت لیست نمایش (با هدر کلاس‌ها)
     */
    private fun rebuildDisplayList() {
        displayList.clear()

        if (!showClassHeaders) {
            // بدون هدر (وقتی کلاس خاصی انتخاب شده)
            workOrders.forEach {
                displayList.add(DisplayItem.Card(it))
            }
            return
        }

        // گروه‌بندی بر اساس کلاس (۱۴ → ۱۲ → ۱۲P → بقیه)
        val groups = workOrders.groupBy { it.requestClass ?: "other" }
        val order = listOf("14", "12", "12P", "other")

        for (classKey in order) {
            val items = groups[classKey] ?: continue
            if (items.isEmpty()) continue

            // هدر
            val headerText = when (classKey) {
                "14" -> "📋 کلاس ۱۴ (غیرمترقبه)"
                "12" -> "📋 کلاس ۱۲ (برنامه‌ای)"
                "12P" -> "📋 پیشگیرانه (۱۲-P)"
                else -> "📋 بدون کلاس"
            }
            val headerColor = when (classKey) {
                "14" -> "#D32F2F"
                "12" -> "#FF9800"
                "12P" -> "#4CAF50"
                else -> "#9E9E9E"
            }
            displayList.add(DisplayItem.Header(headerText, headerColor))

            // کارت‌ها
            items.forEach { displayList.add(DisplayItem.Card(it)) }
        }
    }

    /**
     * به‌روزرسانی لیست
     */
    fun updateData(newList: List<WorkOrderModel>, showHeaders: Boolean = showClassHeaders) {
        workOrders = newList
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is DisplayItem.Header -> TYPE_HEADER
            is DisplayItem.Card -> TYPE_CARD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.workorder_item_class_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.workorder_item_card, parent, false)
                CardViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is DisplayItem.Header -> (holder as HeaderViewHolder).bind(item)
            is DisplayItem.Card -> (holder as CardViewHolder).bind(item.workOrder)
        }
    }

    override fun getItemCount(): Int = displayList.size

    // ============ ViewHolders ============

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorBar: View = itemView.findViewById(R.id.colorBar)
        private val tvClassHeader: TextView = itemView.findViewById(R.id.tvClassHeader)

        fun bind(header: DisplayItem.Header) {
            tvClassHeader.text = header.text
            tvClassHeader.setTextColor(Color.parseColor(header.color))
            colorBar.setBackgroundColor(Color.parseColor(header.color))
        }
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView as CardView
        private val tvWoNumber: TextView = itemView.findViewById(R.id.tvWoNumber)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)

        fun bind(wo: WorkOrderModel) {
            tvWoNumber.text = wo.id  // WO-1001
            tvDate.text = wo.requestDate.ifEmpty { "—" }
            tvTitle.text = wo.title.ifEmpty { "بدون عنوان" }

            // درصد پیشرفت
            tvProgress.text = wo.progressText
            tvProgress.setTextColor(wo.progressColor)

            // کلیک
            card.setOnClickListener {
                onItemClick(wo)
            }
        }
    }

    // ============ DisplayItem (برای هدر و کارت) ============

    sealed class DisplayItem {
        data class Header(val text: String, val color: String) : DisplayItem()
        data class Card(val workOrder: WorkOrderModel) : DisplayItem()
    }
}