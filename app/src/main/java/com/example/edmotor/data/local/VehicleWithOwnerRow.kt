package com.example.edmotor.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class VehicleWithOwnerRow(
    @Embedded val vehicle: VehicleEntity,
    @ColumnInfo(name = "ownerName") val ownerName: String?,
    @ColumnInfo(name = "ownerUsername") val ownerUsername: String?
)
