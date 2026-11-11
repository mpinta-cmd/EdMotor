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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.components.AddVehicleBottomSheet

@Composable
fun ClientGarageScreen(
    vehicles: List<Vehicle>,
    onAddVehicle: (String, String, Int, String, String, String, String) -> Unit,
    onVehicleClick: (Int) -> Unit,
    onLogout: () -> Unit
) {
    var showAddVehicle by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ED MOTOR / ESPACIO PERSONAL", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Mi garaje", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Registra tus vehículos y consulta su historial.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp)) { Text("Salir") }
        }

        Button(
            onClick = { showAddVehicle = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("+ Registrar mi vehículo") }

        if (vehicles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Tu garaje está vacío", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Registra tu automóvil. Cuando el taller realice un servicio, el historial aparecerá aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
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
                            Text("Placa: ${vehicle.plate.ifBlank { "No registrada" }}")
                            Text("Kilometraje inicial: ${vehicle.mileage.ifBlank { "No registrado" }}")
                            Text("Toca para consultar el historial de este vehículo.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showAddVehicle) {
        AddVehicleBottomSheet(
            onDismiss = { showAddVehicle = false },
            onVehicleAdded = { brand, model, year, engine, plate, mileage, notes ->
                onAddVehicle(brand, model, year, engine, plate, mileage, notes)
            }
        )
    }
}
