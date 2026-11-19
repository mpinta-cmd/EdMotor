
package com.example.edmotor.model

import androidx.annotation.DrawableRes

data class Product(
    val id: Int,
    val name: String,
    val description: String,
    val price: String,
    @DrawableRes val imageRes: Int,
    val contentDescription: String = name,
    val rating: Double,
    val badge: String = "",
    val category: String = "",

    // ==========================================
    // NUEVOS CAMPOS DE INVENTARIO
    // ==========================================

    val stock: Int = 10,
    val isNew: Boolean = false,
    val isOnPromotion: Boolean = false,
    val promotionPrice: String? = null,
    val imageUri: String? = null,
    val discountPercentage: Int = 0
) {

    val isOutOfStock: Boolean
        get() = stock <= 0

    val effectivePrice: String
        get() = if (
            isOnPromotion &&
            !promotionPrice.isNullOrBlank()
        ) {
            promotionPrice
        } else {
            price
        }

    val availabilityLabel: String
        get() = when {
            stock <= 0 -> "Agotado"
            stock <= 3 -> "Últimas unidades"
            else -> "Disponible"
        }
}
