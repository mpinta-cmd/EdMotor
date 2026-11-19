package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.OrderDao
import com.example.edmotor.data.local.OrderEntity
import com.example.edmotor.data.local.OrderItemEntity
import com.example.edmotor.data.local.OrderWithItems as StoredOrderWithItems
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.Order
import com.example.edmotor.model.OrderItem
import com.example.edmotor.model.OrderReference
import com.example.edmotor.model.OrderSource
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.OrderWithItems
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

sealed interface OrderOperationResult {
    data class Success(val message: String) : OrderOperationResult
    data class Error(val message: String) : OrderOperationResult
}

class OrderRepository(
    private val database: EdMotorDatabase,
    private val orderDao: OrderDao,
    private val productDao: ProductDao,
    private val cartDao: CartDao,
    private val sessionManager: SessionManager,
    private val remoteOrders: RemoteOrderRepository
) {

    fun observeOrders(user: UserEntity): Flow<List<OrderWithItems>> {
        val localOrders = when (user.role) {
            UserRole.WORKSHOP.name -> orderDao.observeAllWithItems()
            UserRole.CLIENT.name -> orderDao.observeByClientWithItems(user.id)
            else -> return flowOf(emptyList())
        }.map { orders -> orders.map { it.toModel() } }

        val authId = user.remoteAuthId?.takeIf { it.isNotBlank() } ?: return localOrders
        val remoteHistory = remoteOrders.observeOrders(
            authId = authId,
            isWorkshop = user.role == UserRole.WORKSHOP.name,
            isSessionActive = {
                sessionManager.currentUser.value?.id == user.id &&
                    sessionManager.remoteSession?.userId == authId
            }
        )
        return combine(localOrders, remoteHistory) { local, remote ->
            (local + remote).sortedByDescending { it.order.createdAtMillis }
        }
    }

    suspend fun createPendingOrder(client: UserEntity): OrderOperationResult =
        orderOperation("No fue posible guardar el pedido.") {
            database.withTransaction {
                val activeClient = requireActiveUser(client, UserRole.CLIENT)
                val cartItems = cartDao.getItemsForUser(activeClient.id)
                require(cartItems.isNotEmpty()) { "Tu carrito está vacío." }

                var totalCop = 0L
                val orderItems = cartItems.map { item ->
                    require(item.quantity > 0) { "El carrito contiene una cantidad inválida." }
                    val product = productDao.findById(item.productId)
                        ?: error("Un repuesto del carrito ya no existe.")
                    check(product.isActive) { "${product.name} ya no está disponible." }
                    check(product.stock >= item.quantity) {
                        "Solo hay ${product.stock} unidades de ${product.name}."
                    }

                    val unitPrice = product.effectivePriceCop
                    check(unitPrice > 0L) { "El precio de ${product.name} no es válido." }
                    val subtotal = Math.multiplyExact(unitPrice, item.quantity.toLong())
                    totalCop = Math.addExact(totalCop, subtotal)

                    OrderItemEntity(
                        orderId = 0,
                        productId = product.id,
                        productName = product.name,
                        category = product.category,
                        unitPriceCop = unitPrice,
                        quantity = item.quantity,
                        subtotalCop = subtotal
                    )
                }

                val orderId = Math.toIntExact(
                    orderDao.insertOrder(
                        OrderEntity(
                            clientUserId = activeClient.id,
                            clientName = activeClient.displayName,
                            clientUsername = activeClient.username,
                            clientPhone = activeClient.phone,
                            totalCop = totalCop
                        )
                    )
                )
                orderDao.insertOrderItems(orderItems.map { it.copy(orderId = orderId) })
                cartDao.clearForUser(activeClient.id)

                OrderOperationResult.Success("Pedido #$orderId guardado en este dispositivo.")
            }
        }

    suspend fun confirmOrder(
        workshop: UserEntity,
        reference: OrderReference
    ): OrderOperationResult = orderOperation("No fue posible confirmar el pedido.") {
        database.withTransaction {
            val activeWorkshop = requireActiveUser(workshop, UserRole.WORKSHOP)
            val orderId = reference.requireLocalId()
            val order = orderDao.getOrderById(orderId)
                ?: error("No encontramos el pedido solicitado.")
            check(order.status == OrderStatus.PENDING) { "Este pedido ya fue procesado." }

            val items = orderDao.getOrderItems(orderId)
            check(items.isNotEmpty()) { "El pedido no contiene productos." }

            val now = System.currentTimeMillis()
            for (item in items) {
                val updated = productDao.decreaseStockIfAvailable(
                    productId = item.productId,
                    quantity = item.quantity,
                    updatedAtMillis = now
                )
                check(updated == 1) { "Existencias insuficientes para ${item.productName}." }
            }

            val updated = orderDao.confirmOrder(
                orderId = orderId,
                status = OrderStatus.CONFIRMED,
                ownerUserId = activeWorkshop.id.toLong(),
                confirmedAtMillis = now,
                updatedAtMillis = now
            )
            check(updated == 1) { "Este pedido ya fue procesado." }
            OrderOperationResult.Success("Pedido #${reference.id} confirmado. Inventario actualizado.")
        }
    }

    suspend fun rejectOrder(
        workshop: UserEntity,
        reference: OrderReference
    ): OrderOperationResult = orderOperation("No fue posible rechazar el pedido.") {
        requireActiveUser(workshop, UserRole.WORKSHOP)
        val orderId = reference.requireLocalId()
        val updated = orderDao.updateOrderStatus(
            orderId = orderId,
            status = OrderStatus.REJECTED,
            updatedAtMillis = System.currentTimeMillis()
        )
        check(updated == 1) { "El pedido no existe o ya fue procesado." }
        OrderOperationResult.Success("Pedido #${reference.id} rechazado.")
    }

    private fun requireActiveUser(user: UserEntity, requiredRole: UserRole): UserEntity {
        val active = sessionManager.currentUser.value
        check(active != null && active.id > 0 && active.id == user.id && active.role == requiredRole.name) {
            "La sesión activa no tiene permiso para realizar esta operación."
        }
        return active
    }

    private fun OrderReference.requireLocalId(): Long {
        check(source == OrderSource.LOCAL) { "Los pedidos del historial en línea son de consulta." }
        require(id > 0) { "El pedido no es válido." }
        return id.toLong()
    }

    private suspend inline fun orderOperation(
        fallbackMessage: String,
        action: () -> OrderOperationResult.Success
    ): OrderOperationResult = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (_: ArithmeticException) {
        OrderOperationResult.Error("El pedido supera los límites de almacenamiento.")
    } catch (e: Exception) {
        OrderOperationResult.Error(e.message ?: fallbackMessage)
    }
}

private fun StoredOrderWithItems.toModel() = OrderWithItems(
    order = Order(
        reference = OrderReference(OrderSource.LOCAL, order.id),
        clientUserId = order.clientUserId,
        clientAuthId = null,
        clientName = order.clientName,
        clientUsername = order.clientUsername,
        clientPhone = order.clientPhone,
        status = order.status,
        totalCop = order.totalCop,
        createdAtMillis = order.createdAtMillis,
        confirmedAtMillis = order.confirmedAtMillis
    ),
    items = items.map {
        OrderItem(it.productName, it.category, it.unitPriceCop, it.quantity, it.subtotalCop)
    }
)
