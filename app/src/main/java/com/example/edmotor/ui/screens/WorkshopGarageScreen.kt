package com.example.edmotor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Vehicle

@Composable
fun WorkshopGarageScreen(
    vehicles: List<Vehicle>,
    onVehicleClick: (Int) -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ED MOTOR / PANEL DEL TALLER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Vehículos de clientes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Selecciona un vehículo para administrar su historial.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp)) {
                Text("Salir")
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("VEHÍCULOS REGISTRADOS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(vehicles.size.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        if (vehicles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Todavía no hay vehículos registrados.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Cuando un cliente cree su cuenta y registre su automóvil, aparecerá automáticamente aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vehicles, key = { it.id }) { vehicle ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onVehicleClick(vehicle.id) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${vehicle.year} · ${vehicle.engine}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Placa: ${vehicle.plate.ifBlank { "No registrada" }}", style = MaterialTheme.typography.bodyMedium)
                            Text("Kilometraje: ${vehicle.mileage.ifBlank { "No registrado" }}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Cliente: ${vehicle.ownerName ?: "Sin propietario asignado"}${vehicle.ownerUsername?.let { " (@$it)" } ?: ""}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Button(onClick = { onVehicleClick(vehicle.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Abrir historial de mantenimiento")
                            }
                        }
                    }
                }
            }
        }
    }
}
