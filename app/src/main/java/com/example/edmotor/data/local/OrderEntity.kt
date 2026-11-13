
package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object OrderStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val REJECTED = "REJECTED"
}

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientUserId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["confirmedByUserId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["clientUserId"]),
        Index(value = ["status"]),
        Index(value = ["confirmedByUserId"])
    ]
)
data class OrderEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val clientUserId: Int,

    // Datos del cliente conservados al crear el pedido.
    val clientName: String,
    val clientUsername: String,
    val clientPhone: String? = null,

    val status: String = OrderStatus.PENDING,

    val totalCop: Long,

    val createdAtMillis: Long = System.currentTimeMillis(),

    val updatedAtMillis: Long = System.currentTimeMillis(),

    val confirmedByUserId: Int? = null,

    val confirmedAtMillis: Long? = null
)
