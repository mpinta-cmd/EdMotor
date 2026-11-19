package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.OrderWithItems
import com.example.edmotor.model.Vehicle

@Composable
fun WorkshopGarageScreen(
    profileName: String,
    vehicles: List<Vehicle>,
    clients: List<UserEntity>,
    orders: List<OrderWithItems>,
    isProcessing: Boolean,
    message: String?,
    isError: Boolean,
    onVehicleClick: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onReject: (Int) -> Unit,
    onLogout: () -> Unit,
    showPurchasesInitially: Boolean = false
) {
    var selectedTab by rememberSaveable { mutableStateOf(if (showPurchasesInitially) 1 else 0) }
    var expandedClientId by rememberSaveable { mutableStateOf<Int?>(null) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        GarageHeader(profileName = "$profileName · Propietario", onLogout = onLogout)
        GarageTabs(
            firstLabel = "Clientes (${clients.size})",
            firstIcon = Icons.Outlined.PersonOutline,
            secondLabel = "Compras (${orders.size})",
            secondIcon = Icons.Outlined.ShoppingBag,
            firstSelected = selectedTab == 0,
            onSelect = { selectedTab = it }
        )

        if (selectedTab == 0) {
            if (clients.isEmpty()) {
                GarageEmptyState(
                    Icons.Outlined.PersonOutline,
                    "Todavía no hay clientes registrados",
                    "Cuando una persona cree su cuenta, aparecerá en esta sección."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(clients, key = { it.id }) { client ->
                        val expanded = expandedClientId == client.id
                        WorkshopClientCard(
                            client = client,
                            vehicles = vehicles.filter { it.ownerUserId == client.id },
                            orders = orders.filter { it.order.clientUserId == client.id },
                            expanded = expanded,
                            onToggle = { expandedClientId = if (expanded) null else client.id },
                            onVehicleClick = onVehicleClick
                        )
                    }
                }
            }
        } else {
            if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            if (message != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isError) colors.errorContainer else colors.primaryContainer,
                    contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer
                ) {
                    Text(message, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                }
            }
            if (orders.isEmpty()) {
                GarageEmptyState(
                    Icons.Outlined.ShoppingBag,
                    "Todavía no hay compras registradas",
                    "Aquí aparecen los pedidos que envíen tus clientes."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders, key = { it.order.id }) { order ->
                        GarageOrderCard(orderWithItems = order, showClient = true) {
                            if (order.order.status == OrderStatus.PENDING) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = { onConfirm(order.order.id) },
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Aceptar pedido")
                                    }
                                    OutlinedButton(
                                        onClick = { onReject(order.order.id) },
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Rechazar pedido")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkshopClientCard(
    client: UserEntity,
    vehicles: List<Vehicle>,
    orders: List<OrderWithItems>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onVehicleClick: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GarageIconTile(Icons.Outlined.PersonOutline)
                Text(
                    client.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GarageDetailLabel("Vehículos: ${vehicles.size}")
                GarageDetailLabel("Pedidos: ${orders.size}")
            }
            OutlinedButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (expanded) "Ocultar historial del cliente" else "Ver vehículos e historial de compras",
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (expanded) {
                HorizontalDivider(color = colors.outline)
                Text("Vehículos del cliente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (vehicles.isEmpty()) {
                    Text("Este cliente todavía no registra vehículos.", color = colors.onSurfaceVariant)
                } else {
                    vehicles.forEach { vehicle ->
                        GarageVehicleCard(
                            vehicle = vehicle,
                            historyLabel = "Abrir historial de mantenimiento",
                            onOpenHistory = { onVehicleClick(vehicle.id) }
                        )
                    }
                }
                Text("Historial de compras", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (orders.isEmpty()) {
                    Text("Este cliente todavía no tiene pedidos.", color = colors.onSurfaceVariant)
                } else {
                    orders.forEach { order -> GarageOrderCard(orderWithItems = order) }
                }
            }
        }
    }
}
