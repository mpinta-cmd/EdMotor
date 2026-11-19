package com.example.edmotor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.ui.screens.CartScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun invalidSavedQuantityCanBeRemoved() {
        val productId = 12
        var quantities by mutableStateOf(mapOf(productId to 0))

        compose.setContent {
            MaterialTheme {
                CartScreen(
                    products = emptyList(),
                    cartQuantity = quantities,
                    onIncrease = {},
                    onDecrease = {},
                    onRemove = { quantities = quantities - it },
                    onSubmitOrder = {},
                    isSubmittingOrder = false,
                    orderMessage = null,
                    isOrderError = false,
                    onClearOrderMessage = {}
                )
            }
        }

        compose.onNodeWithText("Guardar pedido").assertIsNotEnabled()
        compose.onNodeWithText("Quitar del carrito").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(quantities.isEmpty()) }
    }
}
