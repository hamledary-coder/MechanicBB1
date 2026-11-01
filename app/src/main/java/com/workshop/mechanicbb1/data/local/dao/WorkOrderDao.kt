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
                WHEN '15' THEN 4
                ELSE 5
            END, 
            requestDate DESC
    """)
    fun getAll(): Flow<List<WorkOrderEntity>>

    // ============ فیلتر کامل ============
    // status = "" → همه
    // status = "open" → external_status IN ('1'..'9')
    // status = "archived" → external_status = '10'
    @Query("""
        SELECT * FROM work_orders 
        WHERE (:unit = '' OR unit = :unit)
          AND (:requestClass = '' OR requestClass = :requestClass)
          AND (
                :status = ''
                OR (:status = 'archived' AND externalStatus = '10')
                OR (:status = 'open' AND externalStatus IN ('1','2','3','4','5','6','7','8','9'))
              )
          AND (:search = '' OR requestNumber LIKE '%' || :search || '%' OR title LIKE '%' || :search || '%')
        ORDER BY 
            CASE requestClass 
                WHEN '14' THEN 1
                WHEN '12' THEN 2
                WHEN '12P' THEN 3
                WHEN '15' THEN 4
                ELSE 5
            END, 
            requestDate DESC
    """)
    fun getFiltered(
        unit: String,
        requestClass: String,
        status: String,     // "" | "open" | "archived"
        search: String
    ): Flow<List<WorkOrderEntity>>

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

    @Query("""
    SELECT * FROM work_orders 
    WHERE externalStatus != '10'
    ORDER BY 
        CASE requestClass 
            WHEN '14' THEN 1
            WHEN '12' THEN 2
            WHEN '12P' THEN 3
            WHEN '15' THEN 4
            ELSE 5
        END, 
        requestDate DESC
""")
    suspend fun getOpenForPicker(): List<WorkOrderEntity>
}