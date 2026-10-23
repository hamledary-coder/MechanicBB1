package com.workshop.mechanicbb1.utils.chart

import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter

object ChartHelper {

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianNumber(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            if (c in '0'..'9') sb.append(persianDigits[c - '0'])
            else sb.append(c)
        }
        return sb.toString()
    }

    fun decimalHourToHHMM(decimalHour: Float): String {
        val totalMinutes = (decimalHour * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return toPersianNumber(String.format("%02d:%02d", h, m))
    }

    fun decimalHourToSimple(decimalHour: Float): String {
        val s = if (decimalHour == decimalHour.toInt().toFloat()) {
            decimalHour.toInt().toString()
        } else {
            String.format("%.1f", decimalHour)
        }
        return toPersianNumber(s)
    }

    fun applyBaseSettings(chart: BarChart, sectionColor: Int) {
        // لمس و زوم
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.isDoubleTapToZoomEnabled = false

        // ظاهر
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setDrawBarShadow(false)
        chart.setDrawValueAboveBar(true)

        // ✅ مهم: setFitBars(false) → میله‌ها از صفر شروع می‌شن
        chart.setFitBars(false)

        chart.setExtraOffsets(8f, 16f, 8f, 8f)
        chart.animateY(600)

        // ✅ محور Y چپ (سمت چپ صفحه)
        val yAxis = chart.axisLeft
        yAxis.isEnabled = true
        yAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)  // ← بیرون از نمودار
        yAxis.setDrawGridLines(true)
        yAxis.gridColor = Color.parseColor("#EEEEEE")
        yAxis.axisLineColor = Color.parseColor("#CCCCCC")
        yAxis.textColor = Color.parseColor("#666666")
        yAxis.textSize = 10f
        yAxis.axisMinimum = 0f
        yAxis.spaceBottom = 0f
        yAxis.spaceTop = 0.15f
        yAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return decimalHourToSimple(value)
            }
        }

        // ✅ محور Y راست غیرفعال
        chart.axisRight.isEnabled = false

        // محور X
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -45f
        xAxis.textSize = 10f
        xAxis.textColor = Color.parseColor("#333333")
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.yOffset = 6f

        // ✅ اینا رو اضافه کن:
        xAxis.setCenterAxisLabels(true)   // ← مهم‌ترین!
        xAxis.axisMinimum = 0f
        xAxis.setDrawLabels(true)
    }

    fun buildGroupedData(
        names: List<String>,
        rawValues: List<Float>,
        finalValues: List<Float>,
        sectionColor: Int
    ): BarData {
        val rawEntries = rawValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val finalEntries = finalValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }

        val rawSet = BarDataSet(rawEntries, "خام").apply {
            color = Color.parseColor("#BDBDBD")
            valueTextColor = Color.parseColor("#666666")
            valueTextSize = 9f
            setDrawValues(false)
        }

        val finalSet = BarDataSet(finalEntries, "نهایی").apply {
            color = sectionColor
            valueTextColor = sectionColor
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return decimalHourToSimple(value)   // ← تغییر از HHMM به Simple
                }
            }
        }

        val data = BarData(rawSet, finalSet)

        // ✅ گروه‌بندی دستی
        val groupSpace = 0.3f     // فاصله بین گروه‌ها
        val barSpace = 0.05f      // فاصله بین دو میله در یک گروه
        val barWidth = 0.3f       // عرض هر میله

        // فرمول: groupSpace + (barWidth + barSpace) * 2 = 1.0
        // 0.3 + (0.3 + 0.05) * 2 = 0.3 + 0.7 = 1.0 ✅

        data.barWidth = barWidth

        return data
    }
}