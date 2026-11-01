package com.workshop.mechanicbb1.ui.cartable

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.workshop.mechanicbb1.ui.task.create.CreateTaskActivity
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.task.list.WorkListFragment
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.workshop.mechanicbb1.ui.task.list.MyTasksFragment

class CartableActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ViewPagerAdapter
    private lateinit var toolbar: Toolbar

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cartable)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "کارتابل"

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        val fabAddTask = findViewById<ExtendedFloatingActionButton>(R.id.fabAddTask)

        viewPager.layoutDirection = ViewPager2.LAYOUT_DIRECTION_RTL

        adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        // ✅ ۳ تب جدید
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "کارهای من"
                    tab.setIcon(R.drawable.ic_cartable)
                }
                1 -> {
                    tab.text = "کارهای امروز"
                    tab.setIcon(R.drawable.ic_progress)
                }
                2 -> {
                    tab.text = " ارجاع نشده"
                    tab.setIcon(R.drawable.ic_pending)
                }
            }
        }.attach()

        // ========== دسترسی ثبت کار جدید ==========
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userRole = sharedPref.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        if (userRole == Config.RoleCode.SUPERVISOR || userRole == Config.RoleCode.MANAGER) {
            fabAddTask.visibility = View.VISIBLE
            fabAddTask.setOnClickListener {
                val intent = Intent(this, CreateTaskActivity::class.java)
                startActivity(intent)
            }
        } else {
            fabAddTask.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.refreshAllFragments()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        private val workFragments = mutableListOf<WorkListFragment>()
        private var myTasksFragment: MyTasksFragment? = null

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> MyTasksFragment().also { myTasksFragment = it }
                1 -> WorkListFragment.newInstance("today").also { workFragments.add(it) }
                else -> WorkListFragment.newInstance("queue").also { workFragments.add(it) }
            }
        }

        fun refreshAllFragments() {
            myTasksFragment?.refreshTasks()
            workFragments.forEach { it.refreshTasks() }
        }
    }
}