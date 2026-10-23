package com.workshop.mechanicbb1.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,           // rowId

    val personnelId: String = "",
    val name: String = "",
    val role: String = "",

    val lastSyncedAt: Long = 0L
)