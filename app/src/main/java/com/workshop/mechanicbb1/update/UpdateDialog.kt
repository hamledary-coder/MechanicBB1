package com.workshop.mechanicbb1.update

import android.app.Activity
import androidx.appcompat.app.AlertDialog

object UpdateDialog {

    fun showAvailable(
        activity: Activity,
        info: UpdateManager.VersionInfo,
        onDownload: (UpdateManager.VersionInfo) -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("به‌روزرسانی ${info.versionName}")
            .setMessage(info.changelog.ifBlank { "نسخه جدیدی از برنامه منتشر شده است." })
            .setPositiveButton("دانلود") { _, _ -> onDownload(info) }
            .setNegativeButton("بعداً", null)
            .show()
    }

    fun showForce(
        activity: Activity,
        info: UpdateManager.VersionInfo,
        onDownload: (UpdateManager.VersionInfo) -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("به‌روزرسانی اجباری")
            .setMessage(
                info.changelog.ifBlank {
                    "برای ادامه استفاده، لطفاً برنامه را به‌روزرسانی کنید."
                }
            )
            .setCancelable(false)
            .setPositiveButton("دانلود") { _, _ -> onDownload(info) }
            .show()
    }

    fun showReadyToInstall(
        activity: Activity,
        onInstall: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("نصب به‌روزرسانی")
            .setMessage("فایل به‌روزرسانی دانلود شد. اکنون نصب شود؟")
            .setPositiveButton("نصب") { _, _ -> onInstall() }
            .setNegativeButton("بعداً", null)
            .show()
    }
}