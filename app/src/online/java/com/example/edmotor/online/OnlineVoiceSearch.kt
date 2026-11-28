package com.example.edmotor.online

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.edmotor.ui.components.EdMotorBottomSheet

private const val NO_VOICE_RESULT = "No se reconoció una búsqueda. Puedes volver a dictarla o escribirla."

@Stable
internal class OnlineVoiceSearchState(
    queryState: MutableState<String>,
    draftState: MutableState<String?>,
    errorState: MutableState<String?>,
    private val recognizer: ActivityResultLauncher<Intent>
) {
    var query by queryState
    var draft by draftState
    var error by errorState

    fun startRecognition() {
        error = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el repuesto que buscas")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        try {
            recognizer.launch(intent)
        } catch (_: ActivityNotFoundException) {
            error = "La búsqueda por voz no está disponible en este dispositivo. Puedes escribir el repuesto."
        } catch (_: SecurityException) {
            error = "No se pudo abrir el micrófono. Puedes escribir el repuesto."
        }
    }
}

@Composable
internal fun rememberOnlineVoiceSearch(): OnlineVoiceSearchState {
    val query = rememberSaveable { mutableStateOf("") }
    val draft = rememberSaveable { mutableStateOf<String?>(null) }
    val error = rememberSaveable { mutableStateOf<String?>(null) }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                draft.value = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull { it.isNotBlank() }?.trim()
                if (draft.value == null) error.value = NO_VOICE_RESULT
            }
            Activity.RESULT_CANCELED -> Unit
            RecognizerIntent.RESULT_NO_MATCH -> error.value = NO_VOICE_RESULT
            else -> error.value = "El reconocimiento de voz se interrumpió. Inténtalo de nuevo o escribe tu búsqueda."
        }
    }
    return remember(query, draft, error, recognizer) {
        OnlineVoiceSearchState(query, draft, error, recognizer)
    }
}

@Composable
internal fun OnlineVoiceSearchField(voiceSearch: OnlineVoiceSearchState) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = voiceSearch.query,
        onValueChange = { voiceSearch.query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Buscar repuestos") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            TextButton(
                onClick = {
                    focus.clearFocus()
                    voiceSearch.startRecognition()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Buscar por voz", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        singleLine = true
    )
}

@Composable
internal fun OnlineVoiceSearchDialogs(voiceSearch: OnlineVoiceSearchState) {
    voiceSearch.draft?.let { text ->
        val accept = {
            voiceSearch.query = text.trim()
            voiceSearch.draft = null
        }
        EdMotorBottomSheet(
            onDismissRequest = { voiceSearch.draft = null },
            icon = { Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Revisa tu búsqueda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Puedes corregir el texto antes de buscar.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { voiceSearch.draft = it },
                        label = { Text("Texto reconocido") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) accept() }),
                        singleLine = true
                    )
                }
            },
            confirmButton = { Button(enabled = text.isNotBlank(), onClick = accept) { Text("Buscar") } },
            dismissButton = { TextButton(onClick = { voiceSearch.draft = null }) { Text("Cancelar") } }
        )
    }

    voiceSearch.error?.let { message ->
        EdMotorBottomSheet(
            onDismissRequest = { voiceSearch.error = null },
            title = { Text("Búsqueda por voz") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { voiceSearch.error = null }) { Text("Entendido") } }
        )
    }
}
