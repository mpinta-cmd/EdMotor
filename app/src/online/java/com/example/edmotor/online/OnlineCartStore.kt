package com.example.edmotor.online

import android.content.Context

internal class OnlineCartStore(context: Context, project: String) {
    private val preferences = context.getSharedPreferences("edmotor_online_cart_$project", Context.MODE_PRIVATE)

    fun read(clientId: Long? = null): Map<Long, Int> =
        preferences.getStringSet(key(clientId), emptySet()).orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore(':').toLongOrNull()
            val quantity = entry.substringAfter(':', "").toIntOrNull()
            if (id != null && id > 0 && quantity != null && quantity > 0) id to quantity else null
        }.toMap()

    fun save(clientId: Long?, cart: Map<Long, Int>) {
        preferences.edit().putStringSet(key(clientId), encode(cart)).apply()
    }

    fun moveGuestCartTo(clientId: Long): Map<Long, Int> {
        val accountCart = read(clientId).toMutableMap()
        val guestCart = read()
        if (guestCart.isEmpty()) return accountCart

        guestCart.forEach { (id, quantity) ->
            val total = (accountCart[id] ?: 0).toLong() + quantity
            accountCart[id] = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        preferences.edit().putStringSet(key(clientId), encode(accountCart)).remove(key(null)).apply()
        return accountCart
    }

    private fun key(clientId: Long?): String = clientId?.let { "client_$it" } ?: "guest"

    private fun encode(cart: Map<Long, Int>): Set<String> =
        cart.mapTo(mutableSetOf()) { (id, quantity) -> "$id:$quantity" }
}
