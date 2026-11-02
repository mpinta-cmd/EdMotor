
package com.example.edmotor.data.repository

import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.CartItemEntity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CartRepository(
    private val dao: CartDao
) {

    // ==========================================
    // READ: OBSERVAR LAS CANTIDADES DEL CARRITO
    // ==========================================

    val cartQuantity: Flow<Map<Int, Int>> =
        dao.observeAll().map { items ->

            items.associate { item ->
                item.productId to item.quantity
            }
        }

    // ==========================================
    // CREATE / UPDATE: AUMENTAR CANTIDAD
    // ==========================================

    suspend fun increaseQuantity(productId: Int) {

        if (productId <= 0) return

        val currentItem = dao.findByProductId(productId)

        val newQuantity = (currentItem?.quantity ?: 0) + 1

        dao.upsert(
            CartItemEntity(
                productId = productId,
                quantity = newQuantity
            )
        )
    }

    // ==========================================
    // UPDATE / DELETE: DISMINUIR CANTIDAD
    // ==========================================

    suspend fun decreaseQuantity(productId: Int) {

        val currentItem = dao.findByProductId(productId) ?: return

        if (currentItem.quantity > 1) {

            dao.upsert(
                currentItem.copy(
                    quantity = currentItem.quantity - 1
                )
            )

        } else {

            dao.deleteByProductId(productId)
        }
    }

    // ==========================================
    // DELETE: ELIMINAR UN PRODUCTO DEL CARRITO
    // ==========================================

    suspend fun removeProduct(productId: Int) {

        dao.deleteByProductId(productId)
    }

    // ==========================================
    // DELETE: VACIAR TODO EL CARRITO
    // ==========================================

    suspend fun clearCart() {

        dao.clearAll()
    }
}
