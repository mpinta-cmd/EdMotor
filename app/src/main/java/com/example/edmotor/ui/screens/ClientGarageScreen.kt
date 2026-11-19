package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
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
    profileName: String,
    vehicles: List<Vehicle>,
    orders: List<OrderWithItems>,
    onAddVehicle: (String, String, Int, String, String, String, String, (Result<Long>) -> Unit) -> Unit,
    onVehicleClick: (Int) -> Unit,
    onLogout: () -> Unit,
    pickupNotice: Appointment? = null,
    onViewPickup: (Appointment) -> Unit = {},
    onDismissPickup: (Appointment) -> Unit = {}
) {
    var showAddVehicle by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val vehicleListState = rememberLazyListState()
    val ordersListState = rememberLazyListState()
    val listState = if (selectedTab == 0) vehicleListState else ordersListState
    val finishedAt = pickupNotice?.takeIf { it.status == AppointmentStatus.COMPLETED }
        ?.finishedAtMillis?.takeIf { it > 0L }
    LaunchedEffect(listState, pickupNotice?.id, pickupNotice?.createdAtMillis, finishedAt) {
        if (finishedAt != null) listState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        GarageHeader(profileName = profileName, onLogout = onLogout)
        GarageTabs(
            firstLabel = "Mis vehículos",
            firstIcon = Icons.Outlined.DirectionsCar,
            secondLabel = "Mis compras",
            secondIcon = Icons.Outlined.ShoppingBag,
            firstSelected = selectedTab == 0,
            onSelect = { selectedTab = it }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pickupNotice != null && finishedAt != null) {
                item(key = "pickup") {
                    GaragePickupNotice(
                        finishedAtMillis = finishedAt,
                        onView = { onViewPickup(pickupNotice) },
                        onDismiss = { onDismissPickup(pickupNotice) }
                    )
                }
            }
            if (selectedTab == 0) {
                item(key = "register") {
                    Button(
                        onClick = { showAddVehicle = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar mi vehículo")
                    }
                }
                if (vehicles.isEmpty()) {
                    item(key = "empty") {
                        GarageEmptyState(
                            Icons.Outlined.DirectionsCar,
                            "Tu garaje está vacío",
                            "Registra tu automóvil para consultar aquí sus mantenimientos.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(vehicles, key = { it.id }) { vehicle ->
                        GarageVehicleCard(
                            vehicle = vehicle,
                            historyLabel = "Consultar historial de mantenimiento",
                            wholeCardClickable = true,
                            onOpenHistory = { onVehicleClick(vehicle.id) }
                        )
                    }
                }
            } else if (orders.isEmpty()) {
                item(key = "empty") {
                    GarageEmptyState(
                        Icons.Outlined.ShoppingBag,
                        "Todavía no tienes pedidos",
                        "Aquí puedes consultar el estado de los pedidos guardados en este dispositivo.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(orders, key = { it.order.id }) { order ->
                    GarageOrderCard(orderWithItems = order, customerFacing = true)
                }
            }
        }
    }

    if (showAddVehicle) {
        AddVehicleBottomSheet(onDismiss = { showAddVehicle = false }, onVehicleAdded = onAddVehicle)
    }
}

@Composable
private fun GaragePickupNotice(
    finishedAtMillis: Long,
    onView: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                Column(
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                    },
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Listo para recoger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("EdMotor terminó el servicio. Puedes pasar por tu vehículo.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Terminada el ${formatAppointmentDateTime(finishedAtMillis)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(onClick = onView, shape = RoundedCornerShape(12.dp)) { Text("Ver cita") }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSecondaryContainer)
                ) { Text("Entendido") }
            }
        }
    }
}

@Composable
internal fun GarageHeader(profileName: String, onLogout: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                GarageIconTile(Icons.Outlined.DirectionsCar)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Mi garaje",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        profileName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(
                onClick = onLogout,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cerrar sesión")
            }
        }
    }
}

@Composable
internal fun GarageTabs(
    firstLabel: String,
    firstIcon: ImageVector,
    secondLabel: String,
    secondIcon: ImageVector,
    firstSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier.selectableGroup().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(firstLabel to firstIcon, secondLabel to secondIcon).forEachIndexed { index, (label, icon) ->
                val selected = (index == 0) == firstSelected
                val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.primaryContainer else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun GarageIconTile(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        contentColor = colors.primary,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
    }
}

@Composable
internal fun GarageEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(16.dp)
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GarageIconTile(icon)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun GarageVehicleCard(
    vehicle: Vehicle,
    historyLabel: String,
    wholeCardClickable: Boolean = false,
    onOpenHistory: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .then(if (wholeCardClickable) Modifier.clickable(role = Role.Button, onClick = onOpenHistory) else Modifier),
        shape = shape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GarageIconTile(Icons.Outlined.DirectionsCar)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "${vehicle.brand} ${vehicle.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${vehicle.year} · ${vehicle.engine.ifBlank { "Motor sin registrar" }}",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GarageDetailLabel("Placa: ${vehicle.plate.ifBlank { "No registrada" }}")
                GarageDetailLabel("Kilometraje: ${vehicle.mileage.ifBlank { "No registrado" }}")
            }
            if (wholeCardClickable) {
                HorizontalDivider(color = colors.outline)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.primary)
                    Text(
                        historyLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary
                    )
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.primary)
                }
            } else {
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(historyLabel)
                }
            }
        }
    }
}

@Composable
internal fun GarageDetailLabel(label: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceVariant, contentColor = colors.onSurfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun GarageOrderCard(
    orderWithItems: OrderWithItems,
    customerFacing: Boolean = false,
    showClient: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val order = orderWithItems.order
    val currency = remember { NumberFormat.getNumberInstance(Locale("es", "CO")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")) }
    val (status, containerColor, contentColor) = when (order.status) {
        OrderStatus.PENDING -> Triple(
            if (customerFacing) "PENDIENTE DE CONFIRMACIÓN" else "PENDIENTE",
            colors.secondaryContainer,
            colors.onSecondaryContainer
        )
        OrderStatus.CONFIRMED -> Triple(
            if (customerFacing) "PEDIDO CONFIRMADO" else "CONFIRMADO",
            colors.primaryContainer,
            colors.onPrimaryContainer
        )
        OrderStatus.REJECTED -> Triple(
            if (customerFacing) "PEDIDO RECHAZADO" else "RECHAZADO",
            colors.errorContainer,
            colors.onErrorContainer
        )
        else -> Triple(order.status, colors.surfaceVariant, colors.onSurfaceVariant)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GarageIconTile(Icons.Outlined.ReceiptLong)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Pedido #${order.id}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            dateFormat.format(Date(order.createdAtMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = containerColor,
                    contentColor = contentColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (showClient) Text("Cliente: ${order.clientName}", fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = colors.outline)
            orderWithItems.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.productName, fontWeight = FontWeight.Medium)
                        Text(
                            if (customerFacing) "Cantidad: ${item.quantity}"
                            else "${item.quantity} × $${currency.format(item.unitPriceCop)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Text("$${currency.format(item.subtotalCop)}", fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceVariant,
                contentColor = colors.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        "$${currency.format(order.totalCop)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (customerFacing && order.status == OrderStatus.PENDING) {
                Text(
                    "Pedido guardado en este dispositivo. Pendiente de revisión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            actions()
        }
    }
}
