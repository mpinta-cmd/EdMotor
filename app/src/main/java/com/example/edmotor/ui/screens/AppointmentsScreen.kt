package com.example.edmotor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.BuildConfig
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.Vehicle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppointmentsScreen(
    isWorkshop: Boolean,
    appointments: List<Appointment>,
    syncState: AppointmentSyncState,
    vehicles: List<Vehicle>,
    isProcessing: Boolean,
    message: String?,
    isError: Boolean,
    onCreateAppointment: (Vehicle, Long, String) -> Unit,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onReschedule: (Long, Long) -> Unit,
    onClearMessage: () -> Unit,
    onRefresh: () -> Unit
) {
    var showAgenda by rememberSaveable(isWorkshop) { mutableStateOf(false) }
    var rescheduleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var cancelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val requestListState = rememberLazyListState()
    val agendaListState = rememberLazyListState()
    val listState = if (showAgenda) agendaListState else requestListState
    val isConfigured = syncState != AppointmentSyncState.NOT_CONFIGURED
    val canManage = syncState == AppointmentSyncState.UP_TO_DATE && !isProcessing
    val upcomingCount = appointments.count {
        it.status == AppointmentStatus.CONFIRMED && it.scheduledAtMillis >= nowMillis
    }
    val requests = remember(appointments, isWorkshop) {
        if (isWorkshop) {
            appointments.filter { it.status == AppointmentStatus.PENDING }.sortedBy { it.scheduledAtMillis }
        } else {
            appointments.filter { it.status != AppointmentStatus.CONFIRMED }.sortedByDescending { it.createdAtMillis }
        }
    }
    val trackedAppointments = remember(appointments) {
        appointments.filter { it.status != AppointmentStatus.PENDING }.sortedBy { it.scheduledAtMillis }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) listState.scrollToItem(0)
    }
    BackHandler(enabled = showAgenda) { showAgenda = false }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showAgenda) {
                    TextButton(onClick = { showAgenda = false }) {
                        Text(if (isWorkshop) "Volver a solicitudes" else "Volver a solicitar cita")
                    }
                }
                Text(
                    text = when {
                        showAgenda -> "Citas agendadas"
                        isWorkshop -> "Solicitudes de servicio"
                        else -> "Solicita una cita"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!showAgenda) {
                    Text(
                        text = if (isWorkshop) {
                            "Revisa las solicitudes y ajusta su horario antes de confirmarlas."
                        } else {
                            "Elige tu vehículo, el servicio y un horario para que EdMotor revise tu solicitud."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isConfigured) {
                        OpenAgendaButton(
                            upcomingCount = upcomingCount,
                            onClick = { showAgenda = true }
                        )
                    }
                }
            }
        }

        if (!message.isNullOrBlank()) {
            item(key = "message") {
                AppointmentMessage(message, isError, onClearMessage)
            }
        }

        item(key = "sync") {
            AppointmentSyncNotice(syncState, isWorkshop, appointments.isNotEmpty(), isProcessing, onRefresh)
        }

        if (isConfigured) {
            if (showAgenda && isWorkshop) {
                item(key = "agenda") {
                    WorkshopAppointmentAgenda(
                        appointments = trackedAppointments,
                        nowMillis = nowMillis,
                        showEmptyDay = syncState == AppointmentSyncState.UP_TO_DATE,
                        appointmentContent = { appointment ->
                            AppointmentCard(
                                appointment = appointment,
                                isWorkshop = true,
                                canManage = canManage,
                                onAccept = { onAccept(appointment.id) },
                                onReject = { onReject(appointment.id) },
                                onReschedule = { rescheduleId = appointment.id },
                                onCancel = { cancelId = appointment.id }
                            )
                        }
                    )
                }
            } else {
                if (!isWorkshop && !showAgenda) {
                    item(key = "request") {
                        AppointmentRequestForm(
                            vehicles = vehicles,
                            nowMillis = nowMillis,
                            isProcessing = isProcessing,
                            onCreateAppointment = onCreateAppointment
                        )
                    }
                }

                item(key = "heading") {
                    Text(
                        text = when {
                            showAgenda -> "Mis citas confirmadas"
                            isWorkshop -> "Solicitudes pendientes (${requests.size})"
                            else -> "Mis solicitudes"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                val visibleAppointments = if (showAgenda) {
                    trackedAppointments.filter { it.status == AppointmentStatus.CONFIRMED }
                        .sortedBy { it.scheduledAtMillis < nowMillis }
                } else {
                    requests
                }
                if (visibleAppointments.isEmpty() && syncState == AppointmentSyncState.UP_TO_DATE) {
                    item(key = "empty") {
                        EmptyAppointmentsMessage(
                            when {
                                showAgenda -> "Tus citas aparecerán aquí cuando EdMotor las confirme."
                                isWorkshop -> "No hay solicitudes pendientes de revisar."
                                else -> "Aquí aparecerán tus solicitudes y los cambios de estado."
                            }
                        )
                    }
                }
                items(visibleAppointments, key = { it.id }) { appointment ->
                    AppointmentCard(
                        appointment = appointment,
                        isWorkshop = isWorkshop,
                        canManage = canManage,
                        onAccept = { onAccept(appointment.id) },
                        onReject = { onReject(appointment.id) },
                        onReschedule = { rescheduleId = appointment.id },
                        onCancel = { cancelId = appointment.id }
                    )
                }
            }
        }
    }

    val appointmentToReschedule = appointments.firstOrNull { it.id == rescheduleId && it.canBeChanged() }
    if (appointmentToReschedule != null) {
        AppointmentRescheduleDialog(
            appointment = appointmentToReschedule,
            isWorkshop = isWorkshop,
            nowMillis = nowMillis,
            enabled = canManage,
            onDismiss = { rescheduleId = null },
            onSave = { newTime ->
                rescheduleId = null
                onReschedule(appointmentToReschedule.id, newTime)
            }
        )
    }

    val appointmentToCancel = appointments.firstOrNull { it.id == cancelId && it.canBeChanged() }
    if (appointmentToCancel != null) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.EventAvailable, contentDescription = null) },
            title = { Text("Cancelar cita") },
            text = { Text("Se cancelará la cita del ${formatAppointmentDateTime(appointmentToCancel.scheduledAtMillis)}.") },
            onDismissRequest = { cancelId = null },
            confirmButton = {
                Button(
                    enabled = canManage,
                    onClick = {
                        cancelId = null
                        onCancel(appointmentToCancel.id)
                    }
                ) { Text("Confirmar cancelación") }
            },
            dismissButton = { TextButton(onClick = { cancelId = null }) { Text("Conservar cita") } }
        )
    }
}

