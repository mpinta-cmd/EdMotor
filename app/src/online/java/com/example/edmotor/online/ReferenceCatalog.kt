package com.example.edmotor.online

import androidx.annotation.DrawableRes
import com.example.edmotor.data.productList

private data class ReferenceCatalogEntry(val product: OnlineProduct, @DrawableRes val image: Int)

private val referenceCatalogEntries = productList.associate { original ->
    // Server product IDs are positive. Reference products are never sent to the API.
    val id = -original.id.toLong()
    id to ReferenceCatalogEntry(
        product = OnlineProduct(
            id = id,
            version = 0,
            name = original.name,
            description = original.description,
            category = original.category,
            priceCop = original.price.filter(Char::isDigit).toLong(),
            promotionPriceCop = null,
            stock = 0,
            imagePath = null
        ),
        image = original.imageRes
    )
}

internal val referenceCatalog = referenceCatalogEntries.values.map { it.product }

internal fun referenceCatalogProduct(productId: Long): OnlineProduct? =
    referenceCatalogEntries[productId]?.product

@DrawableRes
internal fun referenceCatalogImage(productId: Long): Int? = referenceCatalogEntries[productId]?.image
