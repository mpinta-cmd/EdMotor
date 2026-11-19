package com.example.edmotor.data.remote

import org.json.JSONArray
import org.json.JSONObject

internal data class RemoteOrderDto(
    val id: Int,
    val clientAuthId: String,
    val clientName: String,
    val clientUsername: String,
    val clientPhone: String?,
    val status: String,
    val totalCop: Long,
    val createdAtMillis: Long,
    val confirmedAtMillis: Long?,
    val items: List<RemoteOrderItemDto>
)

internal data class RemoteOrderItemDto(
    val productName: String,
    val category: String,
    val unitPriceCop: Long,
    val quantity: Int,
    val subtotalCop: Long
)

internal fun parseRemoteOrders(response: String): List<RemoteOrderDto> =
    JSONArray(response).objects { order ->
        RemoteOrderDto(
            id = order.getInt("id"),
            clientAuthId = order.getString("client_auth_id"),
            clientName = order.getString("client_name"),
            clientUsername = order.getString("client_username"),
            clientPhone = order.optString("client_phone").takeUnless {
                order.isNull("client_phone") || it.isBlank()
            },
            status = order.getString("status"),
            totalCop = order.getLong("total_cop"),
            createdAtMillis = order.getLong("created_at_ms"),
            confirmedAtMillis = order.optLong("confirmed_at_ms").takeIf { it > 0 },
            items = order.getJSONArray("order_items").objects { item ->
                RemoteOrderItemDto(
                    productName = item.getString("product_name"),
                    category = item.getString("category"),
                    unitPriceCop = item.getLong("unit_price_cop"),
                    quantity = item.getInt("quantity"),
                    subtotalCop = item.getLong("subtotal_cop")
                )
            }
        )
    }

private inline fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }
