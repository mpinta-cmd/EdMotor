package com.example.edmotor.model

object OrderStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val REJECTED = "REJECTED"
}

data class Order(
    val id: Int,
    val clientUserId: Int,
    val clientName: String,
    val clientUsername: String,
    val clientPhone: String?,
    val status: String,
    val totalCop: Long,
    val createdAtMillis: Long,
    val confirmedAtMillis: Long?
)

data class OrderItem(
    val productName: String,
    val category: String,
    val unitPriceCop: Long,
    val quantity: Int,
    val subtotalCop: Long
)

data class OrderWithItems(
    val order: Order,
    val items: List<OrderItem>
)
