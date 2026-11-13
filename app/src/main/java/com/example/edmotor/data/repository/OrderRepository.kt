
package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.OrderDao
import com.example.edmotor.data.local.OrderEntity
import com.example.edmotor.data.local.OrderItemEntity
import com.example.edmotor.data.local.OrderStatus
import com.example.edmotor.data.local.OrderWithItems
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed interface OrderOperationResult {

    data class Success(
        val orderId: Int,
        val message: String
    ) : OrderOperationResult

    data class Error(
        val message: String
    ) : OrderOperationResult
}

class OrderRepository(
    private val database: EdMotorDatabase,
    private val orderDao: OrderDao,
    private val cartDao: CartDao,
    private val productDao: ProductDao
) {

    // ==========================================
    // OBSERVAR PEDIDOS DEL CLIENTE
    // ==========================================

    fun observeOrdersForClient(
        clientUserId: Int
    ): Flow<List<OrderWithItems>> =
        orderDao.observeOrdersForClient(clientUserId)

    // ==========================================
    // OBSERVAR TODOS LOS PEDIDOS
    // ==========================================

    fun observeAllOrders(): Flow<List<OrderWithItems>> =
        orderDao.observeAllOrders()

    // ==========================================
    // CREAR PEDIDO DEL CLIENTE
    // NO DESCUENTA INVENTARIO
    // ==========================================

    suspend fun createPendingOrder(
        client: UserEntity
    ): OrderOperationResult {

        if (client.id <= 0 ||
            client.role != UserRole.CLIENT.name
        ) {
            return OrderOperationResult.Error(
                "No tienes permisos para crear pedidos."
            )
        }

        return try {

            database.withTransaction {

                // Consultar carrito del cliente.

                val cartItems = cartDao
                    .observeForUser(client.id)
                    .first()

                if (cartItems.isEmpty()) {

                    return@withTransaction OrderOperationResult.Error(
                        "Tu carrito está vacío."
                    )
                }

                val orderItems = mutableListOf<OrderItemEntity>()

                var totalCop = 0L

                // Validar y conservar los datos de cada producto.

                for (cartItem in cartItems) {

                    if (cartItem.quantity <= 0) {

                        return@withTransaction OrderOperationResult.Error(
                            "Existe una cantidad inválida en el carrito."
                        )
                    }

                    val product = productDao.findById(
                        cartItem.productId
                    ) ?: return@withTransaction OrderOperationResult.Error(
                        "Uno de los productos ya no existe."
                    )

                    if (!product.isActive) {

                        return@withTransaction OrderOperationResult.Error(
                            "${product.name} ya no está disponible."
                        )
                    }

                    if (product.stock < cartItem.quantity) {

                        return@withTransaction OrderOperationResult.Error(
                            "Existencias insuficientes para ${product.name}. Disponibles: ${product.stock}."
                        )
                    }

                    val unitPrice = product.effectivePriceCop

                    if (unitPrice <= 0L) {

                        return@withTransaction OrderOperationResult.Error(
                            "El precio de ${product.name} no es válido."
                        )
                    }

                    val subtotal = Math.multiplyExact(
                        unitPrice,
                        cartItem.quantity.toLong()
                    )

                    totalCop = Math.addExact(
                        totalCop,
                        subtotal
                    )

                    orderItems.add(
                        OrderItemEntity(
                            orderId = 0,
                            productId = product.id,
                            productName = product.name,
                            category = product.category,
                            unitPriceCop = unitPrice,
                            quantity = cartItem.quantity,
                            subtotalCop = subtotal
                        )
                    )
                }

                val now = System.currentTimeMillis()

                // Registrar encabezado del pedido.

                val generatedId = orderDao.insertOrder(
                    OrderEntity(
                        clientUserId = client.id,
                        clientName = client.displayName,
                        clientUsername = client.username,
                        clientPhone = client.phone,
                        status = OrderStatus.PENDING,
                        totalCop = totalCop,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                ).toInt()

                // Registrar los productos asociados.

                orderDao.insertItems(
                    orderItems.map { item ->
                        item.copy(orderId = generatedId)
                    }
                )

                // Vaciar carrito solamente después
                // de guardar correctamente el pedido.

                cartDao.clearForUser(client.id)

                OrderOperationResult.Success(
                    orderId = generatedId,
                    message = "Pedido #$generatedId enviado correctamente. Está pendiente de confirmación por EdMotor."
                )
            }

        } catch (e: Exception) {

            OrderOperationResult.Error(
                e.message ?: "No fue posible registrar el pedido."
            )
        }
    }

    // ==========================================
    // CONFIRMAR VENTA
    // EXCLUSIVO DEL TALLER
    // ==========================================

    suspend fun confirmOrder(
        workshop: UserEntity,
        orderId: Int
    ): OrderOperationResult {

        if (workshop.id <= 0 ||
            workshop.role != UserRole.WORKSHOP.name
        ) {
            return OrderOperationResult.Error(
                "Solo el dueño puede confirmar ventas."
            )
        }

        if (orderId <= 0) {

            return OrderOperationResult.Error(
                "El pedido no es válido."
            )
        }

        return try {

            database.withTransaction {

                val order = orderDao.findWithItems(orderId)
                    ?: return@withTransaction OrderOperationResult.Error(
                        "No encontramos el pedido."
                    )

                if (order.order.status != OrderStatus.PENDING) {

                    return@withTransaction OrderOperationResult.Error(
                        "Este pedido ya fue procesado."
                    )
                }

                if (order.items.isEmpty()) {

                    return@withTransaction OrderOperationResult.Error(
                        "El pedido no contiene productos."
                    )
                }

                // Validar y descontar cada producto.
                // Cualquier fallo revierte toda la transacción.

                for (item in order.items) {

                    val product = productDao.findById(
                        item.productId
                    ) ?: throw StockConflictException(
                        "${item.productName} ya no existe en el inventario."
                    )

                    if (!product.isActive) {

                        throw StockConflictException(
                            "${item.productName} ya no está disponible."
                        )
                    }

                    if (product.stock < item.quantity) {

                        throw StockConflictException(
                            "Stock insuficiente para ${item.productName}. Solicitadas: ${item.quantity}. Disponibles: ${product.stock}."
                        )
                    }

                    val updated = productDao.updateStock(
                        productId = product.id,
                        newStock = product.stock - item.quantity
                    )

                    if (updated != 1) {

                        throw StockConflictException(
                            "No fue posible actualizar el inventario de ${item.productName}."
                        )
                    }
                }

                val now = System.currentTimeMillis()

                val updatedOrder = order.order.copy(
                    status = OrderStatus.CONFIRMED,
                    confirmedByUserId = workshop.id,
                    confirmedAtMillis = now,
                    updatedAtMillis = now
                )

                val rows = orderDao.updateOrder(updatedOrder)

                if (rows != 1) {

                    throw IllegalStateException(
                        "No fue posible actualizar el estado del pedido."
                    )
                }

                OrderOperationResult.Success(
                    orderId = orderId,
                    message = "Venta #$orderId confirmada. Inventario actualizado correctamente."
                )
            }

        } catch (e: StockConflictException) {

            OrderOperationResult.Error(
                e.message ?: "No hay existencias suficientes."
            )

        } catch (e: Exception) {

            OrderOperationResult.Error(
                e.message ?: "No fue posible confirmar la venta."
            )
        }
    }

    // ==========================================
    // RECHAZAR PEDIDO
    // EXCLUSIVO DEL TALLER
    // ==========================================

    suspend fun rejectOrder(
        workshop: UserEntity,
        orderId: Int
    ): OrderOperationResult {

        if (workshop.id <= 0 ||
            workshop.role != UserRole.WORKSHOP.name
        ) {
            return OrderOperationResult.Error(
                "No tienes permisos para rechazar pedidos."
            )
        }

        return try {

            database.withTransaction {

                val order = orderDao.findWithItems(orderId)
                    ?: return@withTransaction OrderOperationResult.Error(
                        "No encontramos el pedido."
                    )

                if (order.order.status != OrderStatus.PENDING) {

                    return@withTransaction OrderOperationResult.Error(
                        "Este pedido ya fue procesado."
                    )
                }

                val updated = orderDao.updateOrder(
                    order.order.copy(
                        status = OrderStatus.REJECTED,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )

                if (updated != 1) {

                    return@withTransaction OrderOperationResult.Error(
                        "No fue posible rechazar el pedido."
                    )
                }

                OrderOperationResult.Success(
                    orderId = orderId,
                    message = "Pedido #$orderId rechazado. No se modificó el inventario."
                )
            }

        } catch (e: Exception) {

            OrderOperationResult.Error(
                e.message ?: "No fue posible rechazar el pedido."
            )
        }
    }

    private class StockConflictException(
        message: String
    ) : Exception(message)
}
