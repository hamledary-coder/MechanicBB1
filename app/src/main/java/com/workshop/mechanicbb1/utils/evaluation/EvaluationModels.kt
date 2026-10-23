package com.workshop.mechanicbb1.utils.evaluation

/**
 * مدل‌های داده‌ای ماژول ارزیابی
 * تمام مدل‌های مربوط به ارزیابی در اینجا متمرکز شده‌اند
 */
object EvaluationModels {

    /**
     * ضرایب ارزیابی - از سرور دریافت می‌شود
     */
    data class Coefficients(
        val physicalDifficulty: Int = 25,   // حداکثر درصد سختی فیزیکی
        val technicalComplexity: Int = 30,  // حداکثر درصد پیچیدگی فنی
        val temperature: Int = 15,          // حداکثر درصد دما
        val pollution: Int = 20             // حداکثر درصد آلودگی
    )

    /**
     * شاخص‌های یک نفر در یک بازه زمانی
     */
    data class PersonIndicators(
        val userId: String,
        val userName: String,
        // ساعت خام
        val rawResponsibility: Double = 0.0,   // ساعت مسئولیت
        val rawTechnical: Double = 0.0,        // ساعت کار فنی
        val rawPhysical: Double = 0.0,         // ساعت کار فیزیکی
        val rawInspection: Double = 0.0,       // ساعت کار بررسی
        // ساعت با ضریب
        val finalResponsibility: Double = 0.0,
        val finalTechnical: Double = 0.0,
        val finalPhysical: Double = 0.0,
        val finalInspection: Double = 0.0
    ) {
        // تبدیل به فرمت نمایشی برای UI
        fun toDisplayFormat(): DisplayPersonIndicators {
            return DisplayPersonIndicators(
                userId = userId,
                userName = userName,
                rawResponsibility = formatDuration(rawResponsibility),
                rawTechnical = formatDuration(rawTechnical),
                rawPhysical = formatDuration(rawPhysical),
                rawInspection = formatDuration(rawInspection),
                finalResponsibility = formatDuration(finalResponsibility),
                finalTechnical = formatDuration(finalTechnical),
                finalPhysical = formatDuration(finalPhysical),
                finalInspection = formatDuration(finalInspection)
            )
        }

        private fun formatDuration(minutes: Double): String {
            val hours = (minutes / 60).toInt()
            val mins = (minutes % 60).toInt()
            return String.format("%02d:%02d", hours, mins)
        }
    }

    /**
     * شاخص‌های یک نفر به فرمت نمایشی (رشته‌ای)
     * برای استفاده در Adapterها و UI
     */
    data class DisplayPersonIndicators(
        val userId: String,
        val userName: String,
        val rawResponsibility: String,
        val rawTechnical: String,
        val rawPhysical: String,
        val rawInspection: String,
        val finalResponsibility: String,
        val finalTechnical: String,
        val finalPhysical: String,
        val finalInspection: String
    )

    /**
     * ضرایب محاسبه‌شده برای یک گزارش
     */
    data class LogCoefficients(
        val physicalCoeff: Double,      // ضریب سختی فیزیکی
        val technicalCoeff: Double,     // ضریب پیچیدگی فنی
        val tempCoeff: Double,          // ضریب دما
        val pollutionCoeff: Double,     // ضریب آلودگی
        val physicalTotalCoeff: Double, // مجموع ضرایب فیزیکی (سختی + دما + آلودگی)
        val fullCoeff: Double           // مجموع همه ضرایب (فیزیکی + فنی)
    )

    /**
     * نتیجه ارزیابی یک گزارش برای همه نفرات
     */
    data class LogEvaluationResult(
        val logId: String,
        val taskId: String,
        val responsibleId: String,      // کسی که گزارش را ثبت کرده
        val workType: String,
        val durationMinutes: Int,
        val coefficients: LogCoefficients,
        val userIndicators: Map<String, PersonIndicators>  // userId → شاخص‌ها
    )

    /**
     * گزارش کارکرد پرسنل (برای نمایش در PersonnelReportActivity)
     */
    data class PersonnelReport(
        val userId: String,
        val userName: String,
        val rawResponsibility: String,
        val rawTechnical: String,
        val rawPhysical: String,
        val rawInspection: String,
        val finalResponsibility: String,
        val finalTechnical: String,
        val finalPhysical: String,
        val finalInspection: String
    )
}