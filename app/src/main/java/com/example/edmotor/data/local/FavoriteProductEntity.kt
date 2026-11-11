package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favorite_products",
    primaryKeys = ["userId", "productId"],
    indices = [Index(value = ["userId"])]
)
data class FavoriteProductEntity(
    val userId: Int,
    val productId: Int
)
