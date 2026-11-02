
package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_products")
data class FavoriteProductEntity(

    @PrimaryKey
    val productId: Int

)
