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
import android.view.View
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.repository.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.SyncManager
import com.workshop.mechanicbb1.data.sync.SyncState
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

/**
 * صفحه‌ی اصلی برنامه.
 *
 * وظایف:
 *   - نمایش منوی کناری (Drawer) + کارت‌های اصلی
 *   - نمایش وضعیت شبکه (لحظه‌ای) + وضعیت sync (فقط با SyncManager)
 *   - آخرین زمان sync (حتی آفلاین)
 *   - sync خودکار هنگام وصل شدن نت + sync دستی با دکمه
 *   - بررسی مسئول بودن کاربر برای فعال/غیرفعال کردن کارت QuickLog
 */
@Suppress("DEPRECATION")
class HomeActivity : AppCompatActivity() {

    // =========================================================================
    // Views
    // =========================================================================
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var tvWelcome: TextView

    // بخش وضعیت
    private lateinit var tvNetworkStatus: TextView
    private lateinit var tvLastSyncTime: TextView
    private lateinit var btnSync: MaterialButton

    // کارت‌ها
    private lateinit var cardQuickLog: CardView
    private lateinit var cardCartable: CardView
    private lateinit var cardPersonnelReport: CardView

    // =========================================================================
    // Repos / Jobs
    // =========================================================================
    private lateinit var taskLocalRepository: TaskLocalRepository
    private var responsibleTasksJob: Job? = null
<<<<<<< Updated upstream
=======
<<<<<<< Updated upstream
=======
>>>>>>> Stashed changes
    private var syncJob: Job? = null

    // =========================================================================
    // Network
    // =========================================================================
    private lateinit var connectivityManager: ConnectivityManager
    private var isNetworkCallbackRegistered = false
    private var isOnline = false

    private val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            runOnUiThread {
                updateNetworkStatusUI(true)
<<<<<<< Updated upstream
                // sync خودکار وقتی نت وصل شد
                startSync()
=======
                startSync(showProgress = false)   // ✅ بدون پروگرس
>>>>>>> Stashed changes
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            runOnUiThread { updateNetworkStatusUI(false) }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, caps)
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            runOnUiThread { updateNetworkStatusUI(hasInternet) }
        }
    }
<<<<<<< Updated upstream
=======
>>>>>>> Stashed changes
>>>>>>> Stashed changes

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "HomeActivity"
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

        // ✅ پاک‌سازی APK قدیمی اگر نصب انجام شده
        //UpdateManager.cleanupInstalledApk(this)

        taskLocalRepository = TaskLocalRepository(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)

        initViews()
        setupToolbar()
        setupDrawer()
        setupClickListeners()
        loadLastSyncTime()

        setQuickLogCardEnabled(false)

        loadUserData()
        checkNetworkStatusInitially()
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


    @SuppressLint("GestureBackNavigation")
    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
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
        tvLastSyncTime = findViewById(R.id.tvLastSyncTime)
        btnSync = findViewById(R.id.btnSync)

        cardQuickLog = findViewById(R.id.cardQuickLog)
        cardCartable = findViewById(R.id.cardCartable)
        cardPersonnelReport = findViewById(R.id.cardPersonnelReport)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

<<<<<<< Updated upstream
=======
<<<<<<< Updated upstream
=======
>>>>>>> Stashed changes
    // =========================================================================
    // Drawer
    // =========================================================================
    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        // Header
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

        // گزارشات روزانه فقط برای مدیر/سرشیفت
        val isManager = userRole == Config.RoleCode.MANAGER || userRole == Config.RoleCode.SUPERVISOR
        navigationView.menu.findItem(R.id.nav_reports)?.isVisible = isManager

        applyColoredMenuIcons()
        navigationView.setCheckedItem(R.id.nav_home)
    }

    @SuppressLint("InflateParams")
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

            val titleText = menuItem.title.toString()

            val actionView = LayoutInflater.from(this).inflate(
                R.layout.nav_item_with_icon, null
            )
            val iconView = actionView.findViewById<ImageView>(R.id.navIcon)
            val titleView = actionView.findViewById<TextView>(R.id.navTitle)

            iconView.setImageResource(drawableRes)
            titleView.text = titleText

            menuItem.actionView = actionView
            menuItem.title = ""

            actionView.setOnClickListener {
                onNavigationItemSelected(itemId)
            }
        }
    }

    private fun onNavigationItemSelected(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> { /* همین صفحه */ }
            R.id.nav_cartable -> startActivity(Intent(this, CartableActivity::class.java))
            R.id.nav_personnel_report -> startActivity(Intent(this, PersonnelReportActivity::class.java))
            R.id.nav_reports -> startActivity(Intent(this, ReportsActivity::class.java))
            R.id.nav_archive -> startActivity(Intent(this, ArchiveActivity::class.java))
            R.id.nav_work_orders -> startActivity(Intent(this, WorkOrderListActivity::class.java))
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        drawerLayout.closeDrawer(navigationView)
    }

<<<<<<< Updated upstream
=======


>>>>>>> Stashed changes
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
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        updateNetworkStatusUI(online)

<<<<<<< Updated upstream
        // sync خودکار اگه آنلاین بود
        if (online) startSync()
