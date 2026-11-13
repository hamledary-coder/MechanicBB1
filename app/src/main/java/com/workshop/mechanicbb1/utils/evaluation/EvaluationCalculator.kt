package com.workshop.mechanicbb1.utils.evaluation

import TaskModel
import android.util.Log
import com.workshop.mechanicbb1.data.model.TaskLogModel
import com.workshop.mechanicbb1.utils.UserCache

/**
 * هسته محاسباتی ماژول ارزیابی
 * تمام فرمول‌ها و محاسبات در اینجا متمرکز شده‌اند
 */
object EvaluationCalculator {

    private const val TAG = "EvaluationCalculator"

    // ============================================================
    // 1. توابع پایه
    // ============================================================

    /**
     * محاسبه ضریب خطی
     * فرمول: (value / maxValue) * maxPercent
     */
    fun calculateCoefficient(value: Int, maxValue: Int, maxPercent: Int): Double {
        if (maxPercent == 0 || value <= 0) return 0.0
        val x = value.toDouble() / maxValue
        return (maxPercent / 100.0) * x
    }

    /**
     * محاسبه مدت زمان به دقیقه از ساعت شروع و پایان
     */
    fun calculateDurationMinutes(startTime: String, endTime: String): Int {
        if (startTime.isEmpty() || endTime.isEmpty()) return 0
        try {
            val startEnglish = convertPersianToEnglish(startTime)
            val endEnglish = convertPersianToEnglish(endTime)

            val startParts = startEnglish.split(":")
            val endParts = endEnglish.split(":")
            if (startParts.size == 2 && endParts.size == 2) {
                val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()
                val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()
                var diff = endMin - startMin
                if (diff < 0) diff += 24 * 60
                return diff
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating duration: ${e.message}")
        }
        return 0
    }

    private fun convertPersianToEnglish(input: String): String {
        val persianDigits = arrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val englishDigits = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        var result = input
        for (i in persianDigits.indices) {
            result = result.replace(persianDigits[i], englishDigits[i])
        }
        return result
    }

    fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format("%02d:%02d", hours, mins)
    }

    fun parseDuration(duration: String): Int {
        val parts = duration.split(":")
        if (parts.size == 2) {
            return parts[0].toInt() * 60 + parts[1].toInt()
        }
        return 0
    }

    // ============================================================
    // 2. محاسبه ضرایب یک گزارش
    // ============================================================

    fun calculateLogCoefficients(
        physicalDifficulty: Int,
        technicalComplexity: Int,
        heatLevel: Int,
        pollutionLevel: Int,
        workType: String,
        coeffs: EvaluationModels.Coefficients
    ): EvaluationModels.LogCoefficients {

        val physicalCoeff = if (workType != "3") {
            calculateCoefficient(physicalDifficulty, 5, coeffs.physicalDifficulty)
        } else 0.0

        val technicalCoeff = if (workType != "3") {
            calculateCoefficient(technicalComplexity, 5, coeffs.technicalComplexity)
        } else 0.0

        val tempCoeff = if (workType != "3") {
            calculateCoefficient(heatLevel - 30, 25, coeffs.temperature)
        } else 0.0

        val pollutionCoeff = if (workType != "3") {
            calculateCoefficient(pollutionLevel, 100, coeffs.pollution)
        } else 0.0

        val physicalTotalCoeff = physicalCoeff + tempCoeff + pollutionCoeff
        val fullCoeff = physicalTotalCoeff + technicalCoeff

        return EvaluationModels.LogCoefficients(
            physicalCoeff = physicalCoeff,
            technicalCoeff = technicalCoeff,
            tempCoeff = tempCoeff,
            pollutionCoeff = pollutionCoeff,
            physicalTotalCoeff = physicalTotalCoeff,
            fullCoeff = fullCoeff
        )
    }

    // ============================================================
    // 3. محاسبه شاخص‌های یک گزارش برای همه نفرات
    // ============================================================

