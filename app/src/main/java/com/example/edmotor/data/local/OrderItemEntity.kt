
package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["orderId"])
    ]
)
data class OrderItemEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val orderId: Int,

    val productId: Int,

    val productName: String,

    val category: String,

    val unitPriceCop: Long,

    val quantity: Int,

    val subtotalCop: Long
)
