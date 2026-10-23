package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coefficients")
data class CoefficientEntity(
    @PrimaryKey
    val key: String,           // همیشه "default" (چون فقط یه ست ضرایب داریم)

    val physicalDifficulty: Int = 25,
    val technicalComplexity: Int = 30,
    val temperature: Int = 15,
    val pollution: Int = 20,

    val lastSyncedAt: Long = 0L
)