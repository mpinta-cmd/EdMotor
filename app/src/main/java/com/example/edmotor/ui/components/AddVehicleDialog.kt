package com.example.edmotor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleBottomSheet(
    onDismiss: () -> Unit,
    onVehicleAdded: (
        String, String, Int, String, String, String, String, (Result<Long>) -> Unit
    ) -> Unit
) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val notesBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp
        ),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "🚘",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Agregar vehículo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Registra los datos de tu vehículo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Información del vehículo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Marca (ej. Chevrolet)") },
                enabled = !isSaving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modelo (ej. Aveo)") },
                enabled = !isSaving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = year,
                    onValueChange = {
                        year = it.filter { char -> char.isDigit() }
                            .take(4)
                    },
                    label = { Text("Año") },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = engine,
                    onValueChange = { engine = it },
                    label = { Text("Motor (opcional)") },
                    enabled = !isSaving,
                    singleLine = true,
                    modifier = Modifier.weight(1.35f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Placa") },
                    enabled = !isSaving,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it },
                    label = { Text("Kilometraje (opcional)") },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Text(
                text = "Detalles adicionales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas personales (opcional)") },
                enabled = !isSaving,
                placeholder = {
                    Text("Mantenimiento, recordatorios, etc.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(notesBringIntoViewRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            coroutineScope.launch {
                                delay(250)
                                notesBringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    val yearValue = year.toIntOrNull()
                    if (year.length != 4 || yearValue == null) {
                        errorMessage = "Ingresa el año del vehículo con cuatro dígitos."
                    } else if (!isSaving) {
                        errorMessage = null
                        isSaving = true
                        onVehicleAdded(
                            brand.trim(),
                            model.trim(),
                            yearValue,
                            engine.trim(),
                            plate.trim().uppercase(),
                            mileage.trim(),
                            notes.trim()
                        ) { result ->
                            isSaving = false
                            result.onSuccess { onDismiss() }
                                .onFailure { error ->
                                    errorMessage = error.message ?: "No se pudo registrar el vehículo."
                                }
                        }
                    }
                },
                enabled = !isSaving && brand.isNotBlank() && model.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (isSaving) "Guardando vehículo…" else "Guardar vehículo",
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Cancelar")
            }
        }
    }
}
