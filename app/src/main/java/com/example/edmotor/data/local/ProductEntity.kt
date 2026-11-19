package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["name"]),
        Index(value = ["category"]),
        Index(value = ["isActive"]),
        Index(value = ["stock"])
    ]
)
data class ProductEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // INFORMACIÓN DEL PRODUCTO
    val name: String,
    val description: String,
    val category: String,

    // PRECIOS EN PESOS COLOMBIANOS
    val priceCop: Long,
    val promotionPriceCop: Long? = null,

    // IMAGEN LOCAL DEL PRODUCTO
    val imageRes: Int,
    val contentDescription: String,
    val imageUri: String? = null,

    // VALORACIÓN
    val rating: Double = 0.0,

    // INVENTARIO
    val stock: Int = 10,

    // ETIQUETAS COMERCIALES
    val isNew: Boolean = false,
    val isOnPromotion: Boolean = false,

    // ESTADO DEL PRODUCTO
    val isActive: Boolean = true,

    // CONTROL DE REGISTRO
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {

    val isOutOfStock: Boolean
        get() = stock <= 0

    val effectivePriceCop: Long
        get() = if (
            isOnPromotion &&
            promotionPriceCop != null &&
            promotionPriceCop in 1 until priceCop
        ) {
            promotionPriceCop
        } else {
            priceCop
        }

    val discountPercentage: Int
        get() {
            val effectivePrice = effectivePriceCop
            if (priceCop <= 0 || effectivePrice >= priceCop) return 0
            return ((priceCop - effectivePrice).toBigInteger() * 100.toBigInteger() / priceCop.toBigInteger()).toInt()
        }

    val availabilityLabel: String
        get() = when {
            !isActive -> "NO DISPONIBLE"
            stock <= 0 -> "AGOTADO"
            stock <= 3 -> "ÚLTIMAS UNIDADES"
            else -> "DISPONIBLE"
        }
}
