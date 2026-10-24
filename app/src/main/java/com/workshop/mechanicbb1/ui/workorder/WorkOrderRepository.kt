package com.workshop.mechanicbb1.ui.workorder

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.VolleySingleton
import org.json.JSONObject
import java.net.URLEncoder

@Suppress("DEPRECATION")
class WorkOrderRepository(private val context: Context) {

    fun getWorkOrders(
        unit: String = "",
        requestClass: String = "",
        search: String = "",
        onSuccess: (List<WorkOrderModel>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = buildString {
            append("${Config.BASE_URL}?action=getWorkOrders")
            append("&_=${System.currentTimeMillis()}")
            if (unit.isNotEmpty()) append("&unit=${URLEncoder.encode(unit, "UTF-8")}")
            if (requestClass.isNotEmpty()) append("&class=$requestClass")
            if (search.isNotEmpty()) append("&search=${URLEncoder.encode(search, "UTF-8")}")
        }

        val request = object : JsonArrayRequest(
            url,
            { response ->
                try {
                    val list = mutableListOf<WorkOrderModel>()
                    for (i in 0 until response.length()) {
                        val obj = response.getJSONObject(i)
                        list.add(parseWorkOrder(obj))
                    }
                    onSuccess(list)
                } catch (e: Exception) {
                    Log.e("WorkOrderRepo", "Parse error: ${e.message}")
                    onError("خطا در پردازش داده: ${e.message}")
                }
            },
            { error ->
                Log.e("WorkOrderRepo", "Volley error: ${error.message}")
                onError("خطا در اتصال: ${error.message}")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf("Cache-Control" to "no-cache")
            }
        }
        VolleySingleton.getInstance(context).add(request)
    }

    fun getWorkOrder(
        workOrderId: String,
        onSuccess: (WorkOrderModel) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "${Config.BASE_URL}?action=getWorkOrder&workOrderId=$workOrderId"

        val request = object : JsonObjectRequest(
            url, null,
            { response ->
                try {
                    onSuccess(parseWorkOrder(response))
                } catch (e: Exception) {
                    onError("خطا در پردازش: ${e.message}")
                }
            },
            { error -> onError("خطا: ${error.message}") }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf("Cache-Control" to "no-cache")
            }
        }
        VolleySingleton.getInstance(context).add(request)
    }

    fun createWorkOrder(
        workOrder: WorkOrderModel,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "${Config.BASE_URL}?action=createWorkOrder"
        val json = JSONObject().apply {
            put("request_number", workOrder.requestNumber)
            put("title", workOrder.title)
            put("request_class", workOrder.requestClass ?: JSONObject.NULL)
            put("request_date", workOrder.requestDate)
            put("requester", workOrder.requester)
            put("unit", workOrder.unit)
            put("sub_unit", workOrder.subUnit)
            put("equipment_name", workOrder.equipmentName)
            put("description", workOrder.description)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, json,
            { response ->
                if (response.optString("status") == "success") {
                    onSuccess(response.optString("id", ""))
                } else {
                    onError(response.optString("message", "خطا"))
                }
            },
            { error -> onError("خطا در اتصال: ${error.message}") }
        )
        VolleySingleton.getInstance(context).add(request)
    }

    fun updateWorkOrder(
        workOrder: WorkOrderModel,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "${Config.BASE_URL}?action=updateWorkOrder"
        val json = JSONObject().apply {
            put("workOrderId", workOrder.id)
            put("title", workOrder.title)
            put("request_class", workOrder.requestClass ?: JSONObject.NULL)
            put("request_date", workOrder.requestDate)
            put("requester", workOrder.requester)
            put("unit", workOrder.unit)
            put("sub_unit", workOrder.subUnit)
            put("equipment_name", workOrder.equipmentName)
            put("description", workOrder.description)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, json,
            { response ->
                if (response.optString("status") == "success") onSuccess()
                else onError(response.optString("message", "خطا"))
            },
            { error -> onError("خطا: ${error.message}") }
        )
        VolleySingleton.getInstance(context).add(request)
    }

    fun deleteWorkOrder(
        workOrderId: String,
        deletedBy: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "${Config.BASE_URL}?action=deleteWorkOrder&workOrderId=$workOrderId&deletedBy=$deletedBy"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                if (response.optString("status") == "success") onSuccess()
                else onError(response.optString("message", "خطا"))
            },
            { error -> onError("خطا: ${error.message}") }
        )
        VolleySingleton.getInstance(context).add(request)
    }

    private fun parseWorkOrder(obj: JSONObject): WorkOrderModel {
        return WorkOrderModel(
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
            progress = obj.optInt("progress", 0),
            createdAt = obj.optString("created_at", ""),
            updatedAt = obj.optString("updated_at", "")
        )
    }
}