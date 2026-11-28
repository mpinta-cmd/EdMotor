package com.example.edmotor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.edmotor.model.Category

@Composable
fun CategorySelector(
    selectedCategory: String,
    categories: List<Category>,
    onCategorySelected: (String) -> Unit,
    onCreateCategory: (String, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Categoría"
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var createError by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreating by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selectedCategory.isBlank()) {
                    "Seleccionar $label"
                } else {
                    "$label: $selectedCategory"
                },
                modifier = Modifier.weight(1f)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.emoji}  ${category.nombre}") },
                    onClick = {
                        onCategorySelected(category.nombre)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        "+ Crear nueva categoría",
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    expanded = false
                    newCategoryName = ""
                    createError = null
                    showCreateDialog = true
                }
            )
        }
    }

    if (showCreateDialog) {
        EdMotorBottomSheet(
            dismissEnabled = !isCreating,
            onDismissRequest = {
                if (!isCreating) showCreateDialog = false
            },
            title = { Text("Crear categoría") },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = {
                            newCategoryName = it
                            createError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre de la categoría") },
                        singleLine = true
                    )
                    if (createError != null) {
                        Text(
                            createError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isCreating && newCategoryName.trim().length >= 2,
                    onClick = {
                        isCreating = true
                        onCreateCategory(newCategoryName.trim()) { success, message ->
                            isCreating = false
                            if (success) {
                                onCategorySelected(newCategoryName.trim())
                                showCreateDialog = false
                                newCategoryName = ""
                            } else {
                                createError = message ?: "No fue posible crear la categoría."
                            }
                        }
                    }
                ) {
                    Text(if (isCreating) "Guardando..." else "Crear")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isCreating,
                    onClick = { showCreateDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