@Composable
private fun OpenAgendaButton(upcomingCount: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = colors.surface, contentColor = colors.primary) {
                Icon(Icons.Outlined.EventAvailable, contentDescription = null, modifier = Modifier.padding(10.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Ver citas agendadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (upcomingCount > 0) {
                    Text(
                        if (upcomingCount == 1) "1 cita próxima" else "$upcomingCount citas próximas",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun AppointmentMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isError) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun AppointmentSyncNotice(
    syncState: AppointmentSyncState,
    isWorkshop: Boolean,
    hasAppointments: Boolean,
    isProcessing: Boolean,
    onRefresh: () -> Unit
) {
    val isDeviceAgenda = BuildConfig.EDMOTOR_PRIVATE_MODE
    when (syncState) {
        AppointmentSyncState.NOT_CONFIGURED -> Text(
            if (isDeviceAgenda) "Inicia sesión para consultar las citas de este dispositivo."
            else "Entra con un perfil conectado a la agenda para gestionar citas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppointmentSyncState.LOADING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        AppointmentSyncState.UNAVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    isDeviceAgenda -> "No se pudieron consultar las citas de este dispositivo."
                    !isWorkshop -> "No se pudo consultar el historial. Puedes intentar solicitar una cita."
                    hasAppointments -> "Los datos de la agenda pueden estar desactualizados."
                    else -> "No se pudo consultar la agenda."
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            if (!isDeviceAgenda) TextButton(onClick = onRefresh) { Text("Reintentar") }
        }
        AppointmentSyncState.UP_TO_DATE -> if (!isDeviceAgenda) {
            TextButton(onClick = onRefresh, enabled = !isProcessing) { Text("Actualizar citas") }
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: Appointment,
    isWorkshop: Boolean,
    canManage: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (statusLabel, statusContainer, statusColor) = when (appointment.status) {
        AppointmentStatus.CONFIRMED -> Triple("Confirmada", colors.primaryContainer, colors.onPrimaryContainer)
        AppointmentStatus.PENDING -> Triple("Pendiente", colors.secondaryContainer, colors.onSecondaryContainer)
        AppointmentStatus.REJECTED -> Triple("Rechazada", colors.surfaceVariant, colors.onSurfaceVariant)
        AppointmentStatus.CANCELLED -> Triple("Cancelada", colors.surfaceVariant, colors.onSurfaceVariant)
        else -> Triple(appointment.status, colors.surfaceVariant, colors.onSurfaceVariant)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    appointment.description,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(shape = CircleShape, color = statusContainer, contentColor = statusColor) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                formatAppointmentDateTime(appointment.scheduledAtMillis),
                color = colors.primary,
                fontWeight = FontWeight.Bold
            )
            if (isWorkshop) {
                Text(appointment.clientName.ifBlank { "Cliente" }, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                listOf(
                    "${appointment.vehicleBrand} ${appointment.vehicleModel}",
                    appointment.vehicleYear.toString(),
                    appointment.vehiclePlate
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )

            if (appointment.canBeChanged()) {
                val requiresReview = isWorkshop && appointment.status == AppointmentStatus.PENDING
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (requiresReview) {
                        Button(onClick = onAccept, enabled = canManage) { Text("Confirmar") }
                        OutlinedButton(onClick = onReject, enabled = canManage) { Text("Rechazar") }
                    }
                    OutlinedButton(onClick = onReschedule, enabled = canManage) {
                        Text(if (requiresReview) "Ajustar fecha y hora" else "Reagendar")
                    }
                    TextButton(onClick = onCancel, enabled = canManage) { Text("Cancelar cita") }
                }
            }
        }
    }
}

@Composable
internal fun EmptyAppointmentsMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(message, modifier = Modifier.padding(18.dp))
    }
}

private fun Appointment.canBeChanged(): Boolean = AppointmentStatus.isActive(status)

internal fun formatAppointmentDateTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")).format(Date(millis))
