package com.example.edmotor.ui.theme.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Vehicle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onDelete: () -> Unit,
    onUpdateNotes: (String) -> Unit,
    onUpdateMileage: (String) -> Unit
) {
    var notesExpanded by remember(vehicle.id) { mutableStateOf(false) }
    var isEditingNotes by remember(vehicle.id) { mutableStateOf(false) }
    var editedNotesText by remember(vehicle.notes) {
        mutableStateOf(vehicle.notes)
    }
    var showMileageDialog by remember(vehicle.id) { mutableStateOf(false) }
    var showDeleteDialog by remember(vehicle.id) { mutableStateOf(false) }
    var tempMileageText by remember(vehicle.mileage) {
        mutableStateOf(
            vehicle.mileage
                .replace("km", "", ignoreCase = true)
                .trim()
                .filter { it.isDigit() || it == '.' || it == ',' }
        )
    }

    val notesFocusRequester = remember(vehicle.id) { FocusRequester() }
    val notesBringIntoViewRequester = remember(vehicle.id) {
        BringIntoViewRequester()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Al activar la edición, enfoca el campo y desplaza la lista
    // hasta dejarlo visible por encima del teclado.
    LaunchedEffect(isEditingNotes) {
        if (isEditingNotes) {
            delay(180)
            notesFocusRequester.requestFocus()
            delay(280)
            notesBringIntoViewRequester.bringIntoView()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            // Identidad del vehículo.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "ED",
                        modifier = Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 15.dp
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${vehicle.brand} ${vehicle.model}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${vehicle.year}  ·  Motor ${vehicle.engine}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { showDeleteDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = "Eliminar",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            // Datos esenciales en dos bloques compactos.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VehicleDataTile(
                    label = "PLACA",
                    value = vehicle.plate.ifBlank { "No registrada" },
                    modifier = Modifier.weight(1f)
                )

                VehicleDataTile(
                    label = "KILOMETRAJE  ·  EDITAR",
                    value = vehicle.mileage.ifBlank { "Sin registrar" },
                    modifier = Modifier.weight(1f),
                    highlighted = true,
                    onClick = { showMileageDialog = true }
                )
            }

            // Sección plegable: mantiene la tarjeta despejada
            // mientras el usuario no necesite consultar o editar notas.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isEditingNotes) {
                                notesExpanded = !notesExpanded
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mantenimiento y notas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isEditingNotes -> "Editando tus notas"
                                vehicle.notes.isNotBlank() -> "Tienes una nota guardada"
                                else -> "Añade recordatorios cuando lo necesites"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = {
                            if (notesExpanded) {
                                notesExpanded = false
                            } else {
                                notesExpanded = true
                            }
                        },
                        enabled = !isEditingNotes
                    ) {
                        Text(if (notesExpanded) "Ocultar" else "Ver notas")
                    }
                }

                if (!notesExpanded && vehicle.notes.isNotBlank()) {
                    Text(
                        text = vehicle.notes,
                        modifier = Modifier.padding(start = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(
                    visible = notesExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isEditingNotes) {
                            OutlinedTextField(
                                value = editedNotesText,
                                onValueChange = { editedNotesText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(notesFocusRequester)
                                    .bringIntoViewRequester(notesBringIntoViewRequester)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            coroutineScope.launch {
                                                delay(220)
                                                notesBringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    },
                                label = { Text("Notas de mantenimiento") },
                                placeholder = {
                                    Text("Ej. Próximo cambio de aceite, revisión de frenos...")
                                },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        editedNotesText = vehicle.notes
                                        isEditingNotes = false
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ) {
                                    Text("Cancelar")
                                }

                                Button(
                                    onClick = {
                                        onUpdateNotes(editedNotesText.trim())
                                        isEditingNotes = false
                                        notesExpanded = false
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Guardar")
                                }
                            }
                        } else {
                            Text(
                                text = vehicle.notes.ifBlank {
                                    "Todavía no has añadido notas. Puedes guardar aquí recordatorios de mantenimiento, reparaciones o próximas revisiones."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    editedNotesText = vehicle.notes
                                    notesExpanded = true
                                    isEditingNotes = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (vehicle.notes.isBlank()) {
                                        "Añadir nota"
                                    } else {
                                        "Editar nota"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo para actualizar kilometraje.
    if (showMileageDialog) {
        AlertDialog(
            onDismissRequest = { showMileageDialog = false },
            title = {
                Text("Actualizar kilometraje", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ingresa el kilometraje actual de ${vehicle.brand} ${vehicle.model}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = tempMileageText,
                        onValueChange = { value ->
                            tempMileageText = value.filter {
                                it.isDigit() || it == '.' || it == ','
                            }
                        },
                        label = { Text("Kilometraje en km") },
                        placeholder = { Text("Ej. 130000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() }
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateMileage(tempMileageText.trim())
                        showMileageDialog = false
                    },
                    enabled = tempMileageText.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMileageDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    // Confirmación antes de eliminar.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("Eliminar vehículo", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "¿Seguro que deseas eliminar ${vehicle.brand} ${vehicle.model}? Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Conservar vehículo")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
private fun VehicleDataTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val tileModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = tileModifier,
        shape = RoundedCornerShape(15.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
