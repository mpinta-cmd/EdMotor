package com.example.edmotor.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductCatalogOrderTest {
    @Test
    fun activePromotionsGoFirstAndReturnToAlphabeticalOrderWhenRemoved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EdMotorDatabase::class.java).build()
        try {
            val products = database.productDao()
            val alpha = repuesto("amortiguador")
            val invalidPromotion = repuesto("Batería").copy(isOnPromotion = true)
            val firstPromotion = repuesto("Tornillo").copy(isOnPromotion = true, promotionPriceCop = 10)
            val lastPromotion = repuesto("Zapata").copy(isOnPromotion = true, promotionPriceCop = 50)
            products.insert(lastPromotion)
            products.insert(invalidPromotion)
            products.insert(firstPromotion)
            val id = products.insert(alpha).toInt()

            val initial = listOf("Tornillo", "Zapata", "amortiguador", "Batería")
            val catalog = products.observeActiveProducts()
            assertEquals(initial, catalog.first().map { it.name })
            assertEquals(initial, products.observeAllProducts().first().map { it.name })
            assertEquals(initial, products.searchProducts("Frenos").first().map { it.name })
            assertEquals(initial, products.observeByCategory("Frenos").first().map { it.name })

            products.update(alpha.copy(id = id, isOnPromotion = true, promotionPriceCop = 30))
            assertEquals(listOf("amortiguador", "Tornillo", "Zapata", "Batería"), catalog.first().map { it.name })

            products.update(alpha.copy(id = id))
            assertEquals(initial, catalog.first().map { it.name })
        } finally {
            database.close()
        }
    }

    private fun repuesto(name: String) = ProductEntity(
        name = name,
        description = "Repuesto de prueba",
        category = "Frenos",
        priceCop = 100,
        imageRes = 0,
        contentDescription = name
    )
}
