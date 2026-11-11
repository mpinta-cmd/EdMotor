
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteProductDao {

    // ==========================================
    // CONSULTAR FAVORITOS
    // ==========================================

    @Query("""
        SELECT * FROM favorite_products
        WHERE userId = :userId
        ORDER BY productId ASC
    """)
    fun observeForUser(
        userId: Int
    ): Flow<List<FavoriteProductEntity>>

    @Query("""
        SELECT * FROM favorite_products
        WHERE userId = :userId
    """)
    suspend fun getFavoritesForUser(
        userId: Int
    ): List<FavoriteProductEntity>

    @Query("""
        SELECT * FROM favorite_products
        WHERE userId = :userId
        AND productId = :productId
        LIMIT 1
    """)
    suspend fun findByProductId(
        userId: Int,
        productId: Int
    ): FavoriteProductEntity?

    // ==========================================
    // INSERTAR FAVORITOS
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        favorite: FavoriteProductEntity
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(
        favorite: FavoriteProductEntity
    ): Long

    // ==========================================
    // ELIMINAR FAVORITOS
    // ==========================================

    @Query("""
        DELETE FROM favorite_products
        WHERE userId = :userId
        AND productId = :productId
    """)
    suspend fun deleteByProductId(
        userId: Int,
        productId: Int
    )

    @Query("""
        DELETE FROM favorite_products
        WHERE userId = :userId
    """)
    suspend fun clearForUser(userId: Int)

    // ==========================================
    // ACTIVAR / DESACTIVAR FAVORITO
    // ==========================================

    @Transaction
    suspend fun toggleFavoriteAtomic(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        val existing = findByProductId(
            userId,
            productId
        )

        if (existing != null) {

            deleteByProductId(
                userId,
                productId
            )

        } else {

            insertIfAbsent(
                FavoriteProductEntity(
                    userId = userId,
                    productId = productId
                )
            )
        }
    }

    // ==========================================
    // TRANSFERIR FAVORITOS ANTIGUOS
    // ==========================================

    @Transaction
    suspend fun assignLegacyFavorites(
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

        val legacyFavorites = getFavoritesForUser(
            legacyUserId
        )

        var transferred = 0

        for (favorite in legacyFavorites) {

            if (favorite.productId <= 0) continue

            val result = insertIfAbsent(
                FavoriteProductEntity(
                    userId = newUserId,
                    productId = favorite.productId
                )
            )

            if (result != -1L) {
                transferred++
            }
        }

        // Eliminar los favoritos antiguos
        // después de procesar su transferencia.

        clearForUser(legacyUserId)

        return transferred
    }
}
