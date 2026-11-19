
package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.OrderDao
import com.example.edmotor.data.local.OrderEntity
import com.example.edmotor.data.local.OrderItemEntity
import com.example.edmotor.data.local.ProductDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

// =====================================================
// RESULTADOS DE OPERACIONES
// =====================================================

sealed interface OrderOperationResult {

    data object Success : OrderOperationResult

    data class Error(
        val message: String
    ) : OrderOperationResult
}


// =====================================================
// MODELO TEMPORAL PARA LOS PRODUCTOS DEL PEDIDO
// =====================================================

data class OrderItemDraft(

    val productId: Int,

    val productName: String,

    val category: String,

    val unitPriceCop: Int,

    val quantity: Int

) {

    val subtotalCop: Int
        get() = Math.multiplyExact(
            unitPriceCop,
            quantity
        )
}


// =====================================================
// REPOSITORIO PRINCIPAL DE PEDIDOS
// =====================================================

class OrderRepository(

    private val database: EdMotorDatabase,

    private val orderDao: OrderDao,

    private val productDao: ProductDao

) {

    companion object {

        const val STATUS_PENDING = "PENDING"

        const val STATUS_CONFIRMED = "CONFIRMED"

        const val STATUS_REJECTED = "REJECTED"
    }


    // =================================================
    // 1. OBSERVAR TODOS LOS PEDIDOS
    // =================================================

    fun observeAllOrders(): Flow<List<OrderEntity>> {

        return orderDao.observeAllOrders()
    }


    // =================================================
    // 2. OBSERVAR PEDIDOS DE UN CLIENTE
    // =================================================

    fun observeOrdersByClient(
        clientUserId: Int
    ): Flow<List<OrderEntity>> {

        return orderDao.observeOrdersByClient(
            clientUserId.toLong()
        )
    }


    // =================================================
    // 3. OBSERVAR PEDIDOS POR ESTADO
    // =================================================

    fun observeOrdersByStatus(
        status: String
    ): Flow<List<OrderEntity>> {

        return orderDao.observeOrdersByStatus(status)
    }


    // =================================================
    // 4. OBSERVAR DETALLES DE UN PEDIDO
    // =================================================

    fun observeOrderItems(
        orderId: Int
    ): Flow<List<OrderItemEntity>> {

        return orderDao.observeOrderItems(
            orderId.toLong()
        )
    }


    // =================================================
    // 5. OBTENER PEDIDO POR ID
    // =================================================

    suspend fun getOrderById(
        orderId: Int
    ): OrderEntity? {

        return orderDao.getOrderById(
            orderId.toLong()
        )
    }


    // =================================================
    // 6. OBTENER PRODUCTOS DE UN PEDIDO
    // =================================================

    suspend fun getOrderItems(
        orderId: Int
    ): List<OrderItemEntity> {

        return orderDao.getOrderItems(
            orderId.toLong()
        )
    }


    // =================================================
    // 7. CREAR PEDIDO
    // =================================================

    suspend fun createOrder(
        order: OrderEntity,
        items: List<OrderItemDraft>
    ): Long {

        require(items.isNotEmpty()) {
            "No puedes crear un pedido sin productos."
        }

        require(items.all {
            it.productId > 0 &&
                    it.quantity > 0 &&
                    it.unitPriceCop >= 0
        }) {
            "Los productos contienen datos inválidos."
        }

        // Calcular total del pedido.

        val calculatedTotal = items.sumOf { item ->
            item.subtotalCop.toLong()
        }

        require(
            calculatedTotal == order.totalCop.toLong()
        ) {
            "El total del pedido no coincide con sus productos."
        }

        return database.withTransaction {

            // Crear encabezado.

            val orderId = orderDao.insertOrder(order)

            // Crear detalles del pedido.

            val orderItems = items.map { item ->

                val itemSubtotal = Math.multiplyExact(
                    item.unitPriceCop,
                    item.quantity
                )

                OrderItemEntity(

                    id = 0,

                    orderId = orderId.toInt(),

                    productId = item.productId,

                    productName = item.productName,

                    category = item.category,

                    unitPriceCop = item.unitPriceCop,

                    quantity = item.quantity,

                    subtotalCop = itemSubtotal
                )
            }

            // Guardar detalles.

            orderDao.insertOrderItems(orderItems)

            orderId
        }
    }


    // =================================================
    // 8. CONFIRMAR PEDIDO Y DESCONTAR INVENTARIO
    // =================================================

    suspend fun confirmOrder(
        orderId: Int,
        ownerUserId: Int
    ): OrderOperationResult {

        return try {

            database.withTransaction {

                // Buscar pedido.

                val order = orderDao.getOrderById(
                    orderId.toLong()
                ) ?: return@withTransaction OrderOperationResult.Error(
                    "No encontramos el pedido solicitado."
                )

                // Verificar estado.

                if (order.status != STATUS_PENDING) {

                    return@withTransaction OrderOperationResult.Error(
                        "Este pedido ya fue procesado."
                    )
                }

                // Obtener productos.

                val items = orderDao.getOrderItems(
                    orderId.toLong()
                )

                if (items.isEmpty()) {

                    return@withTransaction OrderOperationResult.Error(
                        "El pedido no contiene productos."
                    )
                }

                val currentTime = System.currentTimeMillis()

                // Descontar inventario.

                for (item in items) {

                    val affectedRows =
                        productDao.decreaseStockIfAvailable(

                            productId = item.productId,

                            quantity = item.quantity,

                            updatedAtMillis = currentTime
                        )

                    if (affectedRows != 1) {

                        throw InsufficientStockException(
                            "Existencias insuficientes para ${item.productName}."
                        )
                    }
                }

                // Confirmar pedido.

                val updatedRows = orderDao.confirmOrder(

                    orderId = orderId.toLong(),

                    status = STATUS_CONFIRMED,

                    ownerUserId = ownerUserId.toLong(),

                    confirmedAtMillis = currentTime,

                    updatedAtMillis = currentTime
                )

                if (updatedRows != 1) {

                    throw IllegalStateException(
                        "No fue posible confirmar el pedido."
                    )
                }

                OrderOperationResult.Success
            }

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            OrderOperationResult.Error(
                e.message ?: "Error al confirmar el pedido."
            )
        }
    }


    // =================================================
    // 9. RECHAZAR PEDIDO
    // =================================================

    suspend fun rejectOrder(
        orderId: Int
    ): OrderOperationResult {

        return try {

            database.withTransaction {

                val order = orderDao.getOrderById(
                    orderId.toLong()
                ) ?: return@withTransaction OrderOperationResult.Error(
                    "No encontramos el pedido."
                )

                if (order.status != STATUS_PENDING) {

                    return@withTransaction OrderOperationResult.Error(
                        "Este pedido ya fue procesado."
                    )
                }

                val updatedRows = orderDao.updateOrderStatus(

                    orderId = orderId.toLong(),

                    status = STATUS_REJECTED,

                    updatedAtMillis = System.currentTimeMillis()
                )

                if (updatedRows != 1) {

                    return@withTransaction OrderOperationResult.Error(
                        "No fue posible rechazar el pedido."
                    )
                }

                OrderOperationResult.Success
            }

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            OrderOperationResult.Error(
                e.message ?: "Error al rechazar el pedido."
            )
        }
    }


    // =================================================
    // 10. CONTAR PEDIDOS
    // =================================================

    suspend fun getOrdersCount(): Int {

        return orderDao.getOrdersCount()
    }


    // =================================================
    // 11. CONTAR PEDIDOS POR CLIENTE
    // =================================================

    suspend fun getOrdersCountByClient(
        clientUserId: Int
    ): Int {

        return orderDao.getOrdersCountByClient(
            clientUserId.toLong()
        )
    }


    // =================================================
    // EXCEPCIÓN DE INVENTARIO
    // =================================================

    private class InsufficientStockException(
        message: String
    ) : Exception(message)

}
