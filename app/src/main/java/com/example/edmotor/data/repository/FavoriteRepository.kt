
package com.example.edmotor.data.repository

import com.example.edmotor.data.local.FavoriteProductDao
import com.example.edmotor.data.local.FavoriteProductEntity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteRepository(
    private val dao: FavoriteProductDao
) {

    // ==========================================
    // READ: OBSERVAR PRODUCTOS FAVORITOS
    // ==========================================

    val favoriteProductIds: Flow<Set<Int>> =
        dao.observeAll().map { favorites ->

            favorites.map { favorite ->
                favorite.productId
            }.toSet()
        }

    // ==========================================
    // CREATE: AGREGAR FAVORITO
    // ==========================================

    suspend fun addFavorite(productId: Int) {

        if (productId <= 0) return

        dao.insert(
            FavoriteProductEntity(
                productId = productId
            )
        )
    }

    // ==========================================
    // DELETE: ELIMINAR FAVORITO
    // ==========================================

    suspend fun removeFavorite(productId: Int) {

        dao.deleteByProductId(productId)
    }

    // ==========================================
    // TOGGLE: CAMBIAR ESTADO DEL FAVORITO
    // ==========================================

    suspend fun toggleFavorite(productId: Int) {

        if (productId <= 0) return

        val existingFavorite = dao.findByProductId(productId)

        if (existingFavorite != null) {

            removeFavorite(productId)

        } else {

            addFavorite(productId)
        }
    }
}
