package com.workshop.mechanicbb1.utils

object TimeUtils {

    fun timeToMinutes(time: String): Int {
        if (time.isEmpty() || time == "00:00" || time == "0:00" || time == "0") return 0
        try {
            val parts = time.split(":")
            if (parts.size == 2) {
                return parts[0].toInt() * 60 + parts[1].toInt()
            }
        } catch (e: Exception) {
            // ignore
        }
        return 0
    }

    /**
     * تبدیل زمان HH:MM به ساعت اعشاری
     * مثال: "10:30" -> 10.5
     */
    fun timeToDecimalHour(time: String): Double {
        if (time.isEmpty() || time == "00:00" || time == "0:00" || time == "0") return 0.0
        try {
            val parts = time.split(":")
            if (parts.size == 2) {
                val hours = parts[0].toInt()
                val minutes = parts[1].toInt()
                return hours + (minutes / 60.0)
            }
        } catch (e: Exception) {
            // ignore
        }
        return 0.0
    }
}