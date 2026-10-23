package com.workshop.mechanicbb1.utils

import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

object VolleySingleton {
    private var instance: RequestQueue? = null

    fun getInstance(context: Context): RequestQueue {
        return instance ?: synchronized(this) {
            Volley.newRequestQueue(context.applicationContext).also { instance = it }
        }
    }
}