package com.workshop.mechanicbb1.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * وضعیت sync برای نمایش در UI.
 *
 * حالت‌ها:
 *   Idle      → هنوز sync شروع نشده
 *   Offline   → نت نداریم
 *   Syncing   → در حال sync
 *   Success   → sync موفق
 *   Error     → نت داریم ولی sync fail شد
 */
sealed class SyncState {
    object Idle : SyncState()
    object Offline : SyncState()
    data class Syncing(val step: String) : SyncState()
    data class Success(val timestamp: Long) : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * مدیر sync کامل — DeltaSyncer (همه‌ی جداول) + UserSyncer
 *
 * جریان:
 *   1. چک نت → اگه نبود → Offline
 *   2. DeltaSyncer → اگه fail → Error
 *   3. UserSyncer → Success
 */
object SyncManager {

    private const val TAG = "SyncManager"
    private const val PREF_NAME = "sync_prefs"
    private const val KEY_LAST_SYNC = "last_sync_time"

    /**
     * اجرای sync کامل.
     * @param onState callback برای UI — هر مرحله صدا زده می‌شه
     */
    fun syncAll(context: Context, onState: (SyncState) -> Unit) {
        // ۱. چک نت
        if (!isNetworkAvailable(context)) {
            Log.w(TAG, "No network — offline")
            onState(SyncState.Offline)
            return
        }

        onState(SyncState.Syncing("در حال همگام‌سازی..."))

        // ۲. DeltaSync (همه‌ی جداول)
        DeltaSyncer.syncDelta(context) { result ->
            Log.d(TAG, "Delta: success=${result.success}, tasks=${result.tasksCount}, reports=${result.reportsCount}")

            if (!result.success) {
                onState(SyncState.Error("خطا در همگام‌سازی"))
                return@syncDelta
            }

            // ۳. UserSync
            UserSyncer.syncUsersFromServer(context) { userCount ->
                Log.d(TAG, "Users synced: $userCount")

                val timestamp = System.currentTimeMillis()
                saveLastSyncTime(context, timestamp)
                onState(SyncState.Success(timestamp))
            }
        }
    }

    /**
     * چک اتصال واقعی به اینترنت (نه فقط وصل بودن به Wi-Fi).
     * NET_CAPABILITY_VALIDATED → یعنی اینترنت واقعاً کار می‌کنه.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "isNetworkAvailable error: ${e.message}")
            false
        }
    }

    /**
     * ذخیره‌ی زمان آخرین sync موفق.
     */
    fun saveLastSyncTime(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC, timestamp)
            .apply()
    }

    /**
     * خوندن زمان آخرین sync موفق.
     * @return timestamp (0 = هنوز sync نشده)
     */
    fun getLastSyncTime(context: Context): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)
    }
}