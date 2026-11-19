package com.example.edmotor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.R
import com.example.edmotor.data.repository.ProductOperationResult
import com.example.edmotor.model.Category
import com.example.edmotor.ui.screens.ProductFormScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalogPhotoOpensFromSecondActionAndIsUsedWhenRegisteringTheProduct() {
        val image = R.drawable.filtro_a
        var pickedImage: Pair<Int, String?>? = null
        var createdId: Int? = null
        compose.setContent {
            MaterialTheme {
                ProductFormScreen(
                    availableImages = listOf("Filtro de la biblioteca" to image),
                    categories = listOf(Category(emoji = "🔧", nombre = "Filtros")),
                    onCreateCategory = { _, _ -> },
                    onSave = { _, _, _, _, imageRes, _, _, _, _, _, imageUri, onResult ->
                        pickedImage = imageRes to imageUri
                        onResult(ProductOperationResult.Success(17, "Repuesto creado con éxito."))
                    },
                    onUpdate = { _, _ -> },
                    onCreated = { createdId = it },
                    onBack = {}
                )
            }
        }

        fillField("Nombre del repuesto", "Filtro nuevo")
        fillField("Descripción", "Filtro de prueba")
        compose.onNodeWithText("Seleccionar Categoría").performScrollTo().performClick()
        compose.onNodeWithText("Filtros", substring = true).performClick()
        fillField("Precio normal (COP)", "36000")

        val device = compose.onNodeWithText("Elegir foto de mi dispositivo")
        val library = compose.onNodeWithText("Elegir imágenes previamente cargadas")
        val submit = compose.onNodeWithText("Registrar Repuesto").performScrollTo()
        assertTrue(device.getUnclippedBoundsInRoot().bottom <= library.getUnclippedBoundsInRoot().top)
        assertTrue(library.getUnclippedBoundsInRoot().bottom <= submit.getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("Filtro de la biblioteca").assertDoesNotExist()
        library.performScrollTo().performClick()
        compose.onNodeWithText("Seleccionar imagen").assertIsDisplayed()
        compose.onNodeWithText("Filtro de la biblioteca").performClick()
        compose.onNodeWithText("Seleccionar imagen").assertDoesNotExist()

        submit.performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(image to null, pickedImage)
            assertEquals(17, createdId)
        }
    }

    private fun fillField(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }
}
