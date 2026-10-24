package com.workshop.mechanicbb1.data.local

import android.content.Context
import com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity
import com.workshop.mechanicbb1.data.model.WorkOrderModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkOrderLocalRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.workOrderDao()

    /**
     * دریافت لیست WorkOrderها (Flow)
     * - unit خالی + class خالی → همه
     * - unit پر + class خالی → فیلتر واحد
     * - unit خالی + class پر → فیلتر کلاس
     * - unit پر + class پر → فیلتر هر دو
     */
    fun getWorkOrders(unit: String, requestClass: String): Flow<List<WorkOrderModel>> {
        val flow = when {
            unit.isEmpty() && requestClass.isEmpty() -> dao.getAll()
            unit.isNotEmpty() && requestClass.isEmpty() -> dao.getByUnit(unit)
            unit.isEmpty() && requestClass.isNotEmpty() -> dao.getByClass(requestClass)
            else -> dao.getByUnitAndClass(unit, requestClass)
        }
        return flow.map { entities -> entities.map { it.toModel() } }
    }

    /**
     * دریافت یک WorkOrder (Flow)
     */
    fun getWorkOrderById(id: String): Flow<WorkOrderModel?> {
        return dao.getByIdFlow(id).map { it?.toModel() }
    }

    /**
     * دریافت یک WorkOrder (suspend)
     */
    suspend fun getWorkOrderByIdOnce(id: String): WorkOrderModel? {
        return dao.getById(id)?.toModel()
    }

    /**
     * ذخیره لیست (از sync)
     */
    suspend fun saveWorkOrders(workOrders: List<WorkOrderEntity>) {
        dao.insertAll(workOrders)
    }

    /**
     * ذخیره یکی
     */
    suspend fun saveWorkOrder(workOrder: WorkOrderEntity) {
        dao.insert(workOrder)
    }

    /**
     * آپدیت
     */
    suspend fun updateWorkOrder(workOrder: WorkOrderEntity) {
        dao.update(workOrder)
    }

    /**
     * حذف
     */
    suspend fun deleteWorkOrder(id: String) {
        dao.deleteById(id)
    }

    /**
     * پاک کردن همه
     */
    suspend fun clearAll() {
        dao.deleteAll()
    }

    /**
     * تعداد
     */
    suspend fun count(): Int = dao.count()

    // ============ Extension: Entity → Model ============
    private fun WorkOrderEntity.toModel(): WorkOrderModel {
        return WorkOrderModel(
            id = id,
            requestNumber = requestNumber,
            title = title,
            requestClass = requestClass,
            requestDate = requestDate,
            requester = requester,
            unit = unit,
            subUnit = subUnit,
            equipmentName = equipmentName,
            description = description,
            externalStatus = externalStatus,
            source = source,
            progress = progress
        )
    }
}

// ============ Extension: Model → Entity (برای استفاده تو Activityها) ============
fun WorkOrderModel.toEntity(): WorkOrderEntity {
    return WorkOrderEntity(
        id = id,
        requestNumber = requestNumber,
        title = title,
        requestClass = requestClass,
        requestDate = requestDate,
        requester = requester,
        unit = unit,
        subUnit = subUnit,
        equipmentName = equipmentName,
        description = description,
        externalStatus = externalStatus,
        source = source,
        progress = progress
    )
}