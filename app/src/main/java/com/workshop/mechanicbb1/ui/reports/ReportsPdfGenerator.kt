package com.workshop.mechanicbb1.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.ReportGroup
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.DateUtils
import com.workshop.mechanicbb1.utils.UserCache
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

object ReportsPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_RIGHT = 50f
    private const val MARGIN_LEFT = 50f
    private const val LINE_WIDTH = 495f

    private const val PDF_FILE_NAME = "report_temp.pdf"

    fun generateReportPdf(
        context: Context,
        reportGroups: List<ReportGroup>,
        currentTab: Int,
        startDate: String,
        endDate: String
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // ========== Paint‌ها ==========
            val titlePaint = Paint().apply {
                textSize = 22f
                color = Color.BLACK
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Align.RIGHT
            }
            val subtitlePaint = Paint().apply {
                textSize = 16f
                color = Color.DKGRAY
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Align.RIGHT
            }
            val headerPaint = Paint().apply {
                textSize = 14f
                color = Color.parseColor("#1565C0")
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Align.RIGHT
            }
            val bodyPaint = Paint().apply {
                textSize = 12f
                color = Color.BLACK
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textAlign = Align.RIGHT
            }
            val smallPaint = Paint().apply {
                textSize = 10f
                color = Color.GRAY
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textAlign = Align.RIGHT
            }
            val logHeaderPaint = Paint().apply {
                textSize = 13f
                color = Color.parseColor("#1976D2")
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Align.RIGHT
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            // ✅ Paint جدید برای شرح کار (کوچکتر و کمرنگ‌تر)
            val descriptionPaint = Paint().apply {
                textSize = 11f
                color = Color.parseColor("#555555")
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textAlign = Align.RIGHT
            }

            var yPosition = MARGIN_RIGHT + 20f
            var pageNumber = 1

            // ========== 1. سربرگ ==========
            val tabName = if (currentTab == 0) "گزارش روزانه" else "گزارش هفتگی"
            canvas.drawText("📊  $tabName", PAGE_WIDTH - MARGIN_RIGHT, yPosition, titlePaint)
            yPosition += 35f

            val startShamsi = DateUtils.toPersianNumber(startDate)
            val endShamsi = DateUtils.toPersianNumber(endDate)
            canvas.drawText("📅 بازه زمانی: $startShamsi تا $endShamsi", PAGE_WIDTH - MARGIN_RIGHT, yPosition, subtitlePaint)
            yPosition += 25f

            val currentDate = DateUtils.toPersianNumber(DateUtils.gregorianToShamsi())
            canvas.drawText("🖨️ تاریخ چاپ: $currentDate", PAGE_WIDTH - MARGIN_RIGHT, yPosition, smallPaint)
            yPosition += 30f

            canvas.drawLine(MARGIN_LEFT, yPosition, PAGE_WIDTH - MARGIN_RIGHT, yPosition, linePaint)
            yPosition += 25f

            // ========== 2. خلاصه آماری ==========
            val totalTasks = reportGroups.size
            val totalLogs = reportGroups.sumOf { it.logs.size }

            canvas.drawText("📋 خلاصه آماری", PAGE_WIDTH - MARGIN_RIGHT, yPosition, subtitlePaint)
            yPosition += 28f

            canvas.drawText("• تعداد کل کارها: ${DateUtils.toPersianNumber(totalTasks.toString())}",
                PAGE_WIDTH - MARGIN_RIGHT - 10f, yPosition, bodyPaint)
            yPosition += 22f
            canvas.drawText("• تعداد کل گزارشات: ${DateUtils.toPersianNumber(totalLogs.toString())}",
                PAGE_WIDTH - MARGIN_RIGHT - 10f, yPosition, bodyPaint)
            yPosition += 22f

            canvas.drawLine(MARGIN_LEFT, yPosition, PAGE_WIDTH - MARGIN_RIGHT, yPosition, linePaint)
            yPosition += 25f

            // ========== 3. لیست کارها ==========
            for ((index, reportGroup) in reportGroups.withIndex()) {

                // ✅ فیلتر گزارشات بر اساس تب
                val logsToShow = if (currentTab == 0) {
                    // روزانه: فقط گزارشات امروز
                    reportGroup.logs.filter { it.date == startDate }
                } else {
                    // هفتگی: همه گزارشات
                    reportGroup.logs
                }

                // اگر هیچ گزارشی برای نمایش وجود نداشت، این کارت رو نمایش نده
                if (logsToShow.isEmpty()) {
                    continue
                }

                // محاسبه فضای مورد نیاز (با logsToShow)
                val titleLines = countLines("${index + 1}. کار #${reportGroup.taskId} - ${reportGroup.taskTitle}", headerPaint)
                val descLines = if (reportGroup.taskDescription.isNotEmpty()) {
                    countLines(reportGroup.taskDescription, descriptionPaint)
                } else 0
                val logLines = logsToShow.sumOf { countLines("${it.date} ${it.startTime} | ${it.actionDescription} (${it.userName})", bodyPaint) }
                val spaceNeeded = 70 + (titleLines * 25) + (descLines * 22) + (logLines * 22) + (logsToShow.size * 15)

                if (yPosition + spaceNeeded > PAGE_HEIGHT - 80) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = MARGIN_RIGHT + 20f
                    pageNumber++
                    canvas.drawText("صفحه ${DateUtils.toPersianNumber(pageNumber.toString())}",
                        PAGE_WIDTH - MARGIN_RIGHT, PAGE_HEIGHT - 25f, smallPaint)
                }

                // ===== هدر کارت =====
                val headerPaintBg = Paint().apply {
                    color = Color.parseColor("#FFF3E0")  // ← نارنجی ملایم (به جای خاکستری)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    MARGIN_LEFT, yPosition - 18f,
                    PAGE_WIDTH - MARGIN_RIGHT, yPosition + 12f,
                    headerPaintBg
                )

// عنوان کار
                val taskTitle = "${DateUtils.toPersianNumber((index + 1).toString())}. کار #${DateUtils.toPersianNumber(reportGroup.taskId)} - ${reportGroup.taskTitle}"
                val taskTitleLines = splitTextWithFullWords(taskTitle, headerPaint, LINE_WIDTH - 20f)
                for (line in taskTitleLines) {
                    canvas.drawText(line, PAGE_WIDTH - MARGIN_RIGHT - 8f, yPosition, headerPaint)
                    yPosition += 25f
                }

                // ===== شرح کار (با فونت کوچکتر و کمرنگ‌تر) =====
                if (reportGroup.taskDescription.isNotEmpty()) {
                    val descLines2 = splitTextWithFullWords(reportGroup.taskDescription, descriptionPaint, LINE_WIDTH - 20f)
                    for (line in descLines2) {
                        canvas.drawText("📌 $line", PAGE_WIDTH - MARGIN_RIGHT - 8f, yPosition, descriptionPaint)
                        yPosition += 20f
                    }
                }

                // ===== وضعیت و مسئول =====
                val statusText = "وضعیت: ${Config.StatusCode.getText(reportGroup.taskStatus)}"
                val responsibleName = UserCache.getName(reportGroup.taskResponsible)
                val responsibleText = "مسئول: $responsibleName"

                // ✅ Paint جدید با فونت کوچکتر و رنگ ملایم‌تر
                val infoPaint = Paint().apply {
                    textSize = 10f
                    color = Color.parseColor("#777777")
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    textAlign = Align.RIGHT
                }
                canvas.drawText("$statusText | $responsibleText",
                    PAGE_WIDTH - MARGIN_RIGHT - 8f, yPosition, infoPaint)
                yPosition += 20f  // ← فاصله کمتر

                // ===== خط جداکننده =====
                yPosition += 5f
                canvas.drawLine(
                    MARGIN_LEFT + 20f, yPosition,
                    PAGE_WIDTH - MARGIN_RIGHT - 20f, yPosition,
                    linePaint
                )
                yPosition += 15f

                // ===== لیست گزارشات =====
                if (logsToShow.isNotEmpty()) {
                    canvas.drawText("📝 گزارشات:", PAGE_WIDTH - MARGIN_RIGHT - 8f, yPosition, logHeaderPaint)
                    yPosition += 22f

                    // ✅ مرتب‌سازی گزارشات از قدیم به جدید
                    val sortedLogs = logsToShow.sortedBy { it.date + it.startTime }

                    for ((logIndex, log) in sortedLogs.withIndex()) {
                        // ✅ بک‌گراند کمرنگ برای تاریخ و ساعت
                        val bgPaint = Paint().apply {
                            color = Color.parseColor("#F0F4FA")
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(
                            MARGIN_LEFT + 20f, yPosition - 16f,
                            PAGE_WIDTH - MARGIN_RIGHT - 20f, yPosition + 4f,
                            bgPaint
                        )

                        // ✅ دریافت نام روز هفته
                        val dayName = DateUtils.getDayNameFromDate(log.date)
                        val logDate = DateUtils.toPersianNumber(log.date)
                        val logTime = DateUtils.toPersianNumber(log.startTime)

                        // خط اول: نام روز + تاریخ + ساعت (با فونت Bold)
                        val dateTimePaint = Paint().apply {
                            textSize = 12f
                            color = Color.parseColor("#1565C0")
                            typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            textAlign = Align.RIGHT
                        }
                        val dateTimeText = "$dayName $logDate $logTime"
                        canvas.drawText(dateTimeText, PAGE_WIDTH - MARGIN_RIGHT - 30f, yPosition, dateTimePaint)

                        // ادامه شرح اقدام (از خط بعدی)
                        val actionText = log.actionDescription
                        val actionLines = splitTextWithFullWords(actionText, bodyPaint, LINE_WIDTH - 40f)
                        var tempY = yPosition + 18f
                        for (line in actionLines) {
                            canvas.drawText("  $line", PAGE_WIDTH - MARGIN_RIGHT - 30f, tempY, bodyPaint)
                            tempY += 18f
                        }

                        // ✅ خط دوم: نام کاربر + گروه انجام‌دهنده
                        val groupNames = getGroupNames(log.assignedUsers)
                        val userAndGroupText = "👤 ${log.userName} | 👥 $groupNames"

                        val infoPaint = Paint().apply {
                            textSize = 10f
                            color = Color.parseColor("#666666")
                            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            textAlign = Align.RIGHT
                        }
                        canvas.drawText(userAndGroupText, PAGE_WIDTH - MARGIN_RIGHT - 30f, tempY + 4f, infoPaint)
                        tempY += 18f

                        yPosition = tempY + 4f

                        // خط جداکننده بین گزارشات (به جز آخرین گزارش)
                        if (logIndex < sortedLogs.size - 1) {
                            val dividerPaint = Paint().apply {
                                color = Color.parseColor("#E8E8E8")
                                strokeWidth = 0.8f
                            }
                            canvas.drawLine(
                                MARGIN_LEFT + 40f, yPosition + 4f,
                                PAGE_WIDTH - MARGIN_RIGHT - 40f, yPosition + 4f,
                                dividerPaint
                            )
                            yPosition += 12f
                        }
                    }
                }

                yPosition += 15f
                canvas.drawLine(
                    MARGIN_LEFT + 20f, yPosition,
                    PAGE_WIDTH - MARGIN_RIGHT - 20f, yPosition,
                    linePaint
                )
                yPosition += 20f
            }

            pdfDocument.finishPage(page)

            val fileName = "Report_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, PDF_FILE_NAME)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            file

        } catch (e: Exception) {
            Log.e("ReportsPdfGenerator", "Error generating PDF: ${e.message}", e)
            null
        }
    }

    // ========== تابع شکستن متن با کلمات کامل و پشتیبانی از \n ==========
    private fun splitTextWithFullWords(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()

        // ✅ اول متن رو بر اساس \n تکه‌تکه کن
        val paragraphs = text.split("\n")

        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                // اگر پاراگراف خالی بود، یک خط خالی اضافه کن
                lines.add("")
                continue
            }

            val words = paragraph.split(" ")
            var currentLine = ""
            var currentWidth = 0f

            for (word in words) {
                val wordWidth = paint.measureText(word)

                // اگر کلمه به تنهایی از عرض خط بیشتره
                if (wordWidth > maxWidth) {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                        currentLine = ""
                        currentWidth = 0f
                    }
                    // کلمه رو حرف به حرف بشکن
                    val chars = word.toCharArray()
                    var charLine = ""
                    var charWidth = 0f
                    for (char in chars) {
                        val charMeasure = paint.measureText(char.toString())
                        if (charWidth + charMeasure > maxWidth && charLine.isNotEmpty()) {
                            lines.add(charLine)
                            charLine = ""
                            charWidth = 0f
                        }
                        charLine += char
                        charWidth += charMeasure
                    }
                    if (charLine.isNotEmpty()) {
                        lines.add(charLine)
                    }
                    continue
                }

                // اگر کلمه به خط فعلی اضافه نشه
                if (currentWidth + wordWidth > maxWidth && currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = ""
                    currentWidth = 0f
                }

                // اضافه کردن کلمه به خط
                if (currentLine.isEmpty()) {
                    currentLine = word
                    currentWidth = wordWidth
                } else {
                    currentLine += " $word"
                    currentWidth += wordWidth + paint.measureText(" ")
                }
            }

            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }

        return if (lines.isEmpty()) listOf(text) else lines
    }

    private fun countLines(text: String, paint: Paint): Int {
        if (text.isEmpty()) return 0
        return splitTextWithFullWords(text, paint, LINE_WIDTH - 20f).size
    }

    fun sharePdf(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "ذخیره یا اشتراک‌گذاری PDF"))
            true
        } catch (e: Exception) {
            Log.e("ReportsPdfGenerator", "Error sharing PDF: ${e.message}", e)
            false
        }
    }



    // ========== تابع دریافت نام گروه انجام‌دهنده ==========
    private fun getGroupNames(assignedUsers: String): String {
        if (assignedUsers.isEmpty() || assignedUsers == "0") {
            return "تعیین نشده"
        }

        val ids = assignedUsers.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "0" }
        if (ids.isEmpty()) {
            return "تعیین نشده"
        }

        val names = ids.mapNotNull { id ->
            val name = UserCache.getName(id)
            if (name != "نامشخص" && !name.startsWith("کاربر")) {
                name
            } else {
                null
            }
        }

        return if (names.isNotEmpty()) {
            names.joinToString("، ")
        } else {
            ids.joinToString("، ")
        }
    }
}