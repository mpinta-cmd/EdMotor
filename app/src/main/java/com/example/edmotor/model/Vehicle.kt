package com.example.edmotor.model

data class Vehicle(
    val id: Int,
    val brand: String,
    val model: String,
    val year: Int,
    val engine: String,
    val plate: String = "",
    val mileage: String = "",
    val notes: String = "",
    val ownerUserId: Int? = null,
    val ownerName: String? = null,
    val ownerUsername: String? = null
)
