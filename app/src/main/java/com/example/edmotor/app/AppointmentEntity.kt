
package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "appointments",
    indices = [
        Index(value = ["clientLocalUserId"]),
        Index(value = ["status"]),
        Index(value = ["scheduledAtMillis"])
    ]
)
data class AppointmentEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val clientLocalUserId: Int,

    val clientAuthId: String,

    val clientName: String,

    val clientUsername: String,

    val clientPhone: String? = null,

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
