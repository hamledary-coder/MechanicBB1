package com.workshop.mechanicbb1.ui.task.detail.viewholder

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.WorkOrderModel

class WorkOrderMiniAdapter(
    private var items: List<WorkOrderModel>,
    private val onClick: (WorkOrderModel) -> Unit,
    private val showRemoveButton: Boolean = false,
    private val onRemove: ((WorkOrderModel) -> Unit)? = null
) : RecyclerView.Adapter<WorkOrderMiniAdapter.VH>() {

    fun updateList(newList: List<WorkOrderModel>) {
        this.items = newList
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val woColorBullet: View = view.findViewById(R.id.woColorBullet)
        val tvWoTitle: TextView = view.findViewById(R.id.tvWoTitle)
        val tvWoClass: TextView = view.findViewById(R.id.tvWoClass)
        val tvWoUnit: TextView = view.findViewById(R.id.tvWoUnit)
        val tvWoProgress: TextView = view.findViewById(R.id.tvWoProgress)
        val ivRemoveWo: ImageView = view.findViewById(R.id.ivRemoveWo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.taskdetail_workorder_card_item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val wo = items[position]

        holder.tvWoTitle.text = wo.title
        holder.tvWoClass.text = wo.classText
        holder.tvWoUnit.text = if (wo.unit.isNotEmpty()) " | ${wo.unit}" else ""
        holder.tvWoProgress.text = wo.progressText
        holder.tvWoProgress.setTextColor(wo.progressColor)

        // ✅ بولت رنگی بر اساس requestClass
        holder.woColorBullet.setBackgroundColor(getBulletColor(wo.requestClass))

        // دکمه حذف
        if (showRemoveButton && onRemove != null) {
            holder.ivRemoveWo.visibility = View.VISIBLE
            holder.ivRemoveWo.setOnClickListener { onRemove.invoke(wo) }
        } else {
            holder.ivRemoveWo.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(wo) }
    }

    override fun getItemCount(): Int = items.size

    // ✅ رنگ بولت بر اساس requestClass
    private fun getBulletColor(requestClass: String?): Int {
        return when (requestClass) {
            "14" -> Color.parseColor("#F44336")   // قرمز — اضطراری
            "12" -> Color.parseColor("#FF9800")   // نارنجی
            "12P" -> Color.parseColor("#2196F3")  // آبی
            "15" -> Color.parseColor("#4CAF50")   // سبز
            else -> Color.parseColor("#9E9E9E")   // خاکستری (نامشخص)
        }
    }
}