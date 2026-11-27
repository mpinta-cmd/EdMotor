package com.example.edmotor.online

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.ui.screens.GarageEmptyState
import com.example.edmotor.ui.screens.GarageHeader
import com.example.edmotor.ui.screens.GarageIconTile
import com.example.edmotor.ui.screens.formatAppointmentDateTime
import java.util.Calendar
import java.util.Locale

@Composable
internal fun OnlineGarageScreen(
    vm: OnlineViewModel,
    state: OnlineState,
    onOpenAppointment: (OnlineAppointment) -> Unit,
    onOpenOrders: () -> Unit
) {
    val user = state.user ?: return
    val isWorkshop = user.role == "WORKSHOP"
    val colors = MaterialTheme.colorScheme
    val vehiclesSync = state.syncStatus(OnlineResource.VEHICLES)
    val visibleVehicles = remember(state.vehicles, user.id, isWorkshop) {
        state.vehicles.filter { isWorkshop || it.clientId == user.id }
            .sortedWith(compareBy({ it.brand.lowercase(Locale.ROOT) }, { it.model.lowercase(Locale.ROOT) }, { it.id }))
    }
    val clientsAndVehicles = remember(visibleVehicles, isWorkshop) {
        if (isWorkshop) visibleVehicles.groupBy { it.clientId }.values
            .sortedBy { it.first().clientName.lowercase(Locale.ROOT) }
        else emptyList()
    }
    var showVehicleForm by rememberSaveable(user.id) { mutableStateOf(false) }
    var submittedVehicle by rememberSaveable(user.id) { mutableStateOf(false) }
    var historyVehicleId by rememberSaveable(user.id) { mutableStateOf<Long?>(null) }
    val historyVehicle = visibleVehicles.firstOrNull { it.id == historyVehicleId }

    val context = LocalContext.current
    val noticePreferences = remember(context) {
        context.getSharedPreferences("online_appointment_notices", Context.MODE_PRIVATE)
    }
    val noticeKey = "pickup_${user.id}_${user.username}"
    var acknowledgedAt by remember(noticeKey) { mutableLongStateOf(noticePreferences.getLong(noticeKey, 0L)) }
    val pickup = remember(state.appointments, user.id, isWorkshop, acknowledgedAt) {
        if (isWorkshop) null else state.appointments
            .filter {
                it.clientId == user.id && it.status == AppointmentStatus.COMPLETED &&
                    (it.finishedAtMillis ?: 0L) > acknowledgedAt
            }
            .maxByOrNull { it.finishedAtMillis ?: 0L }
    }

    Column(Modifier.fillMaxSize()) {
        GarageHeader(profileName = user.displayName, onLogout = { vm.logout() })
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vehiclesSync.waitingForFirstData) {
                item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            val message = state.message
            if (message != null) {
                item(key = "message") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (message.isError) colors.errorContainer else colors.primaryContainer,
                        contentColor = if (message.isError) colors.onErrorContainer else colors.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(message.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = vm::clearMessage) { Text("Cerrar") }
                        }
                    }
                }
            }
            if (pickup != null) {
                item(key = "pickup") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                                Text(
                                    "Listo para recoger",
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text("${pickup.vehicleBrand} ${pickup.vehicleModel} · ${pickup.vehicleYear}")
                            pickup.finishedAtMillis?.let { Text("Terminada el ${formatAppointmentDateTime(it, onlineWorkshopTimeZone)}") }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpenAppointment(pickup) }) { Text("Ver cita") }
                                TextButton(onClick = {
                                    val seenAt = pickup.finishedAtMillis ?: return@TextButton
                                    acknowledgedAt = seenAt
                                    noticePreferences.edit().putLong(noticeKey, seenAt).apply()
                                }) { Text("Entendido") }
                            }
                        }
                    }
                }
            }
            item(key = "controls") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isWorkshop) {
                        Button(
                            onClick = {
                                submittedVehicle = false
                                showVehicleForm = true
                            },
                            enabled = !state.busy
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Registrar mi vehículo", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    OutlinedButton(onClick = onOpenOrders) {
                        Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (isWorkshop) "Pedidos" else "Mis compras", modifier = Modifier.padding(start = 6.dp))
                    }
                    TextButton(onClick = { vm.refresh() }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Actualizar", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            item(key = "heading") {
                Text(
                    if (isWorkshop) "Clientes y vehículos" else "Mis vehículos",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (visibleVehicles.isEmpty() && vehiclesSync.loaded) {
                item(key = "empty") {
                    GarageEmptyState(
                        Icons.Outlined.DirectionsCar,
                        if (isWorkshop) "Aún no hay vehículos de clientes" else "Tu garaje está vacío",
                        if (isWorkshop) "Los vehículos aparecen cuando un cliente los registra."
                        else "Registra tu vehículo para solicitar citas y consultar su historial.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (isWorkshop) {
                clientsAndVehicles.forEach { vehicles ->
                    val clientVehicle = vehicles.first()
                    item(key = "client_${clientVehicle.clientId}") {
                        Text(
                            clientVehicle.clientName.ifBlank { "Cliente" },
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                    items(vehicles, key = { it.id }) { vehicle ->
                        OnlineVehicleCard(vehicle) { historyVehicleId = vehicle.id }
                    }
                }
            } else {
                items(visibleVehicles, key = { it.id }) { vehicle ->
                    OnlineVehicleCard(vehicle) { historyVehicleId = vehicle.id }
                }
            }
        }
    }

    if (!isWorkshop && showVehicleForm) {
        AddOnlineVehicleDialog(
            submitting = state.busy,
            message = state.message?.takeIf { submittedVehicle && it.isError }?.text,
            findModels = vm::findVehicleModels,
            onDismiss = { showVehicleForm = false },
            onAdd = { brand, model, year, plate ->
                vm.clearMessage()
                submittedVehicle = true
                vm.createVehicle(brand, model, year, plate) {
                    submittedVehicle = false
                    showVehicleForm = false
                }
            }
        )
    }
    if (historyVehicle != null) {
        val reports = state.maintenance.filter { it.vehicleId == historyVehicle.id }.sortedByDescending { it.createdAtMillis }
        VehicleHistoryDialog(historyVehicle, reports, onDismiss = { historyVehicleId = null })
    }
}

@Composable
private fun OnlineVehicleCard(vehicle: OnlineVehicle, onOpenHistory: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outline),
        color = colors.surface
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GarageIconTile(Icons.Outlined.DirectionsCar)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(vehicle.year.toString(), vehicle.plate).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onOpenHistory) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Abrir historial de mantenimiento", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun AddOnlineVehicleDialog(
    submitting: Boolean,
    message: String?,
    findModels: suspend (String, Int) -> List<VehicleModelSuggestion>,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String) -> Unit
) {
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var plate by rememberSaveable { mutableStateOf("") }
    var showModelLookup by rememberSaveable { mutableStateOf(false) }
    val maxYear = remember { Calendar.getInstance(onlineWorkshopTimeZone).get(Calendar.YEAR) + 1 }
    val vehicleYear = year.toIntOrNull()?.takeIf { it in 1886..maxYear }
    val lookupYear = vehicleYear?.takeIf { it >= 1996 }
    val searchYear = lookupYear?.takeIf { showModelLookup && brand.isNotBlank() }

    AlertDialog(
        onDismissRequest = {
            if (searchYear != null) showModelLookup = false
            else if (!submitting) onDismiss()
        },
        title = { Text(if (searchYear == null) "Registrar mi vehículo" else "Modelos de vehículo") },
        text = {
            if (searchYear != null) {
                VehicleModelLookupContent(brand.trim(), searchYear, findModels, onSelect = { suggestion ->
                    brand = suggestion.make
                    model = suggestion.model
                    showModelLookup = false
                })
            } else {
                val focus = LocalFocusManager.current
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(brand, { brand = it.take(80) }, label = { Text("Marca") }, singleLine = true, enabled = !submitting)
                    OutlinedTextField(
                        year,
                        { year = it.filter { char -> char in '0'..'9' }.take(4) },
                        label = { Text("Año") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = year.length == 4 && vehicleYear == null,
                        enabled = !submitting
                    )
                    OutlinedButton(
                        onClick = {
                            focus.clearFocus()
                            showModelLookup = true
                        },
                        enabled = !submitting && brand.isNotBlank() && lookupYear != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Buscar modelos", modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(
                        if (vehicleYear != null && lookupYear == null) {
                            "Para vehículos anteriores a 1996, escribe el modelo."
                        } else {
                            "Consulta NHTSA vPIC por marca y año o escribe el modelo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(model, { model = it.take(80) }, label = { Text("Modelo") }, singleLine = true, enabled = !submitting)
                    OutlinedTextField(plate, { plate = it.take(20) }, label = { Text("Placa (opcional)") }, singleLine = true, enabled = !submitting)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (searchYear != null) {
                TextButton(onClick = { showModelLookup = false }) { Text("Volver al formulario") }
            } else {
                Button(
                    onClick = { vehicleYear?.let { onAdd(brand.trim(), model.trim(), it, plate.trim().uppercase(Locale.ROOT)) } },
                    enabled = !submitting && brand.isNotBlank() && model.isNotBlank() && vehicleYear != null
                ) { Text(if (submitting) "Guardando vehículo" else "Registrar vehículo") }
            }
        },
        dismissButton = {
            if (searchYear == null) {
                TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cerrar") }
            }
        }
    )
}

@Composable
private fun VehicleHistoryDialog(
    vehicle: OnlineVehicle,
    reports: List<OnlineMaintenance>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial de mantenimiento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${vehicle.brand} ${vehicle.model} · ${vehicle.year}", fontWeight = FontWeight.Bold)
                if (reports.isEmpty()) {
                    Text("Aún no hay informes de mantenimiento.")
                } else {
                    reports.forEach { report ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    formatAppointmentDateTime(report.createdAtMillis, onlineWorkshopTimeZone),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(report.summary, style = MaterialTheme.typography.bodyMedium)
                                report.mileageKm?.let { Text("Kilometraje: $it km", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
