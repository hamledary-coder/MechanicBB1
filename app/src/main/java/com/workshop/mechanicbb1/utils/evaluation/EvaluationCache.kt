package com.workshop.mechanicbb1.utils.evaluation

import android.content.Context
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.CoefficientEntity
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * کش ضرایب ارزیابی — آفلاین‌فرست
 * اول از Room می‌خونه، اگه نبود از سرور
 */
object EvaluationCache {

    private var coefficients: EvaluationModels.Coefficients? = null
    private var isLoading = false
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    /**
     * دریافت ضرایب (اول Room، بعد سرور)
     */
    fun getCoefficients(context: Context, callback: (EvaluationModels.Coefficients?) -> Unit) {
        // ۱. اگه توی حافظه داریم، فوری برگردون
        coefficients?.let {
            callback(it)
            return
        }

        // ۲. از Room بخون (async)
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val entity = db.coefficientDao().get()

            if (entity != null) {
                val coeffs = EvaluationModels.Coefficients(
                    physicalDifficulty = entity.physicalDifficulty,
                    technicalComplexity = entity.technicalComplexity,
                    temperature = entity.temperature,
                    pollution = entity.pollution
                )
                coefficients = coeffs

                withContext(Dispatchers.Main) {
                    callback(coeffs)
                }
                return@launch
            }

            // ۳. Room خالی → از سرور بگیر
            withContext(Dispatchers.Main) {
                loadFromServer(context, callback)
            }
        }
    }

    /**
     * بارگذاری از سرور و ذخیره توی Room
     */
    private fun loadFromServer(
        context: Context,
        callback: (EvaluationModels.Coefficients?) -> Unit
    ) {
        if (isLoading) {
            pendingCallbacks.add { callback(coefficients) }
            return
        }

        isLoading = true
        val url = "${Config.BASE_URL}?action=getCoefficients"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    val coeffs = EvaluationModels.Coefficients(
                        physicalDifficulty = json.getInt("physical_difficulty"),
                        technicalComplexity = json.getInt("technical_complexity"),
                        temperature = json.getInt("temperature"),
                        pollution = json.getInt("pollution")
                    )
                    coefficients = coeffs
                    isLoading = false

                    // ذخیره توی Room
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getInstance(context)
                        db.coefficientDao().insert(
                            CoefficientEntity(
                                key = "default",
                                physicalDifficulty = coeffs.physicalDifficulty,
                                technicalComplexity = coeffs.technicalComplexity,
                                temperature = coeffs.temperature,
                                pollution = coeffs.pollution,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    callback(coeffs)
                    pendingCallbacks.forEach { it() }
                    pendingCallbacks.clear()
                } catch (e: Exception) {
                    isLoading = false
                    callback(null)
                    pendingCallbacks.forEach { it() }
                    pendingCallbacks.clear()
                }
            },
            { error ->
                isLoading = false
                callback(null)
                pendingCallbacks.forEach { it() }
                pendingCallbacks.clear()
            }
        )

        VolleySingleton.getInstance(context).add(request)
    }

    fun getCachedCoefficients(): EvaluationModels.Coefficients? {
        return coefficients
    }

    fun clear() {
        coefficients = null
    }
}