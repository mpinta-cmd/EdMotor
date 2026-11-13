package com.example.edmotor.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.OrderEntity
import com.example.edmotor.data.local.OrderItemEntity
import com.example.edmotor.data.local.OrderStatus
import com.example.edmotor.data.local.OrderWithItems
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.SupabaseClient
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

sealed interface OrderOperationResult {
    data class Success(val orderId: Int, val message: String) : OrderOperationResult
    data class Error(val message: String) : OrderOperationResult
}

class OrderRepository(
    private val database: EdMotorDatabase,
    private val cartDao: CartDao,
    private val productDao: ProductDao,
    private val supabase: SupabaseClient,
    private val sessionManager: SessionManager
) {
    fun observeOrdersForClient(clientUserId: Int): Flow<List<OrderWithItems>> =
        observeRemoteOrders(clientOnly = true)

    fun observeAllOrders(): Flow<List<OrderWithItems>> =
        observeRemoteOrders(clientOnly = false)

    private fun observeRemoteOrders(clientOnly: Boolean): Flow<List<OrderWithItems>> = flow {
        var lastKnownOrders = emptyList<OrderWithItems>()
        while (currentCoroutineContext().isActive) {
            try {
                val remote = sessionManager.remoteSession
                if (remote == null) {
                    emit(emptyList())
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val clientFilter = if (clientOnly) {
                    "&client_auth_id=eq.${remote.userId}"
                } else {
                    ""
                }
                val response = supabase.get(
                    "/rest/v1/orders?select=*,order_items(*)&order=created_at_ms.desc$clientFilter"
                )
                lastKnownOrders = parseOrders(response)
                emit(lastKnownOrders)
            } catch (e: Exception) {
                Log.w("EdMotorOrders", "No fue posible sincronizar pedidos: ${e.message}")
                emit(lastKnownOrders)
            }

            // Realtime despierta el flujo inmediatamente; el intervalo también
            // funciona como respaldo si el WebSocket se desconecta.
            withTimeoutOrNull(POLL_INTERVAL_MS) {
                supabase.realtimeChanges.first()
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createPendingOrder(client: UserEntity): OrderOperationResult {
        if (
            client.id <= 0 ||
            client.role != UserRole.CLIENT.name ||
            sessionManager.remoteSession?.userId.isNullOrBlank() ||
            sessionManager.remoteSession?.userId != client.remoteAuthId
        ) {
            return OrderOperationResult.Error("No tienes una sesión remota de cliente válida.")
        }

        return try {
            database.withTransaction {
                val cartItems = cartDao.observeForUser(client.id).first()
                if (cartItems.isEmpty()) {
                    return@withTransaction OrderOperationResult.Error("Tu carrito está vacío.")
                }

                val remoteItems = JSONArray()
                var totalCop = 0L

                for (cartItem in cartItems) {
                    if (cartItem.quantity <= 0) {
                        return@withTransaction OrderOperationResult.Error(
                            "Existe una cantidad inválida en el carrito."
                        )
                    }

                    val product = productDao.findById(cartItem.productId)
                        ?: return@withTransaction OrderOperationResult.Error(
                            "Uno de los productos ya no existe."
                        )

                    if (!product.isActive) {
                        return@withTransaction OrderOperationResult.Error(
                            "${product.name} ya no está disponible."
                        )
                    }

                    val unitPrice = product.effectivePriceCop
                    if (unitPrice <= 0L) {
                        return@withTransaction OrderOperationResult.Error(
                            "El precio de ${product.name} no es válido."
                        )
                    }

                    val subtotal = Math.multiplyExact(unitPrice, cartItem.quantity.toLong())
                    totalCop = Math.addExact(totalCop, subtotal)

                    remoteItems.put(
                        JSONObject()
                            .put("product_id", product.id)
                            .put("product_name", product.name)
                            .put("category", product.category)
                            .put("unit_price_cop", unitPrice)
                            .put("quantity", cartItem.quantity)
                            .put("subtotal_cop", subtotal)
                    )
                }

                val response = supabase.rpc(
                    "create_order",
                    JSONObject()
                        .put("p_total_cop", totalCop)
                        .put("p_items", remoteItems)
                )
                val generatedId = response.trim().trim('"').toLongOrNull()?.toInt()
                    ?: throw IllegalStateException("Supabase no devolvió el número del pedido.")

                cartDao.clearForUser(client.id)

                OrderOperationResult.Success(
                    orderId = generatedId,
                    message = "Pedido #$generatedId enviado. EdMotor recibió tu solicitud."
                )
            }
        } catch (e: Exception) {
            OrderOperationResult.Error(
                e.message ?: "No fue posible registrar el pedido en la nube."
            )
        }
    }

    suspend fun confirmOrder(
        workshop: UserEntity,
        orderId: Int
    ): OrderOperationResult {
        if (workshop.id <= 0 || workshop.role != UserRole.WORKSHOP.name) {
            return OrderOperationResult.Error("Solo el dueño puede confirmar ventas.")
        }
        if (orderId <= 0) return OrderOperationResult.Error("El pedido no es válido.")

        return try {
            database.withTransaction {
                val remoteOrder = fetchRemoteOrders(clientOnly = false)
                    .firstOrNull { it.order.id == orderId }
                    ?: return@withTransaction OrderOperationResult.Error("No encontramos el pedido.")

                if (remoteOrder.order.status != OrderStatus.PENDING) {
                    return@withTransaction OrderOperationResult.Error("Este pedido ya fue procesado.")
                }
                if (remoteOrder.items.isEmpty()) {
                    return@withTransaction OrderOperationResult.Error("El pedido no contiene productos.")
                }

                // Se valida y descuenta el stock local del taller. Si el RPC
                // remoto falla, Room revierte la transacción local.
                for (item in remoteOrder.items) {
                    val productById = productDao.findById(item.productId)
                    val product = if (
                        productById != null &&
                        productById.name.equals(item.productName, ignoreCase = true) &&
                        productById.category.equals(item.category, ignoreCase = true)
                    ) {
                        productById
                    } else {
                        productDao.findByNameAndCategory(
                            name = item.productName,
                            category = item.category
                        )
                    } ?: throw IllegalStateException(
                        "${item.productName} no existe en el inventario de este taller."
                    )

                    if (!product.isActive || product.stock < item.quantity) {
                        throw IllegalStateException(
                            "Stock insuficiente para ${item.productName}. " +
                                "Solicitadas: ${item.quantity}; disponibles: ${product.stock}."
                        )
                    }

                    val changed = productDao.updateStock(
                        productId = product.id,
                        newStock = product.stock - item.quantity
                    )
                    if (changed != 1) {
                        throw IllegalStateException(
                            "No fue posible actualizar el inventario de ${item.productName}."
                        )
                    }
                }

                supabase.rpc(
                    "confirm_order",
                    JSONObject().put("p_order_id", orderId)
                )

                OrderOperationResult.Success(
                    orderId,
                    "Pedido #$orderId confirmado. Inventario actualizado."
                )
            }
        } catch (e: Exception) {
            OrderOperationResult.Error(
                e.message ?: "No fue posible confirmar la venta."
            )
        }
    }

    suspend fun rejectOrder(
        workshop: UserEntity,
        orderId: Int
    ): OrderOperationResult {
        if (workshop.id <= 0 || workshop.role != UserRole.WORKSHOP.name) {
            return OrderOperationResult.Error("Solo el dueño puede rechazar pedidos.")
        }

        return try {
            supabase.rpc(
                "reject_order",
                JSONObject().put("p_order_id", orderId)
            )
            OrderOperationResult.Success(
                orderId,
                "Pedido #$orderId rechazado. No se modificó el inventario."
            )
        } catch (e: Exception) {
            OrderOperationResult.Error(
                e.message ?: "No fue posible rechazar el pedido."
            )
        }
    }

    private suspend fun fetchRemoteOrders(clientOnly: Boolean): List<OrderWithItems> {
        val remote = sessionManager.remoteSession
            ?: throw IllegalStateException("No hay sesión remota activa.")
        val filter = if (clientOnly) "&client_auth_id=eq.${remote.userId}" else ""
        val response = supabase.get(
            "/rest/v1/orders?select=*,order_items(*)&order=created_at_ms.desc$filter"
        )
        return parseOrders(response)
    }

    private fun parseOrders(raw: String): List<OrderWithItems> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                val orderId = row.getLong("id").toInt()
                val itemsJson = row.optJSONArray("order_items") ?: JSONArray()
                val items = buildList {
                    for (itemIndex in 0 until itemsJson.length()) {
                        val item = itemsJson.getJSONObject(itemIndex)
                        add(
                            OrderItemEntity(
                                id = item.optLong("id").toInt(),
                                orderId = orderId,
                                productId = item.optInt("product_id"),
                                productName = item.optString("product_name"),
                                category = item.optString("category"),
                                unitPriceCop = item.optLong("unit_price_cop"),
                                quantity = item.optInt("quantity"),
                                subtotalCop = item.optLong("subtotal_cop")
                            )
                        )
                    }
                }

                val entity = OrderEntity(
                    id = orderId,
                    // Los pedidos remotos no se insertan en Room; este valor
                    // solo satisface el modelo visual existente.
                    clientUserId = 0,
                    clientName = row.optString("client_name"),
                    clientUsername = row.optString("client_username"),
                    clientPhone = row.optString("client_phone").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    status = row.optString("status", OrderStatus.PENDING),
                    totalCop = row.optLong("total_cop"),
                    createdAtMillis = row.optLong("created_at_ms"),
                    updatedAtMillis = row.optLong("updated_at_ms"),
                    confirmedByUserId = null,
                    confirmedAtMillis = row.optLong("confirmed_at_ms").takeIf { it > 0L }
                )
                add(OrderWithItems(entity, items))
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
