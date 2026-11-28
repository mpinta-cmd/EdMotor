package com.example.edmotor.online

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineVoiceSearchTest {
    @get:Rule
    val compose = createComposeRule()

    private var catalogVisible by mutableStateOf(true)
    private var deliverResult: (() -> Unit)? = null
    private lateinit var searchState: OnlineVoiceSearchState

    @Test
    fun recognizedTextCanBeEditedBeforeItChangesTheSearch() {
        showSearch {
            ActivityResult(
                Activity.RESULT_OK,
                Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("", "filtro de aceite"))
            )
        }

        compose.onNodeWithText("Buscar por voz").assertIsDisplayed().performClick()
        compose.onNodeWithText("Revisa tu búsqueda").assertIsDisplayed()
        compose.onNodeWithText("filtro de aceite").assertIsDisplayed()
        compose.runOnIdle { assertEquals("", searchState.query) }

        compose.onNode(hasSetTextAction() and hasText("Texto reconocido")).performTextReplacement("  filtro de aire  ")
        compose.onNodeWithText("Buscar").performClick()
        compose.runOnIdle { assertEquals("filtro de aire", searchState.query) }
    }

    @Test
    fun cancelingRecognitionKeepsTheCurrentSearch() {
        showSearch { ActivityResult(Activity.RESULT_CANCELED, null) }
        compose.onNode(hasSetTextAction() and hasText("Buscar repuestos")).performTextReplacement("Bombillo")

        compose.onNodeWithText("Buscar por voz").performClick()

        compose.onNodeWithText("Revisa tu búsqueda").assertDoesNotExist()
        compose.onNodeWithText("Bombillo").assertIsDisplayed()
    }

    @Test
    fun missingRecognizerLeavesTypedSearchAvailable() {
        showSearch { throw ActivityNotFoundException() }

        compose.onNodeWithText("Buscar por voz").performClick()
        compose.onNodeWithText("La búsqueda por voz no está disponible en este dispositivo. Puedes escribir el repuesto.")
            .assertIsDisplayed()
        compose.onNodeWithText("Entendido").performClick()

        compose.onNode(hasSetTextAction() and hasText("Buscar repuestos")).performTextReplacement("bujía")
        compose.runOnIdle { assertEquals("bujía", searchState.query) }
    }

    @Test
    fun recognitionResultWaitsForInventoryUnlock() {
        showSearch(deferResult = true) {
            ActivityResult(
                Activity.RESULT_OK,
                Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("filtro de aceite"))
            )
        }
        compose.onNode(hasSetTextAction() and hasText("Buscar repuestos")).performTextReplacement("Bombillo")
        compose.onNodeWithText("Buscar por voz").performClick()
        compose.runOnIdle { catalogVisible = false }
        compose.onNodeWithText("Inventario bloqueado").assertIsDisplayed()
        compose.onNodeWithText("Buscar por voz").assertDoesNotExist()

        compose.runOnIdle { requireNotNull(deliverResult).invoke() }
        compose.onNodeWithText("Revisa tu búsqueda").assertDoesNotExist()
        compose.runOnIdle { catalogVisible = true }

        compose.onNodeWithText("Revisa tu búsqueda").assertIsDisplayed()
        compose.onNodeWithText("filtro de aceite").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Bombillo", searchState.query) }
        compose.onNodeWithText("Buscar").performClick()
        compose.runOnIdle { assertEquals("filtro de aceite", searchState.query) }
    }

    private fun showSearch(deferResult: Boolean = false, response: () -> ActivityResult) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    val intent = input as Intent
                    assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
                    assertEquals(
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL)
                    )
                    val dispatchRecognition: () -> Unit = {
                        val result = response()
                        dispatchResult(requestCode, result.resultCode, result.data)
                    }
                    if (deferResult) deliverResult = dispatchRecognition else dispatchRecognition()
                }
            }
        }

        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    val voiceSearch = rememberOnlineVoiceSearch()
                    SideEffect { searchState = voiceSearch }
                    if (catalogVisible) {
                        OnlineVoiceSearchField(voiceSearch)
                        OnlineVoiceSearchDialogs(voiceSearch)
                    } else {
                        Text("Inventario bloqueado")
                    }
                }
            }
        }
    }
}
