package com.example.edmotor.data.local

import androidx.room.Embedded
import androidx.room.Relation

 data class MaintenanceWithParts(
    @Embedded val record: MaintenanceRecordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "maintenanceRecordId"
    )
    val parts: List<MaintenancePartEntity>
)
