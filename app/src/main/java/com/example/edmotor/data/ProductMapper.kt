
package com.example.edmotor.data

import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.model.Product
import java.text.NumberFormat
import java.util.Locale

private fun formatProductPrice(value: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale("es", "CO"))
    return "$${formatter.format(value)}"
}

fun ProductEntity.toProduct(): Product {

    val badge = when {
        stock <= 0 -> "Agotado"
        isOnPromotion -> "Oferta"
        isNew -> "Nuevo"
        else -> ""
    }

    return Product(
        id = id,
        name = name,
        description = description,
        price = formatProductPrice(priceCop),
        imageRes = imageRes,
        imageUri = imageUri,
        contentDescription = contentDescription,
        rating = rating,
        badge = badge,
        category = category,
        stock = stock,
        isNew = isNew,
        isOnPromotion = isOnPromotion,
        promotionPrice = promotionPriceCop?.let {
            formatProductPrice(it)
        }
    )
}
