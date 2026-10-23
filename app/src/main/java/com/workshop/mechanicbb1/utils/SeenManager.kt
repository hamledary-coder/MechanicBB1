package com.workshop.mechanicbb1.utils

import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.remote.Config
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONArray
import org.json.JSONObject

object SeenManager {

    fun markAsSeen(
        context: Context,
        userId: String,
        items: List<SeenItem>,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (items.isEmpty()) {
            onSuccess?.invoke()
            return
        }

        val json = JSONObject().apply {
            put("action", "markAsSeen")
            put("userId", userId)
            put("items", JSONArray().apply {
                items.forEach {
                    put(JSONObject().apply {
                        put("type", it.type)
                        put("id", it.id)
                    })
                }
            })
        }

        val request = JsonObjectRequest(
            Request.Method.POST, Config.BASE_URL, json,
            { response ->
                Log.d("SeenManager", "Marked as seen: ${items.size} items - $response")
                onSuccess?.invoke()
            },
            { error ->
                Log.e("SeenManager", "Error: ${error.message}")
                onError?.invoke(error.message ?: "Unknown error")
            }
        )
        VolleySingleton.getInstance(context).add(request)
    }
}

data class SeenItem(val type: String, val id: String)