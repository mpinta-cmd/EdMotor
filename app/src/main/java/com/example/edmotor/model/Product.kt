package com.example.edmotor.model

import androidx.annotation.DrawableRes

data class Product(
    val id: Int,
    val name: String,
    val description: String,
    val price: String,
    @DrawableRes val imageRes: Int,
    val contentDescription: String = name,
    val rating: Double,
    val badge: String = "",
    val category: String = ""
)