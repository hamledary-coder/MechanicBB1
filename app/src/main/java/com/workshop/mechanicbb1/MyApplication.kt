package com.workshop.mechanicbb1

import android.app.Application
import com.workshop.mechanicbb1.notifications.NotificationPermissionManager
import com.workshop.mechanicbb1.notifications.NotificationPoller

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ساخت کانال نوتیفیکیشن
        NotificationPermissionManager.createNotificationChannel(this)  // ← مفرد

        // شروع Polling
        NotificationPoller.start(this)
    }
}