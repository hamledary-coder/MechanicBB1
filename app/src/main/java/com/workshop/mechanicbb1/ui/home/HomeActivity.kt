package com.workshop.mechanicbb1.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.TaskSyncer
import com.workshop.mechanicbb1.data.sync.WorkOrderSyncer
import com.workshop.mechanicbb1.notifications.NotificationPermissionManager
import com.workshop.mechanicbb1.ui.archive.ArchiveActivity
import com.workshop.mechanicbb1.ui.cartable.CartableActivity
import com.workshop.mechanicbb1.ui.reports.PersonnelReportActivity
import com.workshop.mechanicbb1.ui.reports.ReportsActivity
import com.workshop.mechanicbb1.ui.settings.SettingsActivity
import com.workshop.mechanicbb1.ui.task.quicklog.QuickLogActivity
import com.workshop.mechanicbb1.ui.workorder.WorkOrderListActivity
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    // ===== Views =====
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var tvWelcome: TextView
    private lateinit var tvNetworkStatus: TextView
    private lateinit var tvLastOnlineLabel: TextView
    private lateinit var tvLastOnlineTime: TextView

    private lateinit var cardQuickLog: CardView
    private lateinit var cardCartable: CardView
    private lateinit var cardPersonnelReport: CardView

    // ===== Repos/Jobs =====
    private lateinit var taskLocalRepository: TaskLocalRepository
    private var responsibleTasksJob: Job? = null

    // ===== Network =====
    private lateinit var connectivityManager: ConnectivityManager
    private var isNetworkCallbackRegistered = false

    // ===== Prefs =====
    private val NET_PREFS = "NetworkPrefs"
    private val KEY_LAST_ONLINE = "last_online_timestamp"

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "HomeActivity"
    }

    // =========================================================================
    // Network Callback
    // =========================================================================
    private val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            saveLastOnlineTime()
            runOnUiThread { updateNetworkStatusUI(isOnline = true) }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            runOnUiThread { updateNetworkStatusUI(isOnline = false) }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, caps)
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            runOnUiThread { updateNetworkStatusUI(isOnline = hasInternet) }
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge (Android 15+)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setContentView(R.layout.home_activity)

        taskLocalRepository = TaskLocalRepository(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)

        initViews()
        setupToolbar()
        setupDrawer()
        loadUserData()
        setupClickListeners()
        checkNetworkStatusInitially()

        setQuickLogCardEnabled(false)
    }

    override fun onStart() {
        super.onStart()
        registerNetworkCallback()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
    }

    override fun onResume() {
        super.onResume()
        checkUserHasResponsibleTasks()
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // =========================================================================
    // Init
    // =========================================================================
    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        toolbar = findViewById(R.id.toolbar)
        progressBar = findViewById(R.id.progressBar)
        tvWelcome = findViewById(R.id.tvWelcomeHome)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        tvLastOnlineLabel = findViewById(R.id.tvLastOnlineLabel)
        tvLastOnlineTime = findViewById(R.id.tvLastOnlineTime)

        cardQuickLog = findViewById(R.id.cardQuickLog)
        cardCartable = findViewById(R.id.cardCartable)
        cardPersonnelReport = findViewById(R.id.cardPersonnelReport)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupDrawer() {
        // ۱. ActionBarDrawerToggle (آیکون hamburger + باز/بسته)
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        // ۲. Header — آواتار + نام + نقش
        val headerView = navigationView.getHeaderView(0)
        val tvDrawerAvatar = headerView.findViewById<TextView>(R.id.tvDrawerAvatar)
        val tvDrawerUserName = headerView.findViewById<TextView>(R.id.tvDrawerUserName)
        val tvDrawerUserRole = headerView.findViewById<TextView>(R.id.tvDrawerUserRole)

        val prefs = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        val userName = prefs.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"
        val userRole = prefs.getString(Config.PrefKeys.USER_ROLE, "") ?: ""

        tvDrawerUserName.text = userName
        tvDrawerAvatar.text = userName.firstOrNull()?.toString()?.uppercase() ?: "?"
        tvDrawerUserRole.text = when (userRole) {
            Config.RoleCode.MANAGER -> "مدیر"
            Config.RoleCode.SUPERVISOR -> "سرشیفت"
            Config.RoleCode.EMPLOYEE -> "کارمند"
            else -> "کاربر"
        }

        // ۳. محدودیت نمایش «گزارشات روزانه» (فقط مدیر/سرشیفت)
        val isManager = userRole == Config.RoleCode.MANAGER || userRole == Config.RoleCode.SUPERVISOR
        navigationView.menu.findItem(R.id.nav_reports)?.isVisible = isManager

        // ۴. تزریق آیکون‌های رنگی
        applyColoredMenuIcons()

        // ۵. انتخاب پیش‌فرض: خانه
        navigationView.setCheckedItem(R.id.nav_home)
    }

    private fun applyColoredMenuIcons() {
        val menu = navigationView.menu

        val coloredIcons = mapOf(
            R.id.nav_home to R.drawable.ic_home,
            R.id.nav_cartable to R.drawable.ic_cartable,
            R.id.nav_personnel_report to R.drawable.ic_personnel_report,
            R.id.nav_reports to R.drawable.ic_reports,
            R.id.nav_archive to R.drawable.ic_archive,
            R.id.nav_work_orders to R.drawable.ic_work_orders,
            R.id.nav_settings to R.drawable.ic_settings
        )

        coloredIcons.forEach { (itemId, drawableRes) ->
            val menuItem = menu.findItem(itemId) ?: return@forEach

            // عنوان رو قبل از خالی کردن ذخیره کن
            val titleText = menuItem.title.toString()

            val actionView = LayoutInflater.from(this).inflate(
                R.layout.nav_item_with_icon, null
            )
            val iconView = actionView.findViewById<ImageView>(R.id.navIcon)
            val titleView = actionView.findViewById<TextView>(R.id.navTitle)

            iconView.setImageResource(drawableRes)
            titleView.text = titleText

            menuItem.actionView = actionView
            menuItem.title = ""   // ← جلوگیری از تکرار متن

            actionView.setOnClickListener {
                onNavigationItemSelected(itemId)
            }
        }
    }

    private fun onNavigationItemSelected(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> { /* همین صفحه — کاری نکن */ }
            R.id.nav_cartable -> startActivity(Intent(this, CartableActivity::class.java))
            R.id.nav_personnel_report -> startActivity(Intent(this, PersonnelReportActivity::class.java))
            R.id.nav_reports -> startActivity(Intent(this, ReportsActivity::class.java))
            R.id.nav_archive -> startActivity(Intent(this, ArchiveActivity::class.java))
            R.id.nav_work_orders -> startActivity(Intent(this, WorkOrderListActivity::class.java))
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        drawerLayout.closeDrawer(navigationView)
    }

    // =========================================================================
    // Network
    // =========================================================================
    private fun registerNetworkCallback() {
        if (isNetworkCallbackRegistered) return
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isNetworkCallbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "registerNetworkCallback error: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        if (!isNetworkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "unregisterNetworkCallback error: ${e.message}")
        } finally {
            isNetworkCallbackRegistered = false
        }
    }

    private fun checkNetworkStatusInitially() {
        val active = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(active)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        updateNetworkStatusUI(isOnline)
    }

    private fun updateNetworkStatusUI(isOnline: Boolean) {
        if (!::tvNetworkStatus.isInitialized) return

        if (isOnline) {
            tvNetworkStatus.text = "🟢 آنلاین"
            tvNetworkStatus.setTextColor(Color.parseColor("#2E7D32"))

            tvLastOnlineLabel.visibility = TextView.GONE
            tvLastOnlineTime.visibility = TextView.GONE
        } else {
            tvNetworkStatus.text = "🔴 آفلاین"
            tvNetworkStatus.setTextColor(Color.parseColor("#C62828"))

            val prefs = getSharedPreferences(NET_PREFS, MODE_PRIVATE)
            val lastTime = prefs.getLong(KEY_LAST_ONLINE, 0L)

            if (lastTime > 0L) {
                tvLastOnlineLabel.visibility = TextView.VISIBLE
                tvLastOnlineTime.visibility = TextView.VISIBLE
                tvLastOnlineTime.text = formatTimestamp(lastTime)
            } else {
                tvLastOnlineLabel.visibility = TextView.GONE
                tvLastOnlineTime.visibility = TextView.GONE
            }
        }
    }

    private fun saveLastOnlineTime() {
        getSharedPreferences(NET_PREFS, MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ONLINE, System.currentTimeMillis())
            .apply()
    }

    private fun formatTimestamp(millis: Long): String {
        return try {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
            val shamsiDate = DateUtils.gregorianToShamsi(calendar)
            val sdf = SimpleDateFormat("HH:mm", Locale.ENGLISH)
            val time = sdf.format(Date(millis))
            "$shamsiDate $time"
        } catch (e: Exception) {
            ""
        }
    }

    // =========================================================================
    // User Data
    // =========================================================================
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

            // Fix یه‌بارمصرف: scheduledDate="null" → null
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(this@HomeActivity)
                val all = db.taskDao().getAllOnce()
                var fixed = 0
                all.forEach { task ->
                    if (task.scheduledDate == "null" || task.scheduledDate == "NULL") {
                        db.taskDao().update(task.copy(scheduledDate = null))
                        fixed++
                    }
                }
                if (fixed > 0) Log.d("FIX_SCHEDULED", "Fixed $fixed tasks")
            }

            TaskSyncer.syncTasksFromServer(this, userId) { count ->
                Log.d(TAG, "Synced $count tasks to Room")
                WorkOrderSyncer.syncWorkOrdersFromServer(this) { woCount ->
                    Log.d(TAG, "Synced $woCount work orders to Room")
                }
            }
        }
    }

    // =========================================================================
    // QuickLog card
    // =========================================================================
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

        setQuickLogCardEnabled(false)

        responsibleTasksJob?.cancel()
        responsibleTasksJob = lifecycleScope.launch {
            taskLocalRepository.getResponsibleTasks(currentUserId).collectLatest { tasks ->
                val hasTasks = tasks.isNotEmpty()
                setQuickLogCardEnabled(hasTasks)
                Log.d(TAG, "Responsible tasks: ${tasks.size}, card enabled: $hasTasks")
            }
        }
    }

    // =========================================================================
    // Click listeners
    // =========================================================================
    private fun setupClickListeners() {
        cardCartable.setOnClickListener {
            startActivity(Intent(this, CartableActivity::class.java))
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

    // =========================================================================
    // Helpers
    // =========================================================================
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun disableCards(disable: Boolean) {
        val cards = listOf(cardQuickLog, cardCartable, cardPersonnelReport)
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