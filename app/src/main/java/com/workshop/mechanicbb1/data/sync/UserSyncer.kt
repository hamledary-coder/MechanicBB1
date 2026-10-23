package com.workshop.mechanicbb1.data.sync

import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.UserEntity
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * sync کاربران از سرور به Room
 */
object UserSyncer {

    private const val TAG = "UserSyncer"

    fun syncUsersFromServer(
        context: Context,
        onComplete: ((Int) -> Unit)? = null
    ) {
        val url = "${Config.BASE_URL}?action=getEmployees"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonArray = JSONArray(response)
                    val users = mutableListOf<UserEntity>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("rowId", "")
                        var name = obj.optString("name", "")
                        val personnelId = obj.optString("personnelId", "")
                        val role = obj.optString("role", "")

                        if (name.isEmpty()) name = personnelId
                        if (name.isEmpty() || id.isEmpty() || id == "0") continue

                        users.add(
                            UserEntity(
                                id = id,
                                personnelId = personnelId,
                                name = name,
                                role = role,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    Log.d(TAG, "Fetched ${users.size} users from server")

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            db.userDao().insertAll(users)
                            Log.d(TAG, "Saved ${users.size} users to Room")
                            onComplete?.invoke(users.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving: ${e.message}")
                            onComplete?.invoke(0)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing: ${e.message}")
                    onComplete?.invoke(0)
                }
            },
            { error ->
                Log.e(TAG, "Network error: ${error.message}")
                onComplete?.invoke(0)
            }
        )

        VolleySingleton.getInstance(context).add(request)
    }
}