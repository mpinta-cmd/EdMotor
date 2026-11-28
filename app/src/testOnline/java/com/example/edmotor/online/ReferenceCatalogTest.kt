package com.example.edmotor.online

import com.example.edmotor.data.productList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceCatalogTest {
    @Test
    fun includesTheFortyOriginalPartsAndTheirPictures() {
        assertEquals(40, referenceCatalog.size)
        assertEquals(40, referenceCatalog.map { referenceCatalogImage(it.id) }.toSet().size)

        productList.forEach { original ->
            val id = -original.id.toLong()
            val preview = requireNotNull(referenceCatalogProduct(id))
            assertEquals(original.name, preview.name)
            assertEquals(original.category, preview.category)
            assertEquals(original.description, preview.description)
            assertEquals(original.imageRes, referenceCatalogImage(id))
            assertTrue(preview.priceCop > 0)
            assertNull(preview.averageRating)
        }

        assertEquals(109_900L, referenceCatalogProduct(-1)?.priceCop)
        assertNull(referenceCatalogImage(1))
        assertNull(referenceCatalogProduct(1))
        assertTrue(OnlineState(products = referenceCatalog).hasReferenceCatalog)
        assertFalse(OnlineState().hasReferenceCatalog)
    }

    @Test
    fun showsOriginalStockWithExplicitQuantities() {
        val inStock = requireNotNull(referenceCatalogProduct(-2))
        val soldOut = requireNotNull(referenceCatalogProduct(-3))
        assertEquals("Filtro de aceite A-111", soldOut.name)
        assertEquals(39, referenceCatalog.count { it.stock == 10 })
        assertEquals(1, referenceCatalog.count { it.stock == 0 })
        assertEquals("10 unidades", stockQuantityText(inStock.stock))
        assertEquals("0 unidades · Agotado", stockQuantityText(soldOut.stock))
        assertEquals("1 unidad", stockQuantityText(1))
    }
}
