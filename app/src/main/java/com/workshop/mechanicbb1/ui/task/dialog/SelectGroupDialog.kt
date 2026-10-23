package com.workshop.mechanicbb1.ui.task.dialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.Employee
import com.workshop.mechanicbb1.utils.UserCache
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SelectGroupDialog(
    private val currentGroupIds: String,
    private val availableEmployees: List<Employee>,
    private val onGroupSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnConfirm: Button
    private lateinit var btnCancel: Button
    private lateinit var btnSelectAll: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView
    private lateinit var tvSelectionCount: TextView
    private lateinit var searchView: SearchView

    private lateinit var adapter: GroupSelectionAdapter
    private val selectedIds = mutableSetOf<String>()
    private val allUsers = mutableListOf<UserItem>()
    private var filteredUsers = mutableListOf<UserItem>()
    private var isLoading = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_select_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // تنظیم ارتفاع دیالوگ
        dialog?.apply {
            setCanceledOnTouchOutside(true)
            setCancelable(true)
        }

        // تنظیم رفتار BottomSheet
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.peekHeight = 600 // ارتفاع پیش‌فرض
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        initViews(view)
        setupSearchView()
        restoreState(savedInstanceState)
        loadUsers()
        setupButtons()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewGroup)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        progressBar = view.findViewById(R.id.progressBar)
        tvLoading = view.findViewById(R.id.tvLoading)
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)
        searchView = view.findViewById(R.id.searchView)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setHasFixedSize(true)
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterUsers(newText ?: "")
                return true
            }
        })

        // تغییر رنگ hint
        val searchPlate = searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)
        searchPlate?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val savedIds = savedInstanceState.getStringArrayList("selectedIds") ?: emptyList()
            selectedIds.addAll(savedIds)
        } else {
            if (currentGroupIds.isNotEmpty()) {
                selectedIds.addAll(currentGroupIds.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }
        }
        updateSelectionCount()
    }

    private fun loadUsers() {
        if (availableEmployees.isEmpty()) {
            showEmptyState()
            return
        }

        isLoading = true
        updateLoadingState(true)

        // ساخت لیست اولیه
        buildInitialUserList()

        // بررسی کاربرانی که در کش نیستند
        val missingUsers = availableEmployees.filter {
            it.id.isNotEmpty() && it.id != "0" && !UserCache.hasUser(it.id)
        }

        if (missingUsers.isNotEmpty()) {
            tvLoading.text = "در حال بارگذاری اسامی (${missingUsers.size} کاربر)..."
            UserCache.loadAllUsers {
                activity?.runOnUiThread {
                    refreshAllUserNames()
                }
            }
        } else {
            refreshAllUserNames()
        }
    }

    private fun buildInitialUserList() {
        allUsers.clear()

        for (employee in availableEmployees) {
            val initialName = when {
                employee.name.isNotEmpty() && employee.name != employee.id -> employee.name
                employee.id.isNotEmpty() && employee.id != "0" -> {
                    val cachedName = UserCache.getName(employee.id)
                    if (cachedName != "نامشخص" && !cachedName.startsWith("کاربر $")) cachedName
                    else "در حال بارگذاری..."
                }
                else -> employee.name.ifEmpty { "کاربر ناشناس" }
            }

            allUsers.add(
                UserItem(
                    id = employee.id,
                    name = initialName,
                    isSelected = selectedIds.contains(employee.id)
                )
            )
        }

        allUsers.sortBy { it.name }
        filteredUsers.clear()
        filteredUsers.addAll(allUsers)
    }

    private fun refreshAllUserNames() {
        var hasChanges = false

        for (user in allUsers) {
            if (user.name == "در حال بارگذاری..." || user.name.startsWith("کاربر ")) {
                val newName = UserCache.getName(user.id)
                if (newName != "نامشخص" && !newName.startsWith("کاربر $")) {
                    user.name = newName
                    hasChanges = true
                } else if (user.id.isNotEmpty() && user.id != "0") {
                    user.name = user.id.take(8)
                    hasChanges = true
                }
            }
        }

        if (hasChanges) {
            allUsers.sortBy { it.name }
            filterUsers(searchView.query?.toString() ?: "")
        }

        finishLoading()
    }

    private fun filterUsers(query: String) {
        if (query.isEmpty()) {
            filteredUsers.clear()
            filteredUsers.addAll(allUsers)
        } else {
            filteredUsers.clear()
            filteredUsers.addAll(allUsers.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.id.contains(query, ignoreCase = true)
            })
        }

        if (::adapter.isInitialized) {
            adapter.updateList(filteredUsers)
        }

        // نمایش پیام اگر نتیجهای یافت نشد
        if (filteredUsers.isEmpty() && !isLoading) {
            tvLoading.text = "نتیجه‌ای یافت نشد"
            tvLoading.visibility = View.VISIBLE
        } else {
            tvLoading.visibility = View.GONE
        }
    }

    private fun finishLoading() {
        isLoading = false
        updateLoadingState(false)

        if (!::adapter.isInitialized) {
            adapter = GroupSelectionAdapter(filteredUsers) { userId, isChecked ->
                updateUserSelection(userId, isChecked)
            }
            recyclerView.adapter = adapter
        } else {
            adapter.updateList(filteredUsers)
        }

        updateSelectionCount()
    }

    private fun updateUserSelection(userId: String, isChecked: Boolean) {
        if (isChecked) {
            selectedIds.add(userId)
        } else {
            selectedIds.remove(userId)
        }
        updateSelectionCount()

        // به‌روزرسانی وضعیت در allUsers
        allUsers.find { it.id == userId }?.isSelected = isChecked
    }

    private fun updateSelectionCount() {
        val count = selectedIds.size
        val total = allUsers.size
        tvSelectionCount.text = when (count) {
            0 -> "هیچ کسی انتخاب نشده"
            total -> "همه افراد انتخاب شده ($count نفر)"
            else -> "$count نفر از $total نفر انتخاب شده"
        }

        // تغییر رنگ متن در صورت انتخاب همه
        if (count == total && total > 0) {
            tvSelectionCount.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            tvSelectionCount.setTextColor(android.graphics.Color.parseColor("#757575"))
        }
    }

    private fun selectAll() {
        if (selectedIds.size == allUsers.size) {
            // اگر همه انتخاب شده‌اند، همه را لغو انتخاب کن
            selectedIds.clear()
            allUsers.forEach { it.isSelected = false }
        } else {
            // در غیر این صورت، همه را انتخاب کن
            selectedIds.clear()
            selectedIds.addAll(allUsers.map { it.id })
            allUsers.forEach { it.isSelected = true }
        }

        // به‌روزرسانی لیست فیلتر شده
        filteredUsers.forEach { user ->
            user.isSelected = selectedIds.contains(user.id)
        }

        adapter?.notifyDataSetChanged()
        updateSelectionCount()

        // تغییر متن دکمه
        btnSelectAll.text = if (selectedIds.size == allUsers.size) "لغو همه" else "انتخاب همه"
    }

    private fun showEmptyState() {
        isLoading = false
        updateLoadingState(false)
        tvLoading.text = "هیچ کاربری برای انتخاب وجود ندارد"
        tvLoading.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        btnConfirm.isEnabled = false
        btnSelectAll.isEnabled = false
    }

    private fun updateLoadingState(loading: Boolean) {
        if (loading) {
            progressBar.visibility = View.VISIBLE
            tvLoading.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            btnConfirm.isEnabled = false
            btnSelectAll.isEnabled = false
            searchView.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            btnConfirm.isEnabled = true
            btnSelectAll.isEnabled = true
            searchView.isEnabled = true
            if (filteredUsers.isNotEmpty()) {
                tvLoading.visibility = View.GONE
            }
        }
    }

    private fun setupButtons() {
        btnConfirm.setOnClickListener {
            if (!isLoading) {
                val newGroupIds = selectedIds.joinToString(",")
                onGroupSelected(newGroupIds)
                dismiss()
            }
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSelectAll.setOnClickListener {
            selectAll()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selectedIds", ArrayList(selectedIds))
    }

    override fun onStart() {
        super.onStart()
        updateSelectionCount()
    }

    data class UserItem(
        val id: String,
        var name: String,
        var isSelected: Boolean
    )

    inner class GroupSelectionAdapter(
        private var users: List<UserItem>,
        private val onItemClick: (userId: String, isChecked: Boolean) -> Unit
    ) : RecyclerView.Adapter<GroupSelectionAdapter.ViewHolder>() {

        fun updateList(newUsers: List<UserItem>) {
            users = newUsers
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group_selection, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.bind(user)
        }

        override fun getItemCount() = users.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelect)
            private val tvName: TextView = itemView.findViewById(R.id.tvName)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

            fun bind(user: UserItem) {
                tvName.text = user.name
                cbSelect.isChecked = user.isSelected
                tvStatus.text = if (user.isSelected) "✓ انتخاب شده" else "انتخاب"
                tvStatus.setTextColor(if (user.isSelected)
                    android.graphics.Color.parseColor("#4CAF50")
                else android.graphics.Color.parseColor("#9E9E9E"))

                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    if (user.isSelected != isChecked) {
                        user.isSelected = isChecked
                        onItemClick(user.id, isChecked)
                        tvStatus.text = if (isChecked) "✓ انتخاب شده" else "انتخاب"
                        tvStatus.setTextColor(if (isChecked)
                            android.graphics.Color.parseColor("#4CAF50")
                        else android.graphics.Color.parseColor("#9E9E9E"))
                    }
                }

                itemView.setOnClickListener {
                    cbSelect.isChecked = !cbSelect.isChecked
                }
            }
        }
    }
}