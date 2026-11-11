package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.model.MaintenanceDraft
import com.example.edmotor.model.MaintenancePartDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PartFormState(
    val name: String = "",
    val brand: String = "",
    val reference: String = "",
    val specification: String = "",
    val quantity: String = "1",
    val unit: String = "unidad",
    val unitPrice: String = "0"
)

@Composable
fun MaintenanceFormScreen(
    vehicleTitle: String,
    existingRecord: MaintenanceWithParts?,
    onSave: (MaintenanceDraft) -> Unit,
    onBack: () -> Unit
) {
    var serviceType by remember(existingRecord?.record?.id) { mutableStateOf(existingRecord?.record?.serviceType ?: "Cambio de aceite") }
    var serviceDate by remember(existingRecord?.record?.id) {
        mutableStateOf(existingRecord?.record?.serviceDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var mileage by remember(existingRecord?.record?.id) { mutableStateOf(existingRecord?.record?.mileageKm?.toString() ?: "") }
    var description by remember(existingRecord?.record?.id) { mutableStateOf(existingRecord?.record?.description ?: "") }
    var laborCost by remember(existingRecord?.record?.id) { mutableStateOf(existingRecord?.record?.laborCostCop?.toString() ?: "0") }
    var nextMileage by remember(existingRecord?.record?.id) { mutableStateOf(existingRecord?.record?.nextServiceMileageKm?.toString() ?: "") }
    var nextDate by remember(existingRecord?.record?.id) { mutableStateOf(existingRecord?.record?.nextServiceDate ?: "") }
    var observations by remember(existingRecord?.record?.id) { mutableStateOf(existingRecord?.record?.observations ?: "") }
    val parts = remember(existingRecord?.record?.id) {
        mutableStateListOf<PartFormState>().apply {
            val existingParts = existingRecord?.parts.orEmpty()
            if (existingParts.isEmpty()) add(PartFormState())
            else existingParts.forEach { part ->
                add(
                    PartFormState(
                        name = part.name,
                        brand = part.brand,
                        reference = part.reference,
                        specification = part.specification,
                        quantity = part.quantity.toString(),
                        unit = part.unit,
                        unitPrice = part.unitPriceCop.toString()
                    )
                )
            }
        }
    }
    var localError by remember(existingRecord?.record?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(existingRecord?.record?.id) {
        // La pantalla se reinicializa cuando termina de cargar el registro que se editará.
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Volver al historial") }
        Text(
            if (existingRecord == null) "Registrar mantenimiento" else "Editar mantenimiento",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(vehicleTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Datos del servicio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = serviceType,
                    onValueChange = { serviceType = it },
                    label = { Text("Tipo de mantenimiento") },
                    placeholder = { Text("Ej. Cambio de aceite y filtros") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serviceDate,
                    onValueChange = { serviceDate = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it.filter(Char::isDigit).take(9) },
                    label = { Text("Kilometraje del vehículo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del trabajo realizado") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = laborCost,
                    onValueChange = { laborCost = it.filter(Char::isDigit).take(12) },
                    label = { Text("Costo de mano de obra (COP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nextMileage,
                    onValueChange = { nextMileage = it.filter(Char::isDigit).take(9) },
                    label = { Text("Próximo mantenimiento (km, opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nextDate,
                    onValueChange = { nextDate = it },
                    label = { Text("Próxima fecha sugerida (AAAA-MM-DD, opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    label = { Text("Observaciones") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Repuestos y materiales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { parts.add(PartFormState()) }) { Text("+ Agregar") }
                }
                parts.forEachIndexed { index, part ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Repuesto ${index + 1}", fontWeight = FontWeight.Bold)
                                if (parts.size > 1) TextButton(onClick = { parts.removeAt(index) }) { Text("Quitar") }
                            }
                            OutlinedTextField(
                                value = part.name,
                                onValueChange = { parts[index] = part.copy(name = it) },
                                label = { Text("Nombre del repuesto") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = part.brand,
                                onValueChange = { parts[index] = part.copy(brand = it) },
                                label = { Text("Marca") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = part.reference,
                                onValueChange = { parts[index] = part.copy(reference = it) },
                                label = { Text("Referencia") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = part.specification,
                                onValueChange = { parts[index] = part.copy(specification = it) },
                                label = { Text("Especificación (opcional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = part.quantity,
                                    onValueChange = { parts[index] = part.copy(quantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                                    label = { Text("Cantidad") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = part.unit,
                                    onValueChange = { parts[index] = part.copy(unit = it) },
                                    label = { Text("Unidad") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = part.unitPrice,
                                onValueChange = { parts[index] = part.copy(unitPrice = it.filter(Char::isDigit).take(12)) },
                                label = { Text("Precio unitario (COP)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        if (localError != null) {
            Text(localError!!, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                val parsedMileage = mileage.toIntOrNull()
                if (serviceType.isBlank()) {
                    localError = "Escribe el tipo de mantenimiento."
                } else if (!serviceDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                    localError = "La fecha debe tener el formato AAAA-MM-DD."
                } else if (parsedMileage == null) {
                    localError = "Ingresa el kilometraje del vehículo."
                } else if (description.isBlank()) {
                    localError = "Describe el trabajo realizado."
                } else {
                    localError = null
                    val draftParts = parts.filter { it.name.isNotBlank() }.map { part ->
                        MaintenancePartDraft(
                            name = part.name,
                            brand = part.brand,
                            reference = part.reference,
                            specification = part.specification,
                            quantity = part.quantity.replace(',', '.').toDoubleOrNull() ?: 1.0,
                            unit = part.unit,
                            unitPriceCop = part.unitPrice.toLongOrNull() ?: 0L
                        )
                    }
                    onSave(
                        MaintenanceDraft(
                            serviceType = serviceType,
                            serviceDate = serviceDate,
                            mileageKm = parsedMileage,
                            description = description,
                            laborCostCop = laborCost.toLongOrNull() ?: 0L,
                            nextServiceMileageKm = nextMileage.toIntOrNull(),
                            nextServiceDate = nextDate.takeIf { it.isNotBlank() },
                            observations = observations,
                            parts = draftParts
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp)
        ) {
            Text("Guardar mantenimiento", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
            Text("Cancelar")
        }
    }
}
