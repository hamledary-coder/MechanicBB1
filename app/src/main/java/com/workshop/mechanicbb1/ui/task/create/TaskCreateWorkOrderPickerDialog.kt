package com.workshop.mechanicbb1.ui.task.create

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.WorkOrderModel

class TaskCreateWorkOrderPickerDialog(
    context: Context,
    private val availableWorkOrders: List<WorkOrderModel>,
    private val preselectedIds: Set<String>,
    private val onConfirm: (Set<String>) -> Unit
) : Dialog(context) {

    private val selectedIds = mutableSetOf<String>().apply { addAll(preselectedIds) }
    private var filteredItems = listOf<WorkOrderModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.taskcreate_workorder_picker_dialog)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etSearch = findViewById<EditText>(R.id.etSearch)
        val rv = findViewById<RecyclerView>(R.id.rvWorkOrders)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val ivClose = findViewById<ImageView>(R.id.ivClose)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        rv.layoutManager = LinearLayoutManager(context)
        filteredItems = availableWorkOrders
        rv.adapter = WorkOrderPickerAdapter(filteredItems)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                filteredItems = if (q.isEmpty()) {
                    availableWorkOrders
                } else {
                    availableWorkOrders.filter {
                        it.id.lowercase().contains(q) ||
                                it.title.lowercase().contains(q) ||
                                it.requestNumber.lowercase().contains(q)
                    }
                }
                rv.adapter = WorkOrderPickerAdapter(filteredItems)
                tvEmpty.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE
            }
        })

        tvEmpty.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE

        btnConfirm.setOnClickListener {
            onConfirm(selectedIds)
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
        ivClose.setOnClickListener { dismiss() }
    }

    inner class WorkOrderPickerAdapter(
        private val items: List<WorkOrderModel>
    ) : RecyclerView.Adapter<WorkOrderPickerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cb: CheckBox = view.findViewById(R.id.cbWorkOrder)
            val tvWoNumber: TextView = view.findViewById(R.id.tvWoNumber)
            val tvWoTitle: TextView = view.findViewById(R.id.tvWoTitle)
            val tvWoInfo: TextView = view.findViewById(R.id.tvWoInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.taskcreate_workorder_item, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val wo = items[position]
            holder.tvWoNumber.text = wo.id
            holder.tvWoTitle.text = wo.title.ifEmpty { "(بدون عنوان)" }
            holder.tvWoInfo.text = "${wo.classText} | ${wo.unit}"

            holder.cb.setOnCheckedChangeListener(null)
            holder.cb.isChecked = selectedIds.contains(wo.id)
            holder.cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedIds.add(wo.id)
                else selectedIds.remove(wo.id)
            }

            holder.itemView.setOnClickListener {
                holder.cb.isChecked = !holder.cb.isChecked
            }
        }

        override fun getItemCount() = items.size
    }
}