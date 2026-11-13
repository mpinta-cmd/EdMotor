package com.example.edmotor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun StockAdjustmentDialog(
    productName: String,
    currentStock: Int,
    increase: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantityText by rememberSaveable(productName, currentStock, increase) {
        mutableStateOf("1")
    }
    var showConfirmation by rememberSaveable(productName, currentStock, increase) {
        mutableStateOf(false)
    }

    val quantity = quantityText.toIntOrNull()
    val resultingStock = if (quantity != null) {
        currentStock.toLong() + if (increase) quantity.toLong() else -quantity.toLong()
    } else {
        currentStock.toLong()
    }
    val validQuantity = quantity != null &&
            quantity > 0 &&
            resultingStock >= 0L &&
            resultingStock <= Int.MAX_VALUE

    if (!showConfirmation) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(if (increase) "Aumentar existencias" else "Disminuir existencias")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(productName, fontWeight = FontWeight.Bold)
                    Text("Stock actual: $currentStock")
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter(Char::isDigit).take(9) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cantidad de unidades") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    if (quantity != null && quantity > 0 && !validQuantity) {
                        Text(
                            if (!increase) {
                                "No puedes disminuir más unidades de las disponibles."
                            } else {
                                "La cantidad supera el límite permitido."
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validQuantity,
                    onClick = { showConfirmation = true }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Confirmar movimiento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "¿Estás seguro de ${if (increase) "aumentar" else "disminuir"} $quantity ${if (quantity == 1) "unidad" else "unidades"} de $productName?"
                    )
                    Text("Stock actual: $currentStock")
                    Text(
                        "Stock resultante: $resultingStock",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (validQuantity && quantity != null) {
                            onConfirm(quantity)
                        }
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Rechazar")
                }
            }
        )
    }
}
