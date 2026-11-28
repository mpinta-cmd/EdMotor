package com.example.edmotor.online

import android.content.Context
import java.util.UUID

data class PendingOnlineOrder(
    val requestId: String,
    val items: Map<Long, Int>,
    val expectedTotalCop: Long? = null
)

internal class OnlineCartStore(context: Context, project: String) {
    private val preferences = context.getSharedPreferences("edmotor_online_cart_$project", Context.MODE_PRIVATE)

    fun read(clientId: Long? = null): Map<Long, Int> =
        decodeItems(preferences.getStringSet(key(clientId), emptySet()).orEmpty())

    fun save(clientId: Long?, cart: Map<Long, Int>) = synchronized(preferences) {
        preferences.edit().putStringSet(key(clientId), encode(cart)).apply()
    }

    fun favorites(accountId: Long? = null): Set<Long> =
        preferences.getStringSet(favoritesKey(accountId), emptySet()).orEmpty()
            .mapNotNull { value -> value.toLongOrNull()?.takeIf { it > 0 } }.toSet()

    fun saveFavorites(accountId: Long?, products: Set<Long>) = synchronized(preferences) {
        preferences.edit().putStringSet(favoritesKey(accountId), products.map(Long::toString).toSet()).apply()
    }

    fun moveGuestFavoritesTo(clientId: Long): Set<Long> = synchronized(preferences) {
        val guest = favorites()
        val account = favorites(clientId)
        if (guest.isEmpty()) return@synchronized account
        val combined = account + guest
        preferences.edit()
            .putStringSet(favoritesKey(clientId), combined.map(Long::toString).toSet())
            .remove(favoritesKey(null))
            .apply()
        combined
    }

    fun moveGuestCartTo(clientId: Long): Map<Long, Int> = synchronized(preferences) {
        val accountCart = read(clientId).toMutableMap()
        val guestCart = read()
        if (guestCart.isEmpty()) return@synchronized accountCart

        guestCart.forEach { (id, quantity) ->
            val total = (accountCart[id] ?: 0).toLong() + quantity
            accountCart[id] = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        preferences.edit().putStringSet(key(clientId), encode(accountCart)).remove(key(null)).apply()
        accountCart
    }

    fun pendingOrder(clientId: Long): PendingOnlineOrder? {
        val value = preferences.getString(pendingKey(clientId), null) ?: return null
        val fields = value.split('|', limit = 4)
        val requestId = fields.first()
        val lines = fields.getOrNull(1).orEmpty().split(',')
        val items = decodeItems(lines)
        val encodedTotal = fields.getOrNull(2)
        val total = encodedTotal?.toLongOrNull()?.takeIf { it > 0L }
        val validId = runCatching { UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)
        if (!validId || items.isEmpty() || items.size != lines.size ||
            fields.size > 3 || (encodedTotal != null && total == null)
        ) {
            throw OnlineInputError("No pudimos recuperar la solicitud guardada en este teléfono.")
        }
        return PendingOnlineOrder(requestId, items, total)
    }

    fun prepareOrder(
        clientId: Long,
        items: Map<Long, Int>,
        expectedTotalCop: Long? = null
    ): PendingOnlineOrder = synchronized(preferences) {
        if (items.isEmpty() || items.any { (id, quantity) -> id <= 0 || quantity <= 0 } ||
            (expectedTotalCop != null && expectedTotalCop <= 0L)
        ) {
            throw OnlineInputError("Agrega repuestos válidos al carrito.")
        }
        val order = pendingOrder(clientId)
            ?: PendingOnlineOrder(UUID.randomUUID().toString(), items.toSortedMap(), expectedTotalCop)
        if (order.items != items || order.expectedTotalCop != expectedTotalCop) {
            throw OnlineInputError("Primero verifica el resultado del pedido anterior.")
        }
        // Repeat the commit on retries in case an earlier attempt only changed the in-memory preferences.
        if (!preferences.edit().putString(pendingKey(clientId), encodeOrder(order)).commit()) {
            throw OnlineInputError("No pudimos guardar el pedido en este teléfono. Intenta de nuevo.")
        }
        order
    }

    fun acknowledgeOrder(clientId: Long, order: PendingOnlineOrder): Map<Long, Int> = synchronized(preferences) {
        val cart = read(clientId)
        if (!isCurrentOrder(clientId, order)) return@synchronized cart
        val remaining = cart.mapNotNull { (id, quantity) ->
            val count = quantity - (order.items[id] ?: 0)
            if (count > 0) id to count else null
        }.toMap()
        val saved = preferences.edit()
            .putStringSet(key(clientId), encode(remaining))
            .remove(pendingKey(clientId))
            .commit()
        if (!saved) {
            // SharedPreferences updates memory even when the disk commit fails. Retain the UUID for the next retry.
            preferences.edit().putStringSet(key(clientId), encode(cart))
                .putString(pendingKey(clientId), encodeOrder(order)).apply()
            throw OnlineInputError("El servidor recibió el pedido. Intenta verificarlo para guardar el resultado en este teléfono.")
        }
        remaining
    }

    fun discardOrder(clientId: Long, order: PendingOnlineOrder): Unit = synchronized(preferences) {
        if (!isCurrentOrder(clientId, order)) return@synchronized
        if (!preferences.edit().remove(pendingKey(clientId)).commit()) {
            preferences.edit().putString(pendingKey(clientId), encodeOrder(order)).apply()
            throw OnlineInputError("No pudimos actualizar la solicitud guardada en este teléfono. Intenta de nuevo.")
        }
    }

    private fun isCurrentOrder(clientId: Long, order: PendingOnlineOrder): Boolean {
        val pending = pendingOrder(clientId) ?: return false
        if (pending != order) throw OnlineInputError("Hay otra solicitud de pedido pendiente en este teléfono.")
        return true
    }

    private fun key(clientId: Long?): String = clientId?.let { "client_$it" } ?: "guest"

    private fun favoritesKey(accountId: Long?): String = "favorites_${key(accountId)}"

    private fun pendingKey(clientId: Long): String = "pending_$clientId"

    private fun encode(cart: Map<Long, Int>): Set<String> =
        cart.mapTo(mutableSetOf()) { (id, quantity) -> "$id:$quantity" }

    private fun encodeOrder(order: PendingOnlineOrder): String {
        val request = "${order.requestId}|${encode(order.items).joinToString(",")}"
        return order.expectedTotalCop?.let { "$request|$it" } ?: request
    }

    private fun decodeItems(entries: Iterable<String>): Map<Long, Int> = entries.mapNotNull { entry ->
        val id = entry.substringBefore(':').toLongOrNull()
        val quantity = entry.substringAfter(':', "").toIntOrNull()
        if (id != null && id > 0 && quantity != null && quantity > 0) id to quantity else null
    }.toMap()
}
