package com.workshop.mechanicbb1.data.local.dao

import androidx.room.*
import com.workshop.mechanicbb1.data.local.entity.WorkOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderDao {

    @Query("""
        SELECT * FROM work_orders 
        ORDER BY 
            CASE requestClass 
                WHEN '14' THEN 1
                WHEN '12' THEN 2
                WHEN '12P' THEN 3
                ELSE 4
            END, 
            requestDate DESC
    """)
    fun getAll(): Flow<List<WorkOrderEntity>>

    @Query("""
        SELECT * FROM work_orders 
        WHERE unit = :unit 
        ORDER BY 
            CASE requestClass 
                WHEN '14' THEN 1
                WHEN '12' THEN 2
                WHEN '12P' THEN 3
                ELSE 4
            END, 
            requestDate DESC
    """)
    fun getByUnit(unit: String): Flow<List<WorkOrderEntity>>

    @Query("""
        SELECT * FROM work_orders 
        WHERE unit = :unit AND requestClass = :requestClass
        ORDER BY requestDate DESC
    """)
    fun getByUnitAndClass(unit: String, requestClass: String): Flow<List<WorkOrderEntity>>

    @Query("""
        SELECT * FROM work_orders 
        WHERE requestClass = :requestClass
        ORDER BY requestDate DESC
    """)
    fun getByClass(requestClass: String): Flow<List<WorkOrderEntity>>

    @Query("SELECT * FROM work_orders WHERE id = :id")
    suspend fun getById(id: String): WorkOrderEntity?

    @Query("SELECT * FROM work_orders WHERE id = :id")
    fun getByIdFlow(id: String): Flow<WorkOrderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workOrders: List<WorkOrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workOrder: WorkOrderEntity)

    @Update
    suspend fun update(workOrder: WorkOrderEntity)

    @Query("DELETE FROM work_orders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM work_orders")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM work_orders")
    suspend fun count(): Int
}