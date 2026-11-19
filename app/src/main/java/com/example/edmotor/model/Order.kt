package com.example.edmotor.model

object OrderStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val REJECTED = "REJECTED"
}

enum class OrderSource {
    LOCAL,
    REMOTE
}

data class OrderReference(
    val source: OrderSource,
    val id: Int
)

data class Order(
    val reference: OrderReference,
    val clientUserId: Int?,
    val clientAuthId: String?,
    val clientName: String,
    val clientUsername: String,
    val clientPhone: String?,
    val status: String,
    val totalCop: Long,
    val createdAtMillis: Long,
    val confirmedAtMillis: Long?
) {
    val id: Int get() = reference.id
}

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
