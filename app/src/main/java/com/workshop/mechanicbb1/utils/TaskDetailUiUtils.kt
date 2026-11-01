package com.workshop.mechanicbb1.ui.task.detail.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object TaskDetailUiUtils {

    fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

    fun getColorForUserId(userId: String): Int {
        val colors = listOf(
            "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
            "#2196F3", "#03A9F4", "#00BCD4", "#009688",
            "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
            "#FFC107", "#FF9800", "#FF5722", "#795548"
        )
        val index = userId.hashCode().mod(colors.size)
        return Color.parseColor(colors[index])
    }

    fun calculateDurationText(startTime: String, endTime: String): String {
        if (startTime.isEmpty() || endTime.isEmpty()) return ""
        return try {
            val startHour = startTime.split(":")[0].toInt()
            val startMinute = startTime.split(":")[1].toInt()
            val endHour = endTime.split(":")[0].toInt()
            val endMinute = endTime.split(":")[1].toInt()

            var durationMinutes = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
            if (durationMinutes < 0) durationMinutes += 24 * 60

            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60

            when {
                hours > 0 && minutes > 0 -> "مدت زمان: $hours ساعت و $minutes دقیقه"
                hours > 0 && minutes == 0 -> "مدت زمان: $hours ساعت"
                hours == 0 && minutes > 0 -> "مدت زمان: $minutes دقیقه"
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun calculateDurationInMinutes(startTime: String, endTime: String): Int {
        try {
            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            if (startParts.size == 2 && endParts.size == 2) {
                val startHour = startParts[0].toInt()
                val startMinute = startParts[1].toInt()
                val endHour = endParts[0].toInt()
                val endMinute = endParts[1].toInt()
                var duration = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
                if (duration < 0) duration += 24 * 60
                return duration
            }
        } catch (e: Exception) { }
        return 0
    }

    fun createSeenByCircle(
        context: Context,
        letter: String,
        userId: String,
        fullName: String,
        sizeDp: Int
    ): View {
        return TextView(context).apply {
            text = letter
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER

            val size = sizeDp.dpToPx()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = 4.dpToPx()
            }

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getColorForUserId(userId))
            }

            if (fullName.isNotEmpty() && fullName != "نامشخص") {
                setOnLongClickListener {
                    Toast.makeText(context, fullName, Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }
}