package com.example.edmotor.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class AppointmentWorkWithMaintenance(
    @Embedded val work: AppointmentWorkEntity,
    @Relation(
        entity = MaintenanceRecordEntity::class,
        parentColumn = "maintenanceRecordId",
        entityColumn = "id"
    )
    val maintenance: MaintenanceWithParts?
)
