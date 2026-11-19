package com.example.edmotor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.OrderWithItems
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.components.AddVehicleBottomSheet
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClientGarageScreen(
    vehicles: List<Vehicle>,
    orders: List<OrderWithItems>,
    onAddVehicle: (String, String, Int, String, String, String, String, (Result<Long>) -> Unit) -> Unit,
    onVehicleClick: (Int) -> Unit,
    onLogout: () -> Unit
) {
    var showAddVehicle by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val currency = remember { NumberFormat.getNumberInstance(Locale("es", "CO")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "ED MOTOR / ESPACIO PERSONAL",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Mi garaje", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Consulta tus vehículos, mantenimientos y compras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp)) { Text("Salir") }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Mis vehículos") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Mis compras") })
        }

        if (selectedTab == 0) {
            Column(Modifier.fillMaxSize()) {
                Button(
                    onClick = { showAddVehicle = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("+ Registrar mi vehículo") }

                if (vehicles.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tu garaje está vacío", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Registra tu automóvil. Cuando el taller realice un servicio, el historial aparecerá aquí.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vehicles, key = { "vehicle_${it.id}" }) { vehicle ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onVehicleClick(vehicle.id) },
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("${vehicle.year} · ${vehicle.engine.ifBlank { "Motor sin registrar" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Placa: ${vehicle.plate.ifBlank { "No registrada" }}")
                                    Text("Kilometraje: ${vehicle.mileage.ifBlank { "No registrado" }}")
                                    Text("Consultar historial de mantenimiento", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (orders.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "Aquí aparecen tu historial y los pedidos guardados en este dispositivo.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders, key = { it.order.id }) { orderWithItems ->
                        val order = orderWithItems.order
                        val (statusLabel, statusColor) = when (order.status) {
                            OrderStatus.PENDING -> "PENDIENTE DE CONFIRMACIÓN" to MaterialTheme.colorScheme.tertiary
                            OrderStatus.CONFIRMED -> "PEDIDO CONFIRMADO" to MaterialTheme.colorScheme.primary
                            OrderStatus.REJECTED -> "PEDIDO RECHAZADO" to MaterialTheme.colorScheme.error
                            else -> order.status to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pedido #${order.id}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                    Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                                        Text(
                                            statusLabel,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            color = statusColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(dateFormat.format(Date(order.createdAtMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                orderWithItems.items.forEach { item ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.productName, fontWeight = FontWeight.SemiBold)
                                            Text("Cantidad: ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("$${currency.format(item.subtotalCop)}", fontWeight = FontWeight.Medium)
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total", fontWeight = FontWeight.Bold)
                                    Text("$${currency.format(order.totalCop)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                                }
                                when (order.status) {
                                    OrderStatus.PENDING -> Text("Pedido guardado en este dispositivo. Pendiente de revisión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                    OrderStatus.CONFIRMED -> Text("El taller confirmó tu pedido.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    OrderStatus.REJECTED -> Text("El taller rechazó este pedido. El inventario se conserva.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddVehicle) {
        AddVehicleBottomSheet(
            onDismiss = { showAddVehicle = false },
            onVehicleAdded = onAddVehicle
        )
    }
}
