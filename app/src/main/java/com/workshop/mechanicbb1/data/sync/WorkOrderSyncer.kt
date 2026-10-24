@file:Suppress("DEPRECATION")

package com.workshop.mechanicbb1.data.sync

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.JsonArrayRequest
import com.workshop.mechanicbb1.data.local.WorkOrderLocalRepository
import com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object WorkOrderSyncer {

    private const val TAG = "WorkOrderSyncer"

    /**
     * sync WorkOrderها از سرور به Room
     * @param unit فیلتر واحد (خالی = همه)
     * @param onComplete callback با تعداد
     */
    fun syncWorkOrdersFromServer(
        context: Context,
        unit: String = "",
        onComplete: ((Int) -> Unit)? = null
    ) {
        val url = buildString {
            append("${Config.BASE_URL}?action=getWorkOrders")
            append("&_=${System.currentTimeMillis()}")
            if (unit.isNotEmpty()) {
                append("&unit=${java.net.URLEncoder.encode(unit, "UTF-8")}")
            }
        }

        val request = object : JsonArrayRequest(
            url,
            { response ->
                try {
                    val entities = mutableListOf<WorkOrderEntity>()
                    for (i in 0 until response.length()) {
                        val obj = response.getJSONObject(i)
                        entities.add(parseEntity(obj))
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repo = WorkOrderLocalRepository(context)
                            repo.saveWorkOrders(entities)
                            Log.d(TAG, "✅ Synced ${entities.size} work orders")
                            onComplete?.invoke(entities.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving to Room: ${e.message}")
                            onComplete?.invoke(0)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    onComplete?.invoke(0)
                }
            },
            { error ->
                Log.e(TAG, "Volley error: ${error.message}")
                onComplete?.invoke(0)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "Cache-Control" to "no-cache, no-store, must-revalidate",
                    "Pragma" to "no-cache"
                )
            }
        }
        VolleySingleton.getInstance(context).add(request)
    }

    /**
     * sync یک WorkOrder خاص (برای DetailActivity)
     */
    fun syncSingleWorkOrder(
        context: Context,
        workOrderId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val url = "${Config.BASE_URL}?action=getWorkOrder&workOrderId=$workOrderId&_=${System.currentTimeMillis()}"

        val request = object : com.android.volley.toolbox.JsonObjectRequest(
            url, null,
            { response ->
                try {
                    val entity = parseEntity(response)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repo = WorkOrderLocalRepository(context)
                            repo.saveWorkOrder(entity)
                            Log.d(TAG, "✅ Synced single work order: $workOrderId")
                            onComplete?.invoke(true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving: ${e.message}")
                            onComplete?.invoke(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    onComplete?.invoke(false)
                }
            },
            { error ->
                Log.e(TAG, "Volley error: ${error.message}")
                onComplete?.invoke(false)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf("Cache-Control" to "no-cache")
            }
        }
        VolleySingleton.getInstance(context).add(request)
    }

    // ============ Parse ============
    private fun parseEntity(obj: JSONObject): WorkOrderEntity {
        return WorkOrderEntity(
            id = obj.optString("id", ""),
            requestNumber = obj.optString("request_number", ""),
            title = obj.optString("title", ""),
            requestClass = if (obj.isNull("request_class")) null else obj.optString("request_class", ""),
            requestDate = obj.optString("request_date", ""),
            requester = obj.optString("requester", ""),
            unit = obj.optString("unit", ""),
            subUnit = obj.optString("sub_unit", ""),
            equipmentName = obj.optString("equipment_name", ""),
            description = obj.optString("description", ""),
            externalStatus = if (obj.isNull("external_status")) null else obj.optString("external_status", ""),
            source = obj.optString("source", "manual"),
            progress = obj.optInt("progress", 0)
        )
    }
}