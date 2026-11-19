package com.example.edmotor.data

import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.model.categoryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProductMapperTest {
    private val product = ProductEntity(
        name = "Repuesto",
        description = "Descripción",
        category = "Refrigeración",
        priceCop = Long.MAX_VALUE,
        imageRes = 1,
        contentDescription = "Repuesto",
        isOnPromotion = true
    )

    @Test
    fun largeDiscountUsesTheSameValidPromotionAsCheckout() {
        val catalogItem = product.copy(promotionPriceCop = Long.MAX_VALUE / 2).toProduct()
        assertEquals(50, catalogItem.discountPercentage)
        assertEquals(catalogItem.promotionPrice, catalogItem.effectivePrice)
    }

    @Test
    fun invalidLegacyPromotionUsesTheRegularPriceInTheCatalog() {
        val catalogItem = product.copy(promotionPriceCop = 0L).toProduct()
        assertFalse(catalogItem.isOnPromotion)
        assertNull(catalogItem.promotionPrice)
        assertEquals(catalogItem.price, catalogItem.effectivePrice)
        assertEquals(0, catalogItem.discountPercentage)
    }

    @Test
    fun categoriesCompareEquivalentAccentsAndCase() {
        assertEquals(categoryKey("Refrigeración"), categoryKey("  REFRIGERACIO\u0301N  "))
    }
}
