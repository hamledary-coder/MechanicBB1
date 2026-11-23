package com.workshop.mechanicbb1

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.workshop.mechanicbb1.data.sync.ReportSyncWorker
import com.workshop.mechanicbb1.notifications.NotificationPermissionManager
import com.workshop.mechanicbb1.notifications.NotificationPoller

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ قفل کردن حالت روشن
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // ساخت کانال نوتیفیکیشن
        NotificationPermissionManager.createNotificationChannel(this)

        // شروع Polling
        NotificationPoller.start(this)

        // ✅ شروع سینک دوره‌ای (هر ۱۵ دقیقه)
        ReportSyncWorker.enqueuePeriodic(this)
    }
}