package com.workshop.mechanicbb1.data.model

data class PersonnelReport(
    val userId: String,
    val userName: String,
    // ساعت خام
    val rawResponsibility: String,   // "42:00"
    val rawTechnical: String,
    val rawPhysical: String,
    val rawInspection: String,
    // ساعت با ضریب
    val finalResponsibility: String,
    val finalTechnical: String,
    val finalPhysical: String,
    val finalInspection: String
)