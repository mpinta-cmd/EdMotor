package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val brand: String,

    val model: String,

    val year: Int,

    val engine: String,

    val plate: String = "",

    val mileage: String = "",

    val notes: String = ""
)