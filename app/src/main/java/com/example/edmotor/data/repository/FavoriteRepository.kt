
package com.example.edmotor.data.repository

import com.example.edmotor.data.local.FavoriteProductDao
import com.example.edmotor.data.local.FavoriteProductEntity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteRepository(
    private val dao: FavoriteProductDao
) {

    fun observeFavorites(
        userId: Int
    ): Flow<Set<Int>> {

        return dao.observeForUser(userId).map { favorites ->

            favorites.map {
                it.productId
            }.toSet()
        }
    }


    suspend fun addFavorite(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        dao.insert(
            FavoriteProductEntity(
                userId = userId,
                productId = productId
            )
        )
    }


    suspend fun removeFavorite(
        userId: Int,
        productId: Int
    ) {

        if (userId <= 0 || productId <= 0) return

        dao.deleteByProductId(userId, productId)
    }


    suspend fun toggleFavorite(
        userId: Int,
        productId: Int
    ) {
        dao.toggleFavoriteAtomic(userId, productId)
    }
}
