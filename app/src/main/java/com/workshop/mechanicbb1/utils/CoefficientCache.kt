package com.workshop.mechanicbb1.utils

import android.content.Context
import com.workshop.mechanicbb1.data.remote.Config
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import org.json.JSONObject

object CoefficientCache {
    private var coefficients: Map<String, Int>? = null
    private var isLoading = false
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    fun getCoefficients(context: Context, callback: (Map<String, Int>) -> Unit) {
        // اگر از قبل داریم
        coefficients?.let {
            callback(it)
            return
        }

        // اگر در حال بارگذاری هستیم
        if (isLoading) {
            pendingCallbacks.add { callback(coefficients ?: emptyMap()) }
            return
        }

        isLoading = true
        loadFromServer(context) { map ->
            coefficients = map
            isLoading = false
            callback(map)
            pendingCallbacks.forEach { it() }
            pendingCallbacks.clear()
        }
    }

    private fun loadFromServer(context: Context, onComplete: (Map<String, Int>) -> Unit) {
        val url = "${Config.BASE_URL}?action=getCoefficients"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    val map = mutableMapOf<String, Int>()
                    json.keys().forEach { key ->
                        map[key] = json.getInt(key)
                    }
                    onComplete(map)
                } catch (e: Exception) {
                    // در صورت خطا، مقادیر پیش‌فرض
                    onComplete(defaultCoefficients())
                }
            },
            { error ->
                onComplete(defaultCoefficients())
            }
        )
        VolleySingleton.getInstance(context).add(request)
    }

    private fun defaultCoefficients(): Map<String, Int> {
        return mapOf(
            "physical_difficulty" to 25,
            "technical_complexity" to 30,
            "temperature" to 15,
            "pollution" to 20
        )
    }

    fun clear() {
        coefficients = null
    }
}