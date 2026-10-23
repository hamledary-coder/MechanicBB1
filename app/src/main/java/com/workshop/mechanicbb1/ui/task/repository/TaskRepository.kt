package com.workshop.mechanicbb1.ui.task.repository

import android.content.Context
import com.workshop.mechanicbb1.data.remote.Config
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class TaskRepository(private val context: Context) {

    private val client = OkHttpClient()

    // حذف کار (درست)
    fun deleteTask(taskId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = "${Config.Endpoints.DELETE_TASK}?action=delete&id=$taskId"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Cache-Control", "no-cache")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "خطای اتصال")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && body.contains("success")) {
                    onSuccess()
                } else {
                    onError(body)
                }
            }
        })
    }

    // ارجاع کار
    fun assignTask(taskId: String, assigneeIds: String, referralType: String, responsibleId: String?, referredBy: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = Config.Endpoints.TASKS
        val json = JSONObject().apply {
            put("action", "assignTask")
            put("taskId", taskId)
            put("assigneeIds", assigneeIds)
            put("referralType", referralType)
            responsibleId?.let { put("responsibleId", it) }
            put("referredBy", referredBy)  // ← اضافه شد
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).addHeader("Cache-Control", "no-cache").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "خطای اتصال")
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && body.contains("success")) onSuccess() else onError(body)
            }
        })
    }

    // داوطلب شدن
    fun volunteer(taskId: String, assigneeIds: String, responsibleId: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = Config.Endpoints.TASKS
        val json = JSONObject().apply {
            put("action", "volunteer")
            put("taskId", taskId)
            put("assigneeIds", assigneeIds)
            responsibleId?.let { put("responsibleId", it) }
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).addHeader("Cache-Control", "no-cache").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "خطای اتصال")
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && body.contains("success")) onSuccess() else onError(body)
            }
        })
    }

    // دریافت لیست کارمندان
    fun getEmployees(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val url = "${Config.Endpoints.TASKS}?action=getEmployees"
        val request = Request.Builder().url(url).get().addHeader("Cache-Control", "no-cache").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "خطای اتصال")
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) onSuccess(body) else onError(body)
            }
        })
    }

    // ارسال دعوتنامه
    fun sendInvite(taskId: String, inviteeIds: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = Config.Endpoints.TASKS
        val json = JSONObject().apply {
            put("action", "sendInvite")
            put("taskId", taskId)
            put("inviteeIds", inviteeIds)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Cache-Control", "no-cache")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "خطای اتصال")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                android.util.Log.d("SEND_INVITE", "Response: $body")

                if (response.isSuccessful && body.contains("success")) {
                    onSuccess()
                } else {
                    onError(body)
                }
            }
        })
    }


}