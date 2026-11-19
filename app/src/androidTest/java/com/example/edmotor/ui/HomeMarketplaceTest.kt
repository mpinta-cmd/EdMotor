package com.example.edmotor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.R
import com.example.edmotor.model.Category
import com.example.edmotor.model.Product
import com.example.edmotor.ui.screens.HomeScreen
import com.example.edmotor.ui.theme.EdMotorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeMarketplaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clientCanOpenAppointmentsSearchAndUseTheFilteredCatalog() {
        val brakePads = Product(1, "Pastillas de freno", "Compuesto cerámico", "$85000", R.drawable.filtro_a,
            rating = 4.5, category = "Frenos")
        val oilFilter = Product(2, "Filtro de aceite", "Motor de gasolina", "$24500", R.drawable.filtro_a,
            rating = 4.0, category = "Filtros")
        var query by mutableStateOf("")
        var category by mutableStateOf<String?>(null)
        var favoriteIds by mutableStateOf(emptySet<Int>())
        var quantities by mutableStateOf(emptyMap<Int, Int>())
        var appointmentOpens = 0
        val openedProducts = mutableListOf<Int>()

        compose.setContent {
            EdMotorTheme {
                HomeScreen(
                    searchQuery = query,
                    onSearchChange = { query = it },
                    categories = listOf(Category(emoji = "", nombre = "Frenos"), Category(emoji = "", nombre = "Filtros")),
                    product = listOf(brakePads, oilFilter),
                    cartQuantity = quantities,
                    selectedCategory = category,
                    favoriteProductIds = favoriteIds,
                    onFavoriteClick = { id ->
                        favoriteIds = if (id in favoriteIds) favoriteIds - id else favoriteIds + id
                    },
                    onProductClick = { openedProducts.add(it) },
                    onCategorySelect = { category = if (category == it) null else it },
                    onIncrease = { id -> quantities = quantities + (id to ((quantities[id] ?: 0) + 1)) },
                    onDecrease = {},
                    onOpenAppointments = { appointmentOpens++ }
                )
            }
        }

        compose.onNodeWithText("Repuestos").assertIsDisplayed()
        compose.onNodeWithText("Agendar cita").assertIsDisplayed().performClick()
        compose.onNodeWithText("2 repuestos").assertIsDisplayed()
        compose.onNodeWithText("Buscar repuestos o marcas").assertIsDisplayed()
        val search = compose.onNode(hasSetTextAction())
        search.performTextInput("cerámico")
        search.performImeAction()

        compose.onNodeWithText("Resultados").assertIsDisplayed()
        compose.onNodeWithText("1 repuesto").assertIsDisplayed()
        compose.onNodeWithText(oilFilter.name).assertDoesNotExist()
        compose.onNodeWithText(brakePads.name).performScrollTo().performClick()
        compose.onNodeWithText("Agregar").performScrollTo().performClick()
        compose.onNodeWithText("En carrito").assertIsDisplayed()
        compose.onNodeWithContentDescription("Agregar a favoritos").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Quitar de favoritos").assertIsDisplayed()

        compose.onNodeWithContentDescription("Limpiar búsqueda").performClick()
        compose.onNode(hasText("Favoritos") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("1 repuesto").assertIsDisplayed()
        compose.onNodeWithText(brakePads.name).assertIsDisplayed()
        compose.onNodeWithText(oilFilter.name).assertDoesNotExist()

        compose.onNode(hasText("Filtros") and hasClickAction()).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("1 repuesto").assertIsDisplayed()
        compose.onNodeWithText(oilFilter.name).assertIsDisplayed()
        compose.onNodeWithText(brakePads.name).assertDoesNotExist()

        compose.runOnIdle {
            assertEquals(1, appointmentOpens)
            assertEquals(listOf(brakePads.id), openedProducts)
            assertEquals(mapOf(brakePads.id to 1), quantities)
            assertEquals(setOf(brakePads.id), favoriteIds)
        }
    }
}
