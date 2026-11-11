package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "maintenance_parts",
    foreignKeys = [
        ForeignKey(
            entity = MaintenanceRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["maintenanceRecordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["maintenanceRecordId"])]
)
data class MaintenancePartEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val maintenanceRecordId: Int,
    val catalogProductId: Int? = null,
    val name: String,
    val brand: String = "",
    val reference: String = "",
    val specification: String = "",
    val quantity: Double = 1.0,
    val unit: String = "unidad",
    val unitPriceCop: Long = 0L
)
