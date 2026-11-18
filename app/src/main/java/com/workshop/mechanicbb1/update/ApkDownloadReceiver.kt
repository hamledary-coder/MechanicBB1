package com.workshop.mechanicbb1.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.workshop.mechanicbb1.data.remote.Config

class ApkDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val prefs = context.getSharedPreferences(
            Config.PrefKeys.UPDATE_PREFS, Context.MODE_PRIVATE
        )

        val isOurs = prefs.contains("downloadId_$downloadId")
        if (!isOurs) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use {
            if (it.moveToFirst()) {
                val status = it.getInt(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Log.d("ApkDownloadReceiver", "APK downloaded successfully")
                    Toast.makeText(
                        context,
                        "به‌روزرسانی دانلود شد. برای نصب روی دکمه «نصب» بزنید.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Log.w("ApkDownloadReceiver", "download failed, status=$status")
                    Toast.makeText(
                        context, "خطا در دانلود به‌روزرسانی", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        prefs.edit().remove("downloadId_$downloadId").apply()
    }
}