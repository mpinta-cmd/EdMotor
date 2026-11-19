package com.example.edmotor.data.repository

import android.net.Uri
import android.util.Log
import com.example.edmotor.data.remote.RemoteOrderDto
import com.example.edmotor.data.remote.SupabaseClient
import com.example.edmotor.data.remote.parseRemoteOrders
import com.example.edmotor.model.Order
import com.example.edmotor.model.OrderItem
import com.example.edmotor.model.OrderReference
import com.example.edmotor.model.OrderSource
import com.example.edmotor.model.OrderWithItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

class RemoteOrderRepository(private val supabase: SupabaseClient) {

    fun observeOrders(
        authId: String,
        isWorkshop: Boolean,
        isSessionActive: () -> Boolean
    ): Flow<List<OrderWithItems>> = flow {
        // Room can be displayed immediately, even if the remote query takes time.
        emit(emptyList())
        while (currentCoroutineContext().isActive && isSessionActive()) {
            val orders = try {
                fetchOrders(authId, isWorkshop)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w("EdMotorOrders", "No fue posible actualizar el historial en línea.")
                null
            }

            if (!isSessionActive()) break
            if (orders != null) emit(orders)
            withTimeoutOrNull(POLL_INTERVAL_MS) { supabase.realtimeChanges.first() }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchOrders(authId: String, isWorkshop: Boolean): List<OrderWithItems> = buildList {
        val clientFilter = if (isWorkshop) "" else "&client_auth_id=eq.${Uri.encode(authId)}"
        var beforeId: Int? = null
        while (true) {
            val cursor = beforeId?.let { "&id=lt.$it" }.orEmpty()
            val response = supabase.get(
                "/rest/v1/orders?select=*,order_items(*)&order=id.desc&limit=$PAGE_SIZE$clientFilter$cursor",
                expectedUserId = authId
            )
            val page = parseRemoteOrders(response)
            addAll(page.filter { isWorkshop || it.clientAuthId == authId }.map { it.toModel() })
            if (page.size < PAGE_SIZE) break

            val nextId = page.last().id
            check(beforeId == null || nextId < beforeId) { "La paginación del historial no avanzó." }
            beforeId = nextId
        }
    }

    companion object {
        private const val PAGE_SIZE = 200
        private const val POLL_INTERVAL_MS = 45_000L
    }
}

private fun RemoteOrderDto.toModel() = OrderWithItems(
    order = Order(
        reference = OrderReference(OrderSource.REMOTE, id),
        clientUserId = null,
        clientAuthId = clientAuthId,
        clientName = clientName,
        clientUsername = clientUsername,
        clientPhone = clientPhone,
        status = status,
        totalCop = totalCop,
        createdAtMillis = createdAtMillis,
        confirmedAtMillis = confirmedAtMillis
    ),
    items = items.map {
        OrderItem(it.productName, it.category, it.unitPriceCop, it.quantity, it.subtotalCop)
    }
)
