
package com.example.edmotor.data.repository

import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.CartItemEntity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CartRepository(
    private val dao: CartDao
) {

    fun observeCart(
        userId: Int
    ): Flow<Map<Int, Int>> {

        return dao.observeForUser(userId).map { items ->

            items.associate {
                it.productId to it.quantity
            }
        }
    }


    suspend fun increaseQuantity(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        val current = dao.findByProductId(
            userId,
            productId
        )

        if (current != null && current.quantity == Int.MAX_VALUE) {
            return
        }

        dao.upsert(
            CartItemEntity(
                userId = userId,
                productId = productId,
                quantity = (current?.quantity ?: 0) + 1
            )
        )
    }


    suspend fun decreaseQuantity(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        val current = dao.findByProductId(
            userId,
            productId
        ) ?: return

        if (current.quantity > 1) {

            dao.upsert(
                current.copy(
                    quantity = current.quantity - 1
                )
            )

        } else {

            dao.deleteByProductId(userId, productId)
        }
    }


    suspend fun removeProduct(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        dao.deleteByProductId(userId, productId)
    }


    suspend fun clearCart(userId: Int) {

        if (userId <= 0) return

        dao.clearForUser(userId)
    }
}
