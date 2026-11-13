
package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.CartItemEntity
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.ProductDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface CartOperationResult {

    data class Success(
        val message: String
    ) : CartOperationResult

    data class Error(
        val message: String
    ) : CartOperationResult
}

class CartRepository(
    private val database: EdMotorDatabase,
    private val dao: CartDao,
    private val productDao: ProductDao
) {

    // ==========================================
    // OBSERVAR CARRITO DEL USUARIO
    // ==========================================

    fun observeCart(
        userId: Int
    ): Flow<Map<Int, Int>> {

        return dao.observeForUser(userId).map { items ->

            items.associate {
                it.productId to it.quantity
            }
        }
    }

    // ==========================================
    // AGREGAR PRODUCTO VALIDANDO INVENTARIO
    // ==========================================

    suspend fun increaseQuantity(
        userId: Int,
        productId: Int
    ): CartOperationResult {

        if (userId <= 0 || productId <= 0) {
            return CartOperationResult.Error(
                "Usuario o producto no válido."
            )
        }

        return try {

            database.withTransaction {

                val product = productDao.findById(productId)
                    ?: return@withTransaction CartOperationResult.Error(
                        "El producto ya no existe."
                    )

                if (!product.isActive) {
                    return@withTransaction CartOperationResult.Error(
                        "Este producto ya no está disponible."
                    )
                }

                if (product.stock <= 0) {
                    return@withTransaction CartOperationResult.Error(
                        "Producto agotado. No hay existencias disponibles."
                    )
                }

                val current = dao.findByProductId(
                    userId,
                    productId
                )

                val currentQuantity = current?.quantity ?: 0

                if (currentQuantity >= product.stock) {
                    return@withTransaction CartOperationResult.Error(
                        "Solo hay ${product.stock} unidades disponibles. Ya alcanzaste el límite."
                    )
                }

                val newQuantity = currentQuantity + 1

                if (current == null) {

                    dao.upsert(
                        CartItemEntity(
                            userId = userId,
                            productId = productId,
                            quantity = newQuantity
                        )
                    )

                } else {

                    dao.upsert(
                        current.copy(
                            quantity = newQuantity
                        )
                    )
                }

                CartOperationResult.Success(
                    "${product.name} agregado al carrito."
                )
            }

        } catch (e: Exception) {

            CartOperationResult.Error(
                e.message ?: "No fue posible agregar el producto."
            )
        }
    }

    // ==========================================
    // DISMINUIR CANTIDAD
    // ==========================================

    suspend fun decreaseQuantity(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        dao.decreaseQuantityAtomic(
            userId = userId,
            productId = productId
        )
    }

    // ==========================================
    // ELIMINAR PRODUCTO DEL CARRITO
    // ==========================================

    suspend fun removeProduct(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        dao.deleteByProductId(
            userId,
            productId
        )
    }

    // ==========================================
    // VACIAR CARRITO
    // ==========================================

    suspend fun clearCart(
        userId: Int
    ) {

        if (userId <= 0) return

        dao.clearForUser(userId)
    }
}