=======
        if (online) startSync(showProgress = false)   // ✅ بدون پروگرس
>>>>>>> Stashed changes
    }

    private fun updateNetworkStatusUI(online: Boolean) {
        isOnline = online

        if (online) {
            tvNetworkStatus.text = "🟢 آنلاین"
            tvNetworkStatus.setTextColor(Color.parseColor("#2E7D32"))
            btnSync.visibility = View.VISIBLE
            btnSync.isEnabled = true
        } else {
            tvNetworkStatus.text = "🔴 آفلاین"
            tvNetworkStatus.setTextColor(Color.parseColor("#C62828"))
            btnSync.visibility = View.GONE
        }
    }

<<<<<<< Updated upstream
    // =========================================================================
    // Sync
    // =========================================================================
    private fun startSync() {
=======


    // =========================================================================
    // Sync
    // =========================================================================
    private fun startSync(showProgress: Boolean = false) {
>>>>>>> Stashed changes
        if (!isOnline) {
            Toast.makeText(this, "اتصال به اینترنت برقرار نیست", Toast.LENGTH_SHORT).show()
            return
        }

<<<<<<< Updated upstream
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            SyncManager.syncAll(this@HomeActivity) { state ->
                runOnUiThread { updateSyncUI(state) }
=======
        // ✅ اگه sync در حال اجراست، دوباره شروع نکن
        if (syncJob?.isActive == true) {
            if (showProgress) {
                Toast.makeText(this, "همگام‌سازی در حال اجراست...", Toast.LENGTH_SHORT).show()
            }
            return
        }
        syncJob = lifecycleScope.launch {
            SyncManager.syncAll(applicationContext) { state ->
                if (isFinishing || isDestroyed) return@syncAll
                runOnUiThread { updateSyncUI(state, showProgress) }
>>>>>>> Stashed changes
            }
        }
    }

    @SuppressLint("SetTextI18n")
<<<<<<< Updated upstream
    private fun updateSyncUI(state: SyncState) {
        when (state) {
            is SyncState.Idle -> {
                progressBar.visibility = View.GONE
            }
            is SyncState.Offline -> {
                progressBar.visibility = View.GONE
            }
            is SyncState.Syncing -> {
                btnSync.isEnabled = false
                progressBar.visibility = View.VISIBLE
            }
            is SyncState.Success -> {
                btnSync.isEnabled = true
                progressBar.visibility = View.GONE
=======
    private fun updateSyncUI(state: SyncState, showProgress: Boolean) {
        when (state) {
            is SyncState.Idle -> {
                if (showProgress) progressBar.visibility = View.GONE
            }
            is SyncState.Offline -> {
                if (showProgress) progressBar.visibility = View.GONE
            }
            is SyncState.Syncing -> {
                if (showProgress) {
                    btnSync.isEnabled = false
                    progressBar.visibility = View.VISIBLE
                }
            }
            is SyncState.Success -> {
                btnSync.isEnabled = true
                if (showProgress) progressBar.visibility = View.GONE
>>>>>>> Stashed changes
                tvLastSyncTime.visibility = View.VISIBLE
                tvLastSyncTime.text = "آخرین: ${formatSyncTime(state.timestamp)}"
            }
            is SyncState.Error -> {
                btnSync.isEnabled = true
<<<<<<< Updated upstream
                progressBar.visibility = View.GONE
=======
                if (showProgress) progressBar.visibility = View.GONE
>>>>>>> Stashed changes
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadLastSyncTime() {
        val lastSync = SyncManager.getLastSyncTime(this)
        if (lastSync > 0) {
            tvLastSyncTime.visibility = View.VISIBLE
            tvLastSyncTime.text = "آخرین: ${formatSyncTime(lastSync)}"
        }
    }

    private fun formatSyncTime(timestamp: Long): String {
        return try {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val shamsiDate = DateUtils.gregorianToShamsi(calendar)
            val sdf = SimpleDateFormat("HH:mm", Locale.ENGLISH)
            val time = sdf.format(Date(timestamp))
            "$shamsiDate $time"
        } catch (e: Exception) {
            ""
        }
    }

<<<<<<< Updated upstream
    // =========================================================================
    // User Data
    // =========================================================================
=======


    // =========================================================================
    // User Data
    // =========================================================================
>>>>>>> Stashed changes
>>>>>>> Stashed changes
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
        }
    }

<<<<<<< Updated upstream
    // =========================================================================
    // QuickLog card
    // =========================================================================
=======
<<<<<<< Updated upstream
    // ====== متد کمکی برای تغییر وضعیت کارت ======
=======


    // =========================================================================
    // QuickLog card
    // =========================================================================
>>>>>>> Stashed changes
>>>>>>> Stashed changes
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
    // Click Listeners
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
<<<<<<< Updated upstream

        btnSync.setOnClickListener {
            startSync()
        }
=======
<<<<<<< Updated upstream
=======

        btnSync.setOnClickListener {
            startSync(showProgress = true)   // ✅ با پروگرس
        }
>>>>>>> Stashed changes
>>>>>>> Stashed changes
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
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