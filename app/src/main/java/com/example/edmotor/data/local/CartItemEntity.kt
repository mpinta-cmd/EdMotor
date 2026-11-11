package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cart_items",
    primaryKeys = ["userId", "productId"],
    indices = [Index(value = ["userId"])]
)
data class CartItemEntity(
    val userId: Int,
    val productId: Int,
    val quantity: Int
)
