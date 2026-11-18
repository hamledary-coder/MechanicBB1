package com.workshop.mechanicbb1.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import java.io.File

/**
 * مدیر به‌روزرسانی خودکار اپ.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS = Config.PrefKeys.UPDATE_PREFS

    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String,
        val forceUpdate: Boolean,
        val minSupported: Int
    )

    sealed class CheckResult {
        data object NoUpdate : CheckResult()
        data class Available(val info: VersionInfo) : CheckResult()
        data class Force(val info: VersionInfo) : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    // ======================== API ========================

    fun currentVersionCode(context: Context): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                it.longVersionCode.toInt()
            else @Suppress("DEPRECATION") it.versionCode
        }
    } catch (e: Exception) { 0 }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(intent) }
            catch (e: Exception) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // ======================== 1. بررسی نسخه ========================

    fun check(context: Context, callback: (CheckResult) -> Unit) {
        val url = Config.Endpoints.CHECK_VERSION
        Log.d(TAG, "checkVersion → $url")

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    if (response.optString("status") != "success") {
                        callback(CheckResult.Error("پاسخ نامعتبر"))
                        return@JsonObjectRequest
                    }
                    val info = VersionInfo(
                        versionCode = response.optInt("versionCode", 0),
                        versionName = response.optString("versionName", ""),
                        apkUrl      = response.optString("apkUrl", ""),
                        changelog   = response.optString("changelog", ""),
                        forceUpdate = response.optBoolean("forceUpdate", false),
                        minSupported = response.optInt("minSupported", 0)
                    )

                    val current = currentVersionCode(context)
                    Log.d(TAG, "current=$current, server=${info.versionCode}")

                    prefs(context).edit()
                        .putLong(Config.PrefKeys.LAST_CHECKED_AT, System.currentTimeMillis())
                        .apply()

                    val result = when {
                        info.versionCode <= current -> CheckResult.NoUpdate
                        info.forceUpdate || current < info.minSupported -> CheckResult.Force(info)
                        else -> CheckResult.Available(info)
                    }
                    callback(result)

                } catch (e: Exception) {
                    Log.e(TAG, "parse error", e)
                    callback(CheckResult.Error("خطا در تحلیل پاسخ"))
                }
            },
            { error ->
                Log.e(TAG, "network error", error)
                callback(CheckResult.Error(error.message ?: "خطای شبکه"))
            }
        )

        VolleySingleton.getInstance(context).add(request)
    }

    // ======================== 2. دانلود APK ========================

    fun download(context: Context, info: VersionInfo): Long {
        val apkFileName = "MechanicBB1_${info.versionName}.apk"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        // ✅ پاک‌سازی همه APKهای قدیمی قبل از دانلود جدید
        dir?.listFiles()?.forEach { file ->
            if (file.name.startsWith("MechanicBB1_") && file.name.endsWith(".apk")) {
                runCatching { file.delete() }
                Log.d(TAG, "old apk deleted: ${file.name}")
            }
        }

        val dest = File(dir, apkFileName)

        // اگر همین نسخه قبلاً دانلود شده بود، باز پاک کن (overwrite)
        if (dest.exists()) dest.delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("دانلود نسخه ${info.versionName}")
            setDescription("به‌روزرسانی برنامه تعمیرات مکانیک بی‌بی‌یک")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, apkFileName
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        prefs(context).edit()
            .putString(Config.PrefKeys.PENDING_APK_PATH, dest.absolutePath)
            .putString(Config.PrefKeys.PENDING_APK_VERSION, info.versionName)
            .putString(Config.PrefKeys.PENDING_APK_NAME, apkFileName)
            .putLong("downloadId_$downloadId", System.currentTimeMillis())
            .apply()

        Log.d(TAG, "download enqueued, id=$downloadId, dest=${dest.absolutePath}")
        return downloadId
    }

    /**
     * پاک‌سازی خودکار APK ذخیره‌شده.
     *
     * اگر APK ذخیره‌شده نسخه‌اش <= نسخه فعلی نصب‌شده باشد → یعنی نصب موفق بوده → پاک کن.
     *
     * این متد را در onCreate هر Activity اصلی (HomeActivity) صدا بزن.
     */
    fun cleanupInstalledApk(context: Context) {
        val path = prefs(context).getString(Config.PrefKeys.PENDING_APK_PATH, null)
            ?: return
        val file = File(path)
        if (!file.exists()) {
            // فایل نیست، Prefs را پاک کن
            clearPendingApk(context)
            return
        }

        val apkVersionCode = try {
            val apkInfo = context.packageManager.getPackageArchiveInfo(path, 0)
            if (apkInfo == null) 0
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                apkInfo.longVersionCode.toInt()
            else @Suppress("DEPRECATION") apkInfo.versionCode
        } catch (e: Exception) {
            Log.e(TAG, "getPackageArchiveInfo error", e)
            0
        }

        val currentVersionCode = currentVersionCode(context)
        Log.d(TAG, "cleanup: current=$currentVersionCode, apkOnDisk=$apkVersionCode")

        // اگر APK روی دیسک نسخه‌اش <= نسخه نصب‌شده است → نصب انجام شده → پاک کن
        if (apkVersionCode > 0 && currentVersionCode >= apkVersionCode) {
            Log.d(TAG, "APK already installed, cleaning up: $path")
            clearPendingApk(context)
        }
        // اگر فایل APK نسخه‌اش از نسخه فعلی بزرگ‌تر است، نگهش دار (آماده نصب است)
    }

    // ======================== 3. نصب ========================

    fun installPendingApk(activity: Activity): Boolean {
        if (!canInstallPackages(activity)) {
            openInstallPermissionSettings(activity)
            return false
        }

        val path = prefs(activity).getString(Config.PrefKeys.PENDING_APK_PATH, null)
        if (path.isNullOrEmpty()) {
            Log.e(TAG, "no pending apk path")
            return false
        }
        val file = File(path)
        if (!file.exists()) {
            Log.e(TAG, "apk file missing: $path")
            return false
        }

        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
            Log.d(TAG, "install intent launched")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "install error", e)
            return false
        }
    }

    fun hasPendingApk(context: Context): Boolean {
        val path = prefs(context).getString(Config.PrefKeys.PENDING_APK_PATH, null)
            ?: return false
        return File(path).exists()
    }

    fun clearPendingApk(context: Context) {
        val path = prefs(context).getString(Config.PrefKeys.PENDING_APK_PATH, null)
        path?.let { runCatching { File(it).delete() } }
        prefs(context).edit()
            .remove(Config.PrefKeys.PENDING_APK_PATH)
            .remove(Config.PrefKeys.PENDING_APK_VERSION)
            .remove(Config.PrefKeys.PENDING_APK_NAME)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastCheckedAt(context: Context): Long =
        prefs(context).getLong(Config.PrefKeys.LAST_CHECKED_AT, 0L)

    fun shouldCheck(context: Context, minIntervalMs: Long = 6 * 60 * 60 * 1000L): Boolean {
        return System.currentTimeMillis() - lastCheckedAt(context) > minIntervalMs
    }
}