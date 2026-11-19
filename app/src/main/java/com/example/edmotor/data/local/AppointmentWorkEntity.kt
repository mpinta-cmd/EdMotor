package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "appointment_work",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MaintenanceRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["maintenanceRecordId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["clientAuthId", "appointmentId", "appointmentCreatedAtMillis"], unique = true),
        Index(value = ["vehicleId"]),
        Index(value = ["maintenanceRecordId"])
    ]
)
data class AppointmentWorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientAuthId: String,
    val appointmentId: Long,
    val appointmentCreatedAtMillis: Long,
    val vehicleId: Int? = null,
    val finishedAtMillis: Long? = null,
    val maintenanceRecordId: Int? = null
)
