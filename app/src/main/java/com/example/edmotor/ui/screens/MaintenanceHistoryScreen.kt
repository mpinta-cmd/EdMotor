package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.model.Vehicle
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun MaintenanceHistoryScreen(
    vehicle: Vehicle,
    records: List<MaintenanceWithParts>,
    canManage: Boolean,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onBack: () -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) { Text("← Volver") }
            if (canManage) Button(onClick = onAdd) { Text("+ Mantenimiento") }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${vehicle.year} · ${vehicle.plate.ifBlank { "Sin placa" }} · ${vehicle.mileage.ifBlank { "Kilometraje pendiente" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (canManage) "Historial administrado por el taller" else "Historial de mantenimiento de tu vehículo", style = MaterialTheme.typography.bodyMedium)
        }

        if (records.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center) {
                Text("Aún no hay mantenimientos registrados.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (canManage) "Registra el primer servicio realizado a este vehículo."
                    else "Cuando el taller registre el primer servicio, podrás consultar aquí todos los detalles.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canManage) {
                    Button(onClick = onAdd, modifier = Modifier.padding(top = 14.dp)) { Text("Registrar primer mantenimiento") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.record.id }) { item ->
                    val partsTotal = item.parts.sumOf { (it.quantity * it.unitPriceCop.toDouble()).toLong() }
                    val total = partsTotal + item.record.laborCostCop
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.record.serviceType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (canManage) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { onEdit(item.record.id) }) { Text("Editar") }
                                        OutlinedButton(onClick = { pendingDeleteId = item.record.id }) { Text("Eliminar") }
                                    }
                                }
                            }
                            Text("Fecha: ${item.record.serviceDate} · ${item.record.mileageKm} km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(item.record.description)
                            if (item.parts.isNotEmpty()) {
                                Text("Repuestos y materiales", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                item.parts.forEach { part ->
                                    val lineTotal = (part.quantity * part.unitPriceCop).toLong()
                                    Text("• ${part.name} ${part.brand.takeIf { it.isNotBlank() }?.let { "· $it" } ?: ""} · ${part.quantity} ${part.unit} · ${formatCop(lineTotal)}")
                                    if (part.reference.isNotBlank()) Text("  Referencia: ${part.reference}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text("Mano de obra: ${formatCop(item.record.laborCostCop)}")
                            Text("Total registrado: ${formatCop(total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Eliminar mantenimiento") },
            text = { Text("¿Seguro que deseas eliminar este registro y sus repuestos asociados? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId?.let(onDelete)
                    pendingDeleteId = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancelar") }
            }
        )
    }
}

private fun formatCop(value: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.currency = Currency.getInstance("COP")
    format.maximumFractionDigits = 0
    return format.format(value)
}
