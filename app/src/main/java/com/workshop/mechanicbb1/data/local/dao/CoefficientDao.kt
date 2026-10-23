package com.workshop.mechanicbb1.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workshop.mechanicbb1.data.local.entity.CoefficientEntity

@Dao
interface CoefficientDao {

    @Query("SELECT * FROM coefficients WHERE `key` = 'default' LIMIT 1")
    suspend fun get(): CoefficientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CoefficientEntity)

    @Query("DELETE FROM coefficients")
    suspend fun clearAll()
}