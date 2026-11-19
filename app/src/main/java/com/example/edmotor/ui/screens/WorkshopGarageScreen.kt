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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.OrderWithItems
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.Vehicle
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkshopGarageScreen(
    vehicles: List<Vehicle>,
    clients: List<UserEntity>,
    orders: List<OrderWithItems>,
    onVehicleClick: (Int) -> Unit,
    onOpenOrders: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var expandedClientId by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "ED MOTOR / PANEL DEL TALLER",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Mi garaje", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Consulta clientes, vehículos y el historial de compras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp)) { Text("Salir") }
        }

        Button(
            onClick = onOpenOrders,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Administrar pedidos y confirmar ventas", fontWeight = FontWeight.Bold)
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Clientes (${clients.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Vehículos (${vehicles.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Compras (${orders.size})") })
        }

        when (selectedTab) {
            0 -> {
                if (clients.isEmpty()) {
                    EmptyWorkshopMessage(
                        "Todavía no hay clientes registrados",
                        "Cuando una persona cree su cuenta, aparecerá en esta sección."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(clients, key = { "client_${it.id}" }) { client ->
                            val clientVehicles = vehicles.filter { it.ownerUserId == client.id }
                            val clientOrders = orders.filter { it.order.clientUserId == client.id }
                            val expanded = expandedClientId == client.id

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(client.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Vehículos: ${clientVehicles.size}")
                                        Text("Pedidos: ${clientOrders.size}")
                                    }
                                    Button(
                                        onClick = { expandedClientId = if (expanded) null else client.id },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (expanded) "Ocultar historial del cliente" else "Ver vehículos e historial de compras")
                                    }

                                    if (expanded) {
                                        Text(
                                            "VEHÍCULOS DEL CLIENTE",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (clientVehicles.isEmpty()) {
                                            Text("Este cliente todavía no registra vehículos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            clientVehicles.forEach { vehicle ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().clickable { onVehicleClick(vehicle.id) },
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                    shape = RoundedCornerShape(13.dp)
                                                ) {
                                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text("${vehicle.brand} ${vehicle.model}", fontWeight = FontWeight.Bold)
                                                        Text(
                                                            "${vehicle.year} · ${vehicle.plate.ifBlank { "Sin placa" }} · ${vehicle.mileage.ifBlank { "Sin kilometraje" }}",
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                        Text("Abrir historial de mantenimiento", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            "HISTORIAL DE COMPRAS",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (clientOrders.isEmpty()) {
                                            Text("Este cliente todavía no tiene pedidos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            clientOrders.forEach { order ->
                                                WorkshopOrderCard(order = order, showClient = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                if (vehicles.isEmpty()) {
                    EmptyWorkshopMessage(
                        "Todavía no hay vehículos registrados",
                        "Los vehículos que registren los clientes aparecerán aquí."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vehicles, key = { "vehicle_${it.id}" }) { vehicle ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onVehicleClick(vehicle.id) },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("${vehicle.year} · ${vehicle.engine.ifBlank { "Motor sin registrar" }}")
                                    Text("Placa: ${vehicle.plate.ifBlank { "No registrada" }}")
                                    Text("Kilometraje: ${vehicle.mileage.ifBlank { "No registrado" }}")
                                    Text(
                                        "Cliente: ${vehicle.ownerName ?: "Sin propietario asignado"}",
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

            else -> {
                if (orders.isEmpty()) {
                    EmptyWorkshopMessage(
                        "Todavía no hay compras registradas",
                    "Aquí aparecen los pedidos guardados en este dispositivo."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(orders, key = { it.order.id }) { order ->
                            WorkshopOrderCard(order = order, showClient = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkshopOrderCard(
    order: OrderWithItems,
    showClient: Boolean
) {
    val currency = NumberFormat.getNumberInstance(Locale("es", "CO"))
    val dateFormat = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO"))
    val status = when (order.order.status) {
        OrderStatus.PENDING -> "PENDIENTE" to MaterialTheme.colorScheme.tertiary
        OrderStatus.CONFIRMED -> "CONFIRMADO" to MaterialTheme.colorScheme.primary
        OrderStatus.REJECTED -> "RECHAZADO" to MaterialTheme.colorScheme.error
        else -> order.order.status to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pedido #${order.order.id}", fontWeight = FontWeight.ExtraBold)
                Surface(color = status.second.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                    Text(
                        status.first,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = status.second,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (showClient) {
                Text(
                    "Cliente: ${order.order.clientName}",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                dateFormat.format(Date(order.order.createdAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            order.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.productName, fontWeight = FontWeight.Medium)
                        Text(
                            "${item.quantity} × $${currency.format(item.unitPriceCop)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("$${currency.format(item.subtotalCop)}")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontWeight = FontWeight.Bold)
                Text(
                    "$${currency.format(order.order.totalCop)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun EmptyWorkshopMessage(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
