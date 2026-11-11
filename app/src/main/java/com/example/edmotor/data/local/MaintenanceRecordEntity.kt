package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "maintenance_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdByUserId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["updatedByUserId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["createdByUserId"]),
        Index(value = ["updatedByUserId"])
    ]
)
data class MaintenanceRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vehicleId: Int,
    val createdByUserId: Int,
    val updatedByUserId: Int? = null,
    val serviceType: String,
    val serviceDate: String,
    val mileageKm: Int,
    val description: String,
    val laborCostCop: Long = 0L,
    val nextServiceMileageKm: Int? = null,
    val nextServiceDate: String? = null,
    val observations: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
