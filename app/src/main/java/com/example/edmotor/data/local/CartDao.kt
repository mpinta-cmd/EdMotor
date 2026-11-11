
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {

    // ==========================================
    // CONSULTAS DEL CARRITO
    // ==========================================

    @Query("""
        SELECT * FROM cart_items
        WHERE userId = :userId
        ORDER BY productId ASC
    """)
    fun observeForUser(userId: Int): Flow<List<CartItemEntity>>

    @Query("""
        SELECT * FROM cart_items
        WHERE userId = :userId
        AND productId = :productId
        LIMIT 1
    """)
    suspend fun findByProductId(
        userId: Int,
        productId: Int
    ): CartItemEntity?

    @Query("""
        SELECT * FROM cart_items
        WHERE userId = :userId
    """)
    suspend fun getItemsForUser(
        userId: Int
    ): List<CartItemEntity>

    // ==========================================
    // INSERCIONES Y ACTUALIZACIONES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CartItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: CartItemEntity): Long

    // ==========================================
    // INCREMENTO ATÓMICO
    // ==========================================

    @Query("""
        UPDATE cart_items
        SET quantity = quantity + 1
        WHERE userId = :userId
        AND productId = :productId
        AND quantity < 2147483647
    """)
    suspend fun incrementExisting(
        userId: Int,
        productId: Int
    ): Int

    @Transaction
    suspend fun increaseQuantityAtomic(
        userId: Int,
        productId: Int
    ) {
        if (userId <= 0 || productId <= 0) return

        val updated = incrementExisting(userId, productId)

        if (updated == 0) {
            insertIfAbsent(
                CartItemEntity(
                    userId = userId,
                    productId = productId,
                    quantity = 1
                )
            )
        }
    }

    // ==========================================
    // DISMINUCIÓN ATÓMICA
    // ==========================================

    @Query("""
        UPDATE cart_items
        SET quantity = quantity - 1
        WHERE userId = :userId
        AND productId = :productId
        AND quantity > 1
    """)
    suspend fun decrementIfAboveOne(
        userId: Int,
        productId: Int
    ): Int

    @Query("""
        DELETE FROM cart_items
        WHERE userId = :userId
        AND productId = :productId
        AND quantity <= 1
    """)
    suspend fun deleteIfOneOrLess(
        userId: Int,
        productId: Int
    )

    @Transaction
    suspend fun decreaseQuantityAtomic(
        userId: Int,
        productId: Int
    ) {
        if (userId <= 0 || productId <= 0) return

        val updated = decrementIfAboveOne(userId, productId)

        if (updated == 0) {
            deleteIfOneOrLess(userId, productId)
        }
    }

    // ==========================================
    // ELIMINACIÓN DE PRODUCTOS
    // ==========================================

    @Query("""
        DELETE FROM cart_items
        WHERE userId = :userId
        AND productId = :productId
    """)
    suspend fun deleteByProductId(
        userId: Int,
        productId: Int
    )

    @Query("""
        DELETE FROM cart_items
        WHERE userId = :userId
    """)
    suspend fun clearForUser(userId: Int)

    // ==========================================
    // TRANSFERENCIA DE CARRITO ANTIGUO
    // ==========================================

    @Transaction
    suspend fun assignLegacyItems(
        legacyUserId: Int,
        newUserId: Int
    ): Int {

        if (
            legacyUserId < 0 ||
            newUserId <= 0 ||
            legacyUserId == newUserId
        ) {
            return 0
        }

        val legacyItems = getItemsForUser(legacyUserId)
        var transferred = 0

        for (legacyItem in legacyItems) {

            if (
                legacyItem.productId <= 0 ||
                legacyItem.quantity <= 0
            ) {
                continue
            }

            val current = findByProductId(
                newUserId,
                legacyItem.productId
            )

            if (current == null) {

                insertIfAbsent(
                    CartItemEntity(
                        userId = newUserId,
                        productId = legacyItem.productId,
                        quantity = legacyItem.quantity
                    )
                )

            } else {

                val combinedQuantity = (
                        current.quantity.toLong() +
                                legacyItem.quantity.toLong()
                        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

                upsert(
                    current.copy(
                        quantity = combinedQuantity.coerceAtLeast(1)
                    )
                )
            }

            transferred++
        }

        // Se eliminan los registros antiguos únicamente
        // después de procesar la transferencia.
        clearForUser(legacyUserId)

        return transferred
    }
}
