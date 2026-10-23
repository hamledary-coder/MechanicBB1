package com.workshop.mechanicbb1.utils

import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.remote.Config
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ActivityLogger {

    private val client = OkHttpClient()

    fun log(context: Context, userId: String, userName: String, action: String, targetType: String, targetId: String, description: String) {
        val url = "${Config.BASE_URL}?action=logActivity"  // ✅ URL میگوید action چیست
        Log.d("ActivityLogger", "URL: $url")

        val jsonObject = JSONObject().apply {
            put("userId", userId)
            put("userName", userName)
            put("log_action", action)
            put("targetType", targetType)
            put("targetId", targetId)
            put("description", description)
        }

        val jsonString = jsonObject.toString()
        Log.d("ActivityLogger", "Sending JSON: $jsonString")

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonString.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e("ActivityLogger", "❌ Network Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d("ActivityLogger", "✅ Log sent successfully: $responseBody")
                } else {
                    Log.e("ActivityLogger", "❌ HTTP ${response.code}: $responseBody")
                }
            }
        })
    }
}