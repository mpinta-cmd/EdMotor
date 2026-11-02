
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {

    // READ: Observar todos los productos del carrito
    @Query("SELECT * FROM cart_items")
    fun observeAll(): Flow<List<CartItemEntity>>

    // READ: Buscar un producto por su ID
    @Query(
        "SELECT * FROM cart_items " +
                "WHERE productId = :productId LIMIT 1"
    )
    suspend fun findByProductId(
        productId: Int
    ): CartItemEntity?

    // CREATE / UPDATE: Guardar cantidad
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CartItemEntity)

    // DELETE: Eliminar un producto
    @Query(
        "DELETE FROM cart_items " +
                "WHERE productId = :productId"
    )
    suspend fun deleteByProductId(
        productId: Int
    )

    // DELETE: Vaciar carrito
    @Query("DELETE FROM cart_items")
    suspend fun clearAll()
}
