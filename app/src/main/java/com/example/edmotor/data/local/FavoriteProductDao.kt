
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteProductDao {

    // READ: Observar todos los favoritos
    @Query("SELECT * FROM favorite_products")
    fun observeAll(): Flow<List<FavoriteProductEntity>>

    // READ: Consultar si un producto es favorito
    @Query(
        "SELECT * FROM favorite_products " +
                "WHERE productId = :productId LIMIT 1"
    )
    suspend fun findByProductId(
        productId: Int
    ): FavoriteProductEntity?

    // CREATE: Agregar favorito
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        favorite: FavoriteProductEntity
    )

    // DELETE: Quitar favorito
    @Query(
        "DELETE FROM favorite_products " +
                "WHERE productId = :productId"
    )
    suspend fun deleteByProductId(
        productId: Int
    )
}
