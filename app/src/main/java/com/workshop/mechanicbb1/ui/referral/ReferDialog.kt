package com.workshop.mechanicbb1.ui.referral

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.Employee
import com.workshop.mechanicbb1.data.remote.Config
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import android.util.Log

class ReferDialog(
    private val context: Context,
    private val taskTitle: String,
    private val currentAssignees: String = "",
    private val currentResponsible: String = ""
) {
    private var dialog: AlertDialog? = null
    private lateinit var adapter: EmployeeSelectionAdapter
    private val selectedEmployees = mutableSetOf<Employee>()
    private var responsibleEmployee: Employee? = null
    private val client = OkHttpClient()

    private var onReferSubmit: ((String, String, String?) -> Unit)? = null

    fun setOnReferSubmitListener(listener: (String, String, String?) -> Unit) {
        this.onReferSubmit = listener
    }

    @SuppressLint("SetTextI18n")
    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_refer, null)

        view.findViewById<TextView>(R.id.tvDialogTitle).text = "ارجاع کار: $taskTitle"

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewEmployees)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(context)

        // آماده‌سازی مقادیر اولیه
        val preSelectedIds = currentAssignees.split(",").filter { it.isNotEmpty() }.toSet()
        val preSelectedResponsible = currentResponsible.takeIf { it.isNotEmpty() }

        // ساخت آداپتور
        adapter = EmployeeSelectionAdapter(
            employees = emptyList(),
            preSelectedIds = preSelectedIds,
            preSelectedResponsible = preSelectedResponsible
        ) { employee, isSelected, isResponsibleChange ->
            if (isSelected) {
                selectedEmployees.add(employee)
            } else {
                selectedEmployees.remove(employee)
                // اگر کاربری که حذف می‌شود، مسئول فعلی باشد، مسئول را null کن
                if (responsibleEmployee?.id == employee.id) {
                    responsibleEmployee = null
                }
            }

            if (isResponsibleChange) {
                responsibleEmployee = employee
            }

            updateSubmitButton(btnSubmit)
        }
        recyclerView.adapter = adapter

        // بارگذاری لیست کارمندان از سرور
        loadEmployees(adapter, progressBar) { employeesList ->
            // بعد از بارگذاری لیست، selectedEmployees را با افرادی که در preSelectedIds هستند پر کن
            val preSelectedEmployees = employeesList.filter { preSelectedIds.contains(it.id) }
            selectedEmployees.clear()
            selectedEmployees.addAll(preSelectedEmployees)

            // مسئول را هم تنظیم کن
            if (preSelectedResponsible != null) {
                responsibleEmployee = employeesList.find { it.id == preSelectedResponsible }
            }

            // به‌روزرسانی دکمه پس از مقداردهی
            updateSubmitButton(btnSubmit)
        }

        btnCancel.setOnClickListener { dialog?.dismiss() }

        btnSubmit.setOnClickListener {
            if (selectedEmployees.isEmpty()) {
                Toast.makeText(context, "حداقل یک نفر را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (responsibleEmployee == null) {
                Toast.makeText(context, "لطفاً یک مسئول برای کار انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnSubmit.isEnabled = false
            btnCancel.isEnabled = false

            val assigneeIds = selectedEmployees.map { it.id }.distinct().joinToString(",")
            val responsibleId = responsibleEmployee?.id

            onReferSubmit?.invoke(assigneeIds, Config.ReferralType.RESPONSIBLE, responsibleId)

            dialog?.dismiss()
        }

        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog?.show()
    }

    private fun loadEmployees(adapter: EmployeeSelectionAdapter, progressBar: ProgressBar, onComplete: (List<Employee>) -> Unit) {
        progressBar.visibility = View.VISIBLE

        val url = "${Config.BASE_URL}?action=getEmployees"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Cache-Control", "no-cache")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                (context as? android.app.Activity)?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "خطای شبکه: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete(emptyList())
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string() ?: "[]"

                (context as? android.app.Activity)?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    try {
                        val cleanResponse = responseBody.trim().replace("\uFEFF", "")
                        val jsonArray = JSONArray(cleanResponse)

                        val employees = mutableListOf<Employee>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val role = obj.getString("role")
                            // فقط نقش‌های ۲ (مدیر) و ۳ (کارمند) نمایش داده شوند
                            if (role == Config.RoleCode.EMPLOYEE || role == Config.RoleCode.SUPERVISOR) {
                                employees.add(
                                    Employee(
                                        id = obj.getString("rowId"),
                                        name = obj.getString("name"),
                                        role = role
                                    )
                                )
                            }
                        }
                        adapter.updateList(employees)
                        if (employees.isEmpty()) {
                            Toast.makeText(context, "کارمندی برای ارجاع وجود ندارد", Toast.LENGTH_SHORT).show()
                        }
                        onComplete(employees)
                    } catch (e: Exception) {
                        Toast.makeText(context, "خطا در دریافت لیست: ${e.message}", Toast.LENGTH_SHORT).show()
                        onComplete(emptyList())
                    }
                }
            }
        })
    }

    private fun updateSubmitButton(btnSubmit: Button) {
        val hasSelection = selectedEmployees.isNotEmpty()
        val hasResponsible = responsibleEmployee != null

        Log.d("ReferDialog", "hasSelection: $hasSelection, hasResponsible: $hasResponsible")

        btnSubmit.isEnabled = hasSelection && hasResponsible
        btnSubmit.text = when {
            !btnSubmit.isEnabled -> "ارجاع کار (انتخاب مسئول الزامی است)"
            responsibleEmployee != null -> "ارجاع (مسئول: ${responsibleEmployee?.name})"
            else -> "ارجاع به ${selectedEmployees.size} نفر"
        }
    }
}