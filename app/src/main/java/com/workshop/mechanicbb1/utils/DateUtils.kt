package com.workshop.mechanicbb1.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun formatMessageTime(dateStr: String): String {
        return try {
            val date = inputFormat.parse(dateStr) ?: Date()
            timeFormat.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    // ========== توابع تبدیل تاریخ ==========

    /**
     * تبدیل تاریخ میلادی به شمسی (بدون کتابخانه)
     * دقت: برای سال‌های ۱۳۰۰ تا ۱۴۵۰ دقیق است
     * @param calendar Calendar (اختیاری، پیش‌فرض: امروز)
     * @return تاریخ شمسی به صورت "yyyy/MM/dd" با اعداد انگلیسی
     */
    fun gregorianToShamsi(calendar: Calendar = Calendar.getInstance()): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // محاسبه دقیق روز سال میلادی
        val daysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var dayOfYear = day
        for (i in 1 until month) {
            dayOfYear += daysInMonth[i]
        }

        val isLeapGregorian = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
        if (isLeapGregorian && month > 2) {
            dayOfYear++
        }

        // محاسبه دقیق آفست
        val offset = if (isLeapGregorian) 80 else 79
        var shamsiDayOfYear = dayOfYear - offset
        var shamsiYear = year - 621

        if (shamsiDayOfYear <= 0) {
            shamsiYear--
            // تعداد روزهای سال قبل میلادی
            val prevYear = year - 1
            val prevIsLeap = (prevYear % 4 == 0 && prevYear % 100 != 0) || (prevYear % 400 == 0)
            shamsiDayOfYear += if (prevIsLeap) 366 else 365
        }

        // محاسبه دقیق کبیسه شمسی
        val isLeapShamsi = isShamsiLeapYear(shamsiYear)
        val shamsiMonths = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, if (isLeapShamsi) 30 else 29)

        var remaining = shamsiDayOfYear
        var shamsiMonth = 0
        var shamsiDay = remaining

        for (i in 0 until 12) {
            if (remaining <= shamsiMonths[i]) {
                shamsiMonth = i + 1
                shamsiDay = remaining
                break
            }
            remaining -= shamsiMonths[i]
        }

        return String.format(Locale.ENGLISH, "%d/%02d/%02d", shamsiYear, shamsiMonth, shamsiDay)
    }

    private fun isShamsiLeapYear(year: Int): Boolean {
        // الگوریتم رسمی کبیسه شمسی (بر اساس محاسبات نجومی)
        // یک سال کبیسه است اگر: (year × 0.24219858156) % 1 < 0.24219858156
        return (year * 0.24219858156) % 1 < 0.24219858156
    }

    /**
     * دریافت نام روز هفته به فارسی
     * @param calendar Calendar (اختیاری، پیش‌فرض: امروز)
     * @return نام روز هفته به فارسی
     */
    fun getDayName(calendar: Calendar = Calendar.getInstance()): String {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یک‌شنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنج‌شنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
        }
    }

    /**
     * تبدیل اعداد انگلیسی به فارسی برای نمایش
     */
    fun toPersianNumber(number: String): String {
        val map = mapOf(
            '0' to '۰', '1' to '۱', '2' to '۲', '3' to '۳',
            '4' to '۴', '5' to '۵', '6' to '۶', '7' to '۷',
            '8' to '۸', '9' to '۹'
        )
        return number.map { map[it] ?: it }.joinToString("")
    }


    /**
     * دریافت نام روز هفته از تاریخ شمسی (برای نمایش در UI و PDF)
     * @param shamsiDate تاریخ شمسی به صورت "yyyy/MM/dd"
     * @return نام روز هفته به فارسی
     */
    fun getDayNameFromDate(shamsiDate: String): String {
        if (shamsiDate.isEmpty()) return ""
        try {
            val parts = shamsiDate.split("/")
            if (parts.size != 3) return ""

            val shamsiYear = parts[0].toInt()
            val shamsiMonth = parts[1].toInt()
            val shamsiDay = parts[2].toInt()

            // استفاده از تابع اصلاح شده shamsiToGregorian
            val calendar = shamsiToGregorian(shamsiYear, shamsiMonth, shamsiDay)

            // بررسی اینکه آیا تاریخ به درستی تبدیل شده
            val testShamsi = gregorianToShamsi(calendar)
            return if (testShamsi == shamsiDate) {
                getDayName(calendar)
            } else {
                // اگر خطا داشت، از روش جستجو استفاده کن
                fallbackGetDayNameFromDate(shamsiYear, shamsiMonth, shamsiDay)
            }
        } catch (e: Exception) {
            return ""
        }
    }

    private fun fallbackGetDayNameFromDate(shamsiYear: Int, shamsiMonth: Int, shamsiDay: Int): String {
        // روش قبلی با حلقه ۴۰۰ روزه
        val calendar = Calendar.getInstance()
        calendar.set(shamsiYear + 621, 0, 1)

        var found = false
        var attempts = 0
        while (!found && attempts < 400) {
            val testShamsi = gregorianToShamsi(calendar)
            if (testShamsi == "$shamsiYear/$shamsiMonth/$shamsiDay") {
                found = true
                break
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            attempts++
        }

        return if (found) getDayName(calendar) else ""
    }

    private fun shamsiToGregorian(shamsiYear: Int, shamsiMonth: Int, shamsiDay: Int): Calendar {
        // محاسبه روز سال شمسی
        val daysInShamsiMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30,
            if (isShamsiLeapYear(shamsiYear)) 30 else 29)
        var dayOfYear = shamsiDay
        for (i in 0 until shamsiMonth - 1) {
            dayOfYear += daysInShamsiMonth[i]
        }

        // محاسبه سال میلادی و آفست دقیق
        var gregorianYear = shamsiYear + 621

        // بررسی کبیسه بودن سال میلادی
        val isLeapGregorian = (gregorianYear % 4 == 0 && gregorianYear % 100 != 0) || (gregorianYear % 400 == 0)

        // محاسبه آفست بر اساس سال کبیسه
        var offset = 79
        if (isLeapGregorian && shamsiMonth >= 12 && shamsiDay >= 1) {
            offset = 80
        }

        var gregorianDayOfYear = dayOfYear + offset

        // اگر از سال میلادی گذشت
        val daysInGregorianYear = if (isLeapGregorian) 366 else 365
        if (gregorianDayOfYear > daysInGregorianYear) {
            gregorianDayOfYear -= daysInGregorianYear
            gregorianYear++
        }

        // تبدیل روز سال به تاریخ میلادی
        val calendar = Calendar.getInstance()
        calendar.set(gregorianYear, 0, 1)
        calendar.add(Calendar.DAY_OF_YEAR, gregorianDayOfYear - 1)
        return calendar
    }
}