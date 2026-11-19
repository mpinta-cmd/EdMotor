package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.CartItemEntity
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.model.UserRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface CartOperationResult {
    data class Success(val message: String) : CartOperationResult
    data class Error(val message: String) : CartOperationResult
}

class CartRepository(
    private val database: EdMotorDatabase,
    private val dao: CartDao,
    private val productDao: ProductDao
) {
    fun observeCart(userId: Int): Flow<Map<Int, Int>> =
        dao.observeForUser(userId).map { items -> items.associate { it.productId to it.quantity } }

    suspend fun increaseQuantity(userId: Int, productId: Int): CartOperationResult =
        changeCart(userId, "No fue posible agregar el producto.", productId) {
            val product = productDao.findById(productId)
                ?: return@changeCart CartOperationResult.Error("El producto ya no existe.")
            if (!product.isActive) {
                return@changeCart CartOperationResult.Error("Este producto ya no está disponible.")
            }
            if (product.stock <= 0) {
                return@changeCart CartOperationResult.Error("Producto agotado. No hay existencias disponibles.")
            }

            val currentQuantity = dao.findByProductId(userId, productId)?.quantity?.coerceAtLeast(0) ?: 0
            if (currentQuantity >= product.stock) {
                return@changeCart CartOperationResult.Error(
                    "Solo hay ${product.stock} unidades disponibles. Ya alcanzaste el límite."
                )
            }

            dao.upsert(CartItemEntity(userId = userId, productId = productId, quantity = currentQuantity + 1))
            CartOperationResult.Success("${product.name} agregado al carrito.")
        }

    suspend fun decreaseQuantity(userId: Int, productId: Int): CartOperationResult =
        changeCart(userId, "No fue posible disminuir la cantidad.", productId) {
            dao.decreaseQuantityAtomic(userId, productId)
            CartOperationResult.Success("Cantidad actualizada.")
        }

    suspend fun removeProduct(userId: Int, productId: Int): CartOperationResult =
        changeCart(userId, "No fue posible quitar el repuesto.") {
            dao.deleteByProductId(userId, productId)
            CartOperationResult.Success("Repuesto retirado del carrito.")
        }

    suspend fun clearCart(userId: Int): CartOperationResult =
        changeCart(userId, "No fue posible vaciar el carrito.") {
            dao.clearForUser(userId)
            CartOperationResult.Success("Carrito vacío.")
        }

    private suspend fun changeCart(
        userId: Int,
        fallbackMessage: String,
        productId: Int? = null,
        operation: suspend () -> CartOperationResult
    ): CartOperationResult {
        if (userId <= 0 || (productId != null && productId <= 0)) {
            return CartOperationResult.Error("Usuario o producto no válido.")
        }

        return try {
            database.withTransaction {
                val user = database.userDao().findById(userId)
                if (user?.role != UserRole.CLIENT.name) {
                    return@withTransaction CartOperationResult.Error("Solo los clientes pueden modificar su carrito.")
                }
                operation()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CartOperationResult.Error(fallbackMessage)
        }
    }
}
