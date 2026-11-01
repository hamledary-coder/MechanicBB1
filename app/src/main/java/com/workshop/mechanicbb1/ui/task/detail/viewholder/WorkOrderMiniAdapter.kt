package com.workshop.mechanicbb1.ui.task.detail.viewholder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.WorkOrderModel

class WorkOrderMiniAdapter(
    private val items: List<WorkOrderModel>,
    private val onClick: (WorkOrderModel) -> Unit
) : RecyclerView.Adapter<WorkOrderMiniAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvWoNumber: TextView = view.findViewById(R.id.tvWoNumber)
        val tvWoTitle: TextView = view.findViewById(R.id.tvWoTitle)
        val tvWoClass: TextView = view.findViewById(R.id.tvWoClass)
        val tvWoUnit: TextView = view.findViewById(R.id.tvWoUnit)
        val tvWoProgress: TextView = view.findViewById(R.id.tvWoProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.taskdetail_workorder_card_item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val wo = items[position]

        holder.tvWoNumber.text = wo.id
        holder.tvWoTitle.text = wo.title

        holder.tvWoClass.text = wo.classText

        holder.tvWoUnit.text = if (wo.unit.isNotEmpty()) " | ${wo.unit}" else ""

        holder.tvWoProgress.text = wo.progressText
        holder.tvWoProgress.setTextColor(wo.progressColor)

        holder.itemView.setOnClickListener { onClick(wo) }
    }

    override fun getItemCount(): Int = items.size
}