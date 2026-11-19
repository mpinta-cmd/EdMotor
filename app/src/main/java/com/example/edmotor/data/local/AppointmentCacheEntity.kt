package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "appointment_cache",
    primaryKeys = ["accountAuthId", "accountRole", "id"],
    indices = [Index(value = ["accountAuthId", "accountRole", "scheduledAtMillis"])]
)
data class AppointmentCacheEntity(
    val accountAuthId: String,
    val accountRole: String,
    val id: Long,
    val clientAuthId: String,
    val clientName: String,
    val clientUsername: String,
    val clientPhone: String?,
    val vehicleBrand: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val vehiclePlate: String,
    val scheduledAtMillis: Long,
    val description: String,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
