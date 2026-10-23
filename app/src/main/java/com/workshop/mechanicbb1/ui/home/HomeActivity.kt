package com.workshop.mechanicbb1.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.archive.ArchiveActivity
import com.workshop.mechanicbb1.ui.cartable.CartableActivity
import com.workshop.mechanicbb1.ui.chat.ChatActivity
import com.workshop.mechanicbb1.ui.reports.PersonnelReportActivity
import com.workshop.mechanicbb1.ui.reports.ReportsActivity
import com.workshop.mechanicbb1.ui.settings.SettingsActivity
import com.workshop.mechanicbb1.ui.task.quicklog.QuickLogActivity
import com.workshop.mechanicbb1.utils.UserCache
import androidx.lifecycle.lifecycleScope
import com.workshop.mechanicbb1.data.local.TaskLocalRepository
import com.workshop.mechanicbb1.data.sync.TaskSyncer
import com.workshop.mechanicbb1.notifications.NotificationPermissionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class HomeActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var tvWelcome: TextView
    private lateinit var cardReports: CardView
    private lateinit var cardCartable: CardView
    private lateinit var cardArchive: CardView
    private lateinit var cardSettings: CardView
    private lateinit var cardChat: CardView
    private lateinit var cardQuickLog: CardView
    private lateinit var cardPersonnelReport: CardView
    private lateinit var taskLocalRepository: TaskLocalRepository
    private var responsibleTasksJob: Job? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // ← اول taskLocalRepository رو مقداردهی کن
        taskLocalRepository = TaskLocalRepository(this)

        initViews()
        setupToolbar()
        loadUserData()
        setupClickListeners()

        setQuickLogCardEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        // وقتی از صفحه ثبت گزارش برمی‌گردیم، دوباره بررسی کن
        checkUserHasResponsibleTasks()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        progressBar = findViewById(R.id.progressBar)
        tvWelcome = findViewById(R.id.tvWelcomeHome)

        cardReports = findViewById(R.id.cardReports)
        cardCartable = findViewById(R.id.cardCartable)
        cardArchive = findViewById(R.id.cardArchive)
        cardSettings = findViewById(R.id.cardSettings)
        cardChat = findViewById(R.id.cardChat)
        cardQuickLog = findViewById(R.id.cardQuickLog)
        cardPersonnelReport = findViewById(R.id.cardPersonnelReport)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun loadUserData() {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"
        tvWelcome.text = userName

        showLoading(true)
        disableCards(true)

        UserCache.init(this)
        UserCache.loadAllUsers {
            runOnUiThread {
                showLoading(false)
                disableCards(false)
                checkUserHasResponsibleTasks()
                requestNotificationPermission()
            }

            val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
            TaskSyncer.syncTasksFromServer(this, userId) { count ->
                Log.d("HomeActivity", "Synced $count tasks to Room")
            }
        }
    }

    // ====== متد کمکی برای تغییر وضعیت کارت ======
    private fun setQuickLogCardEnabled(enabled: Boolean) {
        cardQuickLog.isEnabled = enabled
        cardQuickLog.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun checkUserHasResponsibleTasks() {
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val currentUserId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""

        if (currentUserId.isEmpty()) {
            setQuickLogCardEnabled(false)
            return
        }

        // اول غیرفعال کن تا وقتی دیتا اومد
        setQuickLogCardEnabled(false)

        responsibleTasksJob?.cancel()
        responsibleTasksJob = lifecycleScope.launch {
            taskLocalRepository.getResponsibleTasks(currentUserId).collectLatest { tasks ->
                val hasTasks = tasks.isNotEmpty()
                setQuickLogCardEnabled(hasTasks)
                Log.d("HomeActivity", "Responsible tasks: ${tasks.size}, card enabled: $hasTasks")
            }
        }
    }

    private fun setupClickListeners() {
        cardReports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        cardCartable.setOnClickListener {
            startActivity(Intent(this, CartableActivity::class.java))
        }

        cardArchive.setOnClickListener {
            startActivity(Intent(this, ArchiveActivity::class.java))
        }

        cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        cardChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        cardPersonnelReport.setOnClickListener {
            startActivity(Intent(this, PersonnelReportActivity::class.java))
        }

        cardQuickLog.setOnClickListener {
            if (cardQuickLog.isEnabled) {
                startActivity(Intent(this, QuickLogActivity::class.java))
            } else {
                Toast.makeText(this, "شما مسئول هیچ کاری در حال انجام نیستید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun disableCards(disable: Boolean) {
        val cards = listOf(
            cardQuickLog,
            cardReports,
            cardCartable,
            cardArchive,
            cardSettings,
            cardChat
        )
        val alpha = if (disable) 0.5f else 1.0f
        cards.forEach { card ->
            card.isEnabled = !disable
            card.alpha = alpha
        }
    }

    private fun requestNotificationPermission() {
        if (!NotificationPermissionManager.hasPermission(this)) {
            val permissions = NotificationPermissionManager.getPermissionToRequest()
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "نوتیفیکیشن فعال شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "برای دریافت نوتیفیکیشن، مجوز بدید", Toast.LENGTH_LONG).show()
            }
        }
    }
}