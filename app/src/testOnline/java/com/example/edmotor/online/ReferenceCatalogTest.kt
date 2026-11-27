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
            assertEquals(0, preview.stock)
            assertNull(preview.averageRating)
        }

        assertEquals(109_900L, referenceCatalogProduct(-1)?.priceCop)
        assertNull(referenceCatalogImage(1))
        assertNull(referenceCatalogProduct(1))
        assertTrue(OnlineState(products = referenceCatalog).hasReferenceCatalog)
        assertFalse(OnlineState().hasReferenceCatalog)
    }
}