    /**
     * محاسبه شاخص‌های یک گزارش برای همه نفرات درگیر
     * (این تابع برای استفاده‌های تک‌گزارشی است — نه تجمیعی)
     */
    fun calculateIndicatorsForLog(
        log: TaskLogModel,
        coeffs: EvaluationModels.Coefficients
    ): EvaluationModels.LogEvaluationResult {

        val durationMinutes = calculateDurationMinutes(log.startTime, log.endTime)
        if (durationMinutes <= 0) {
            return EvaluationModels.LogEvaluationResult(
                logId = log.id,
                taskId = log.taskId,
                responsibleId = log.userId,
                workType = log.workType,
                durationMinutes = 0,
                coefficients = EvaluationModels.LogCoefficients(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                userIndicators = emptyMap()
            )
        }

        val logCoeffs = calculateLogCoefficients(
            physicalDifficulty = log.physicalDifficulty,
            technicalComplexity = log.technicalComplexity,
            heatLevel = log.heatLevel,
            pollutionLevel = log.pollutionLevel,
            workType = log.workType,
            coeffs = coeffs
        )

        val assignedUsers = log.assignedUsers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val responsibleId = log.userId
        val allUsers = (assignedUsers + responsibleId).distinct()
        val isTechnical = log.workType == "2"

        val userIndicators = mutableMapOf<String, EvaluationModels.PersonIndicators>()

        for (userId in allUsers) {
            val rawPhysical = if (userId in assignedUsers && log.workType != "3") durationMinutes.toDouble() else 0.0
            val rawResponsibility = if (userId == responsibleId && log.workType != "3") durationMinutes.toDouble() else 0.0
            val rawTechnical = if (userId == responsibleId && isTechnical) durationMinutes.toDouble() else 0.0
            val rawTechnicalParticipation = if (userId != responsibleId && userId in assignedUsers && isTechnical) durationMinutes.toDouble() else 0.0
            val rawInspection = if (userId in assignedUsers && log.workType == "3") durationMinutes.toDouble() else 0.0

            val finalPhysical = if (userId in assignedUsers && log.workType != "3") {
                durationMinutes * (1 + logCoeffs.physicalTotalCoeff)
            } else 0.0

            val finalResponsibility = if (userId == responsibleId && log.workType != "3") {
                durationMinutes * (1 + logCoeffs.fullCoeff)
            } else 0.0

            val finalTechnical = if (userId == responsibleId && isTechnical) {
                durationMinutes * (1 + logCoeffs.technicalCoeff + logCoeffs.physicalTotalCoeff)
            } else 0.0

            val finalTechnicalParticipation = if (userId != responsibleId && userId in assignedUsers && isTechnical) {
                durationMinutes * (1 + logCoeffs.physicalTotalCoeff)
            } else 0.0

            val finalInspection = if (userId in assignedUsers && log.workType == "3") {
                durationMinutes * (1 + logCoeffs.physicalTotalCoeff)
            } else 0.0

            val userName = UserCache.getName(userId)

            userIndicators[userId] = EvaluationModels.PersonIndicators(
                userId = userId,
                userName = userName,
                rawResponsibility = rawResponsibility,
                rawTechnical = rawTechnical,
                rawTechnicalParticipation = rawTechnicalParticipation,
                rawPhysical = rawPhysical,
                rawInspection = rawInspection,
                finalResponsibility = finalResponsibility,
                finalTechnical = finalTechnical,
                finalTechnicalParticipation = finalTechnicalParticipation,
                finalPhysical = finalPhysical,
                finalInspection = finalInspection
            )
        }

        return EvaluationModels.LogEvaluationResult(
            logId = log.id,
            taskId = log.taskId,
            responsibleId = responsibleId,
            workType = log.workType,
            durationMinutes = durationMinutes,
            coefficients = logCoeffs,
            userIndicators = userIndicators
        )
    }

    // ============================================================
    // 4. جمع‌آوری شاخص‌های چند گزارش برای هر نفر
    // ============================================================

    /**
     * جمع‌آوری شاخص‌های چند گزارش و محاسبه مجموع برای هر نفر
     * ⭐ نسخه نهایی با تفکیک technical / technicalParticipation
     */
    fun aggregateIndicators(
        logs: List<TaskLogModel>,
        tasks: List<TaskModel>,
        coeffs: EvaluationModels.Coefficients
    ): List<EvaluationModels.PersonIndicators> {

        val taskAssignedMap = tasks.associate { it.id to it.assignedTo }
        val aggregated = mutableMapOf<String, EvaluationModels.PersonIndicators>()

        for (log in logs) {
            val durationMinutes = calculateDurationMinutes(log.startTime, log.endTime)
            if (durationMinutes <= 0) continue

            // --- ضریب فیزیکی (سختی + دما + آلودگی) ---
            val physicalCoeff = if (log.workType != "3") {
                calculateCoefficient(log.physicalDifficulty, 5, coeffs.physicalDifficulty)
            } else 0.0

            val tempCoeff = if (log.workType != "3") {
                calculateCoefficient(log.heatLevel - 30, 25, coeffs.temperature)
            } else 0.0

            val pollutionCoeff = if (log.workType != "3") {
                calculateCoefficient(log.pollutionLevel, 100, coeffs.pollution)
            } else 0.0

            val physicalTotalCoeff = physicalCoeff + tempCoeff + pollutionCoeff

            // --- ضریب فنی ---
            val technicalCoeff = if (log.workType != "3") {
                calculateCoefficient(log.technicalComplexity, 5, coeffs.technicalComplexity)
            } else 0.0

            // --- ضریب مسئولیت ---
            val taskAssigned = taskAssignedMap[log.taskId] ?: ""
            val taskTeamSize = taskAssigned.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size
            val isTechnical = log.workType == "2"

            val responsibilityCoeff = calculateResponsibilityCoefficient(
                urgency = log.taskUrgency,
                teamSize = taskTeamSize,
                isTechnical = isTechnical
            )

            val fullCoeff = physicalTotalCoeff + technicalCoeff + responsibilityCoeff

            // --- لیست نفرات ---
            val assignedUsers = log.assignedUsers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val responsibleId = log.userId
            val allUsers = (assignedUsers + responsibleId).distinct()

            for (userId in allUsers) {
                val userName = UserCache.getName(userId)
                val existing = aggregated[userId]

                // === ساعت خام ===

                // فیزیکی: همه اعضای تیم (فقط کار غیر بررسی)
                val rawPhysical = if (userId in assignedUsers && log.workType != "3") {
                    durationMinutes.toDouble()
                } else 0.0

                // مسئولیت: فقط مسئول (فقط کار غیر بررسی)
                val rawResponsibility = if (userId == responsibleId && log.workType != "3") {
                    durationMinutes.toDouble()
                } else 0.0

                // کار فنی (مسئول): فقط مسئول در کار دوار
                val rawTechnical = if (userId == responsibleId && isTechnical) {
                    durationMinutes.toDouble()
                } else 0.0

                // مشارکت در کار فنی (انجام‌دهنده): غیرمسئول در کار دوار
                val rawTechnicalParticipation = if (userId != responsibleId && userId in assignedUsers && isTechnical) {
                    durationMinutes.toDouble()
                } else 0.0

                // بررسی: همه اعضای تیم در کار بررسی
                val rawInspection = if (userId in assignedUsers && log.workType == "3") {
                    durationMinutes.toDouble()
                } else 0.0

                // === ساعت با ضریب ===

                // فیزیکی: ضریب فیزیکی کامل
                val finalPhysical = if (userId in assignedUsers && log.workType != "3") {
                    durationMinutes * (1 + physicalTotalCoeff)
                } else 0.0

                // مسئولیت: فقط ضریب مسئولیت
                val finalResponsibility = if (userId == responsibleId && log.workType != "3") {
                    durationMinutes * (1 + responsibilityCoeff)
                } else 0.0

                // کار فنی (مسئول): ضریب فنی + مسئولیت
                val finalTechnical = if (userId == responsibleId && isTechnical) {
                    durationMinutes * (1 + technicalCoeff + responsibilityCoeff)
                } else 0.0

                // مشارکت در کار فنی (انجام‌دهنده): فقط ضریب فیزیکی
                val finalTechnicalParticipation = if (userId != responsibleId && userId in assignedUsers && isTechnical) {
                    durationMinutes * (1 + physicalTotalCoeff)
                } else 0.0

                // بررسی: ضریب فیزیکی
                val finalInspection = if (userId in assignedUsers && log.workType == "3") {
                    durationMinutes * (1 + physicalTotalCoeff)
                } else 0.0

                if (existing == null) {
                    aggregated[userId] = EvaluationModels.PersonIndicators(
                        userId = userId,
                        userName = userName,
                        rawResponsibility = rawResponsibility,
                        rawTechnical = rawTechnical,
                        rawTechnicalParticipation = rawTechnicalParticipation,
                        rawPhysical = rawPhysical,
                        rawInspection = rawInspection,
                        finalResponsibility = finalResponsibility,
                        finalTechnical = finalTechnical,
                        finalTechnicalParticipation = finalTechnicalParticipation,
                        finalPhysical = finalPhysical,
                        finalInspection = finalInspection
                    )
                } else {
                    aggregated[userId] = EvaluationModels.PersonIndicators(
                        userId = userId,
                        userName = userName,
                        rawResponsibility = existing.rawResponsibility + rawResponsibility,
                        rawTechnical = existing.rawTechnical + rawTechnical,
                        rawTechnicalParticipation = existing.rawTechnicalParticipation + rawTechnicalParticipation,
                        rawPhysical = existing.rawPhysical + rawPhysical,
                        rawInspection = existing.rawInspection + rawInspection,
                        finalResponsibility = existing.finalResponsibility + finalResponsibility,
                        finalTechnical = existing.finalTechnical + finalTechnical,
                        finalTechnicalParticipation = existing.finalTechnicalParticipation + finalTechnicalParticipation,
                        finalPhysical = existing.finalPhysical + finalPhysical,
                        finalInspection = existing.finalInspection + finalInspection
                    )
                }
            }
        }

        return aggregated.values.sortedByDescending { it.rawPhysical }
    }

    // ============================================================
    // 5. توابع کمکی برای گزارش کارکرد پرسنل
    // ============================================================

    fun toDisplayFormat(
        indicators: List<EvaluationModels.PersonIndicators>
    ): List<EvaluationModels.DisplayPersonIndicators> {
        return indicators.map { it.toDisplayFormat() }
    }

    fun detectNonZeroColumns(
        indicators: List<EvaluationModels.PersonIndicators>
    ): ColumnVisibility {
        var hasResponsibility = false
        var hasPhysical = false
        var hasTechnical = false
        var hasTechnicalParticipation = false
        var hasInspection = false

        for (indicator in indicators) {
            if (indicator.rawResponsibility > 0) hasResponsibility = true
            if (indicator.rawPhysical > 0) hasPhysical = true
            if (indicator.rawTechnical > 0) hasTechnical = true
            if (indicator.rawTechnicalParticipation > 0) hasTechnicalParticipation = true
            if (indicator.rawInspection > 0) hasInspection = true
        }

        return ColumnVisibility(
            showResponsibility = hasResponsibility,
            showPhysical = hasPhysical,
            showTechnical = hasTechnical,
            showTechnicalParticipation = hasTechnicalParticipation,
            showInspection = hasInspection
        )
    }

    /**
     * محاسبه ضریب مسئولیت بر اساس فاکتورهای خودکار
     */
    fun calculateResponsibilityCoefficient(
        urgency: String,
        teamSize: Int,
        isTechnical: Boolean
    ): Double {
        val urgencyCoeff = when (urgency) {
            "خیلی زیاد" -> 0.2
            "زیاد" -> 0.1
            else -> 0.0
        }

        val teamCoeff = if (teamSize >= 2) {
            ((teamSize - 1) * 0.05)
        } else {
            0.0
        }

        val technicalCoeff = if (isTechnical) 0.10 else 0.0

        return urgencyCoeff + teamCoeff + technicalCoeff
    }

    data class ColumnVisibility(
        val showResponsibility: Boolean,
        val showPhysical: Boolean,
        val showTechnical: Boolean,
        val showTechnicalParticipation: Boolean,
        val showInspection: Boolean
    )
}