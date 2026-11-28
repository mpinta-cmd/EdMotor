package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.components.EdMotorBottomSheet
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaintenanceHistoryScreen(
    vehicle: Vehicle,
    records: List<MaintenanceWithParts>,
    isLoading: Boolean,
    isProcessing: Boolean,
    canManage: Boolean,
    message: String?,
    onClearMessage: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onBack: () -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onBack) { Text("← Volver") }
                if (canManage) Button(onClick = onAdd, enabled = !isProcessing && !isLoading) { Text("+ Mantenimiento") }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${vehicle.year} · ${vehicle.plate.ifBlank { "Sin placa" }} · ${vehicle.mileage.ifBlank { "Kilometraje pendiente" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (canManage) "Historial administrado por el taller" else "Historial de mantenimiento de tu vehículo", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (isLoading || isProcessing) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        if (!message.isNullOrBlank()) {
            item {
                Row {
                    Text(message, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = onClearMessage) { Text("Cerrar") }
                }
            }
        }

        if (!isLoading && records.isEmpty()) {
            item {
                Column(Modifier.padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aún no hay mantenimientos registrados.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (canManage) "Registra el primer servicio realizado a este vehículo."
                        else "Cuando el taller registre el primer servicio, podrás consultar aquí todos los detalles.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (canManage) {
                        Button(onClick = onAdd, enabled = !isProcessing) { Text("Registrar primer mantenimiento") }
                    }
                }
            }
        } else {
            items(records, key = { it.record.id }) { item ->
                val partTotals = item.parts.map { part ->
                    part.quantity.takeIf(Double::isFinite)?.let { quantity ->
                        BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(part.unitPriceCop)).toBigInteger()
                    }
                }
                val total = partTotals.fold(BigInteger.valueOf(item.record.laborCostCop)) { amount, line ->
                    amount + (line ?: BigInteger.ZERO)
                }.takeIf { partTotals.none { it == null } }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.record.serviceType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (canManage) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { onEdit(item.record.id) }, enabled = !isProcessing) { Text("Editar") }
                                OutlinedButton(onClick = { pendingDeleteId = item.record.id }, enabled = !isProcessing) { Text("Eliminar") }
                            }
                        }
                        Text("Fecha: ${item.record.serviceDate} · ${item.record.mileageKm} km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(item.record.description)
                        if (item.parts.isNotEmpty()) {
                            Text("Repuestos y materiales", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            item.parts.zip(partTotals).forEach { (part, lineTotal) ->
                                val price = lineTotal?.let(::formatCop) ?: "Importe no disponible"
                                Text("• ${part.name} ${part.brand.takeIf { it.isNotBlank() }?.let { "· $it" } ?: ""} · ${part.quantity} ${part.unit} · $price")
                                if (part.reference.isNotBlank()) Text("  Referencia: ${part.reference}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("Mano de obra: ${formatCop(item.record.laborCostCop)}")
                        Text("Total registrado: ${total?.let(::formatCop) ?: "Importe no disponible"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        item.record.nextServiceMileageKm?.let { Text("Próximo servicio sugerido: ${it} km") }
                        item.record.nextServiceDate?.takeIf { it.isNotBlank() }?.let { Text("Fecha sugerida: $it") }
                        if (item.record.observations.isNotBlank()) {
                            Text("Observaciones: ${item.record.observations}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteId != null) {
        EdMotorBottomSheet(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Eliminar mantenimiento") },
            text = { Text("¿Seguro que deseas eliminar este registro y sus repuestos asociados? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId?.let(onDelete)
                        pendingDeleteId = null
                    },
                    enabled = !isProcessing
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancelar") }
            }
        )
    }
}

private fun formatCop(value: Number): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.currency = Currency.getInstance("COP")
    format.maximumFractionDigits = 0
    return format.format(value)
}
