package com.workshop.mechanicbb1.notifications

import android.content.Context
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest

class NotificationRepository(private val context: Context) {

    fun getUserNotifications(
        userId: String,
        onSuccess: (List<NotificationModel>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "${Config.BASE_URL}?action=getUserNotifications&userId=$userId"

        val request = JsonArrayRequest(
            Request.Method.GET, url, null,
            { response ->
                val notifications = mutableListOf<NotificationModel>()
                for (i in 0 until response.length()) {
                    val obj = response.getJSONObject(i)
                    notifications.add(
                        NotificationModel(
                            id = obj.getString("id"),
                            userId = obj.getString("user_id"),
                            taskId = obj.getString("task_id"),
                            type = obj.getString("type"),
                            actorId = obj.optString("actor_id", ""),
                            isSeen = obj.getBoolean("is_seen"),
                            createdAt = obj.getString("created_at")
                        )
                    )
                }
                onSuccess(notifications)
            },
            { error -> onError(error.message ?: "خطای شبکه") }
        )

        VolleySingleton.getInstance(context).add(request)
    }

    fun markAsSeen(
        notificationId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "${Config.BASE_URL}?action=markNotificationSeen&notificationId=$notificationId"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { _ -> onSuccess() },
            { error -> onError(error.message ?: "خطا") }
        )

        VolleySingleton.getInstance(context).add(request)
    }

    fun markAllAsSeen(
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "${Config.BASE_URL}?action=markAllNotificationsSeen&userId=$userId"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { _ -> onSuccess() },
            { error -> onError(error.message ?: "خطا") }
        )

        VolleySingleton.getInstance(context).add(request)
    }
}