package com.workshop.mechanicbb1.data.local.repository

import android.content.Context
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkOrderLocalRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.workOrderDao()

    /**
     * دریافت لیست WorkOrderها با فیلتر
     * @param status "" | "open" | "archived" (به external_status نگاشت می‌شه)
     */
    fun getWorkOrders(
        unit: String,
        requestClass: String,
        status: String = "",
        search: String = ""        // ← جدید
    ): Flow<List<WorkOrderModel>> {
        return dao.getFiltered(unit, requestClass, status, search)
            .map { entities -> entities.map { it.toModel() } }
    }

    fun getWorkOrderById(id: String): Flow<WorkOrderModel?> {
        return dao.getByIdFlow(id).map { it?.toModel() }
    }

    suspend fun getWorkOrderByIdOnce(id: String): WorkOrderModel? {
        return dao.getById(id)?.toModel()
    }

    suspend fun saveWorkOrders(workOrders: List<WorkOrderEntity>) {
        dao.insertAll(workOrders)
    }

    suspend fun saveWorkOrder(workOrder: WorkOrderEntity) {
        dao.insert(workOrder)
    }

    suspend fun updateWorkOrder(workOrder: WorkOrderEntity) {
        dao.update(workOrder)
    }

    suspend fun deleteWorkOrder(id: String) {
        // ۱. progressها
        db.taskLogWorkOrderProgressDao().deleteByWorkOrder(id)   // ← جدید

        // ۲. mappingها
        db.taskWorkOrderMappingDao().deleteByWorkOrder(id)       // ← جدید

        // ۳. خود WorkOrder
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun count(): Int = dao.count()

    suspend fun isRequestNumberTaken(
        requestNumber: String,
        requestClass: String?,
        excludeId: String = ""
    ): Boolean {
        if (requestNumber.isEmpty() || requestClass == null) return false
        return dao.countByRequestNumber(requestNumber, requestClass, excludeId) > 0
    }

    // ============ Mapping ============
    private fun WorkOrderEntity.toModel(): WorkOrderModel {
        return WorkOrderModel(
            id = id,
            requestNumber = requestNumber ?: "",   // ← String? → String
            title = title,
            requestClass = requestClass,
            requestDate = requestDate,
            requester = requester,
            approver = approver ?: "",
            unit = unit,
            subUnit = subUnit,
            equipmentName = equipmentName,
            description = description,
            externalStatus = externalStatus,
            progress = progress,
            source = source
        )
    }
}

// ============ Extension ============
fun WorkOrderModel.toEntity(): WorkOrderEntity {
    return WorkOrderEntity(
        id = id,
        requestNumber = requestNumber.ifEmpty { null },   // ← String → String?
        title = title,
        requestClass = requestClass,
        requestDate = requestDate,
        requester = requester,
        approver = approver.ifEmpty { null },
        unit = unit,
        subUnit = subUnit,
        equipmentName = equipmentName,
        description = description,
        externalStatus = externalStatus,
        progress = progress,
        source = source
    )
}