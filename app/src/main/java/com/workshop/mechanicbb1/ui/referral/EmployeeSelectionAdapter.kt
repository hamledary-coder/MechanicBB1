package com.workshop.mechanicbb1.ui.referral

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.Employee

class EmployeeSelectionAdapter(
    private var employees: List<Employee>,
    private val preSelectedIds: Set<String> = emptySet(),
    private val preSelectedResponsible: String? = null,
    private val onSelectionChanged: (Employee, Boolean, Boolean) -> Unit
) : RecyclerView.Adapter<EmployeeSelectionAdapter.ViewHolder>() {

    private val selectedIds = mutableSetOf<String>().apply { addAll(preSelectedIds) }
    private var responsibleId: String? = preSelectedResponsible

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvEmployeeName)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val rbResponsible: RadioButton = view.findViewById(R.id.rbResponsible)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_employee_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val employee = employees[position]
        holder.tvName.text = employee.name

        holder.cbSelect.setOnCheckedChangeListener(null)
        holder.rbResponsible.setOnCheckedChangeListener(null)

        val isSelected = selectedIds.contains(employee.id)
        val isResponsible = responsibleId == employee.id

        holder.cbSelect.isChecked = isSelected
        holder.rbResponsible.isChecked = isResponsible
        holder.rbResponsible.isEnabled = isSelected

        // چک‌باکس
        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedIds.add(employee.id)
                onSelectionChanged(employee, true, false)
                holder.rbResponsible.isEnabled = true
            } else {
                selectedIds.remove(employee.id)

                // بررسی کنیم آیا این کاربر مسئول است یا نه
                val wasResponsible = (responsibleId == employee.id)

                if (wasResponsible) {
                    // مسئولیت را لغو کن
                    responsibleId = null
                    holder.rbResponsible.isChecked = false
                    // اطلاع بده که مسئولیت لغو شده (isResponsibleChange = false تا دوباره تنظیم نشود)
                    onSelectionChanged(employee, false, false)
                } else {
                    onSelectionChanged(employee, false, false)
                }

                holder.rbResponsible.isEnabled = false
            }
        }

        // رادیو باتن مسئول
        holder.rbResponsible.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val prevResponsible = responsibleId
                responsibleId = employee.id
                if (prevResponsible != null && prevResponsible != employee.id) {
                    notifyDataSetChanged()
                }
                onSelectionChanged(employee, true, true)
            } else {
                if (responsibleId == employee.id) {
                    responsibleId = null
                    onSelectionChanged(employee, true, false)
                }
            }
        }

        // کلیک روی کل آیتم
        holder.itemView.setOnClickListener {
            holder.cbSelect.isChecked = !holder.cbSelect.isChecked
        }
    }

    override fun getItemCount() = employees.size

    fun updateList(newList: List<Employee>) {
        employees = newList

        // حفظ مقادیر انتخاب شده قبلی (تغییرات کاربر در این جلسه)
        selectedIds.retainAll(employees.map { it.id })

        // اگر مسئول قبلی هنوز در لیست جدید وجود دارد، آن را حفظ کن
        if (responsibleId != null && employees.any { it.id == responsibleId }) {
            // حفظ شود
        } else {
            responsibleId = null
        }

        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()
    fun getResponsibleId(): String? = responsibleId
}