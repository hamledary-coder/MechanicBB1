package com.workshop.mechanicbb1.notifications

import android.content.Context
import com.workshop.mechanicbb1.data.remote.Config
import java.util.Calendar

object WorkScheduleManager {

    // ساعات کاری: ۶ صبح تا ۱۲ شب (پیوسته)
    private const val WORK_START_HOUR = 6
    private const val WORK_END_HOUR = 24

    fun shouldPollNow(context: Context): Boolean {
        if (!isActiveWeek(context)) {
            return false
        }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour in WORK_START_HOUR until WORK_END_HOUR
    }

    private fun isActiveWeek(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
        return sharedPref.getBoolean("is_active_week", true)
    }

    fun setActiveWeek(context: Context, isActive: Boolean) {
        val sharedPref = context.getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("is_active_week", isActive).apply()
    }

    fun getNextPollingDelayMinutes(context: Context): Long {
        if (!isActiveWeek(context)) {
            return 60L * 24
        }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (currentHour in WORK_START_HOUR until WORK_END_HOUR) {
            15L
        } else {
            // محاسبه تا ساعت ۶ صبح
            val hoursUntil = (24 - currentHour) + WORK_START_HOUR
            (hoursUntil * 60).toLong()
        }
    }
}