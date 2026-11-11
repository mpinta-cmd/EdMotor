package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["ownerUserId"])]
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val brand: String,
    val model: String,
    val year: Int,
    val engine: String,
    val plate: String = "",
    val mileage: String = "",
    val notes: String = "",
    val ownerUserId: Int? = null
)
