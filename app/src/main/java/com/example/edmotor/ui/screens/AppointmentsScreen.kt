package com.example.edmotor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.BuildConfig
import com.example.edmotor.data.local.AppointmentWorkWithMaintenance
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.Vehicle
import com.example.edmotor.model.appointmentDateUtcMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class AppointmentSection { REQUESTS, CALENDAR, ALL }

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
    onCancel: (Appointment) -> Unit,
    onReschedule: (Long, Long) -> Unit,
    onClearMessage: () -> Unit,
    onRefresh: () -> Unit,
    appointmentWork: List<AppointmentWorkWithMaintenance> = emptyList(),
    onFinish: (Appointment) -> Unit = {},
    onRegisterMaintenance: (Appointment) -> Unit = {},
    onViewMaintenance: ((Int) -> Unit)? = null,
    requestedCalendarDay: Long? = null,
    timeZone: TimeZone = TimeZone.getDefault(),
    onCalendarDayHandled: () -> Unit = {}
) {
    var selectedSection by rememberSaveable(isWorkshop) { mutableStateOf(AppointmentSection.REQUESTS) }
    var rescheduleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingCancel by remember(isWorkshop) { mutableStateOf<Appointment?>(null) }
    var pendingFinish by remember(isWorkshop) { mutableStateOf<Appointment?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val today = remember(nowMillis, timeZone) { appointmentDateUtcMillis(nowMillis, timeZone) }
    var selectedDay by rememberSaveable(isWorkshop, timeZone.id) { mutableLongStateOf(requestedCalendarDay ?: today) }
    val requestListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()
    val allListState = rememberLazyListState()
    val tabStateHolder = rememberSaveableStateHolder()
    val coroutineScope = rememberCoroutineScope()
    val isConfigured = syncState != AppointmentSyncState.NOT_CONFIGURED
    val canManage = syncState == AppointmentSyncState.UP_TO_DATE && !isProcessing
    val colors = MaterialTheme.colorScheme
    val orderedAppointments = remember(appointments) {
        appointments.filter { it.status != AppointmentStatus.CANCELLED }
            .sortedWith(compareBy({ it.scheduledAtMillis }, { it.id }))
    }
    val upcoming = remember(orderedAppointments, nowMillis) {
        orderedAppointments.filter { it.canBeChanged() && it.scheduledAtMillis >= nowMillis }
    }
    val nextAppointment = upcoming.firstOrNull()
    val activeAppointment = if (isWorkshop) null else nextAppointment ?: orderedAppointments.firstOrNull { it.canBeChanged() }
    val requestTabAvailable = isWorkshop || (activeAppointment == null && syncState != AppointmentSyncState.LOADING)
    val visibleSection = when {
        requestedCalendarDay != null -> AppointmentSection.CALENDAR
        selectedSection == AppointmentSection.REQUESTS && !requestTabAvailable -> AppointmentSection.CALENDAR
        else -> selectedSection
    }
    val listState = when (visibleSection) {
        AppointmentSection.REQUESTS -> requestListState
        AppointmentSection.CALENDAR -> calendarListState
        AppointmentSection.ALL -> allListState
    }
    val activeTime = activeAppointment?.let { it.identity() to it.scheduledAtMillis }
    var lastActiveTime by remember(isWorkshop, timeZone.id) { mutableStateOf<Pair<Triple<String, Long, Long>, Long>?>(null) }
    var awaitingActiveAfterCalendarLink by remember(isWorkshop) { mutableStateOf(false) }
    val appointmentsByDay = remember(orderedAppointments, timeZone) {
        orderedAppointments.groupBy { appointmentDateUtcMillis(it.scheduledAtMillis, timeZone) }
    }
    val workByAppointment = remember(appointmentWork) {
        appointmentWork.associateBy { (work) ->
            Triple(work.clientAuthId, work.appointmentId, work.appointmentCreatedAtMillis)
        }
    }
    val requests = remember(orderedAppointments) {
        orderedAppointments.filter { it.status == AppointmentStatus.PENDING }
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
    LaunchedEffect(requestedCalendarDay, activeTime, timeZone.id) {
        if (requestedCalendarDay != null) {
            selectedDay = requestedCalendarDay
            selectedSection = AppointmentSection.CALENDAR
            lastActiveTime = activeTime
            awaitingActiveAfterCalendarLink = !isWorkshop && activeTime == null
            calendarListState.scrollToItem(0)
            onCalendarDayHandled()
        } else if (activeTime != lastActiveTime) {
            lastActiveTime = activeTime
            if (activeAppointment != null) {
                // An explicit history link can arrive before the client's appointments load.
                if (!awaitingActiveAfterCalendarLink) {
                    selectedDay = appointmentDateUtcMillis(activeAppointment.scheduledAtMillis, timeZone)
                    if (selectedSection != AppointmentSection.ALL) {
                        selectedSection = AppointmentSection.REQUESTS
                        calendarListState.scrollToItem(0)
                    }
                }
                awaitingActiveAfterCalendarLink = false
            }
        }
    }
    BackHandler(enabled = selectedSection == AppointmentSection.ALL ||
        (selectedSection == AppointmentSection.CALENDAR && requestTabAvailable)
    ) {
        awaitingActiveAfterCalendarLink = false
        selectedSection = AppointmentSection.REQUESTS
    }

    val appointmentContent: @Composable (Appointment) -> Unit = { appointment ->
        val report = workByAppointment[appointment.identity()]?.let { (work, maintenance) ->
            maintenance?.takeIf {
                work.finishedAtMillis == appointment.finishedAtMillis &&
                    it.record.id == work.maintenanceRecordId && it.record.vehicleId == work.vehicleId
            }
        }
        AppointmentCard(
            appointment = appointment,
            isWorkshop = isWorkshop,
            showsReview = isWorkshop && visibleSection != AppointmentSection.CALENDAR,
            canManage = canManage,
            timeZone = timeZone,
            maintenance = report,
            onAccept = { onAccept(appointment.id) },
            onReject = { onReject(appointment.id) },
            onReschedule = { rescheduleId = appointment.id },
            onCancel = { pendingCancel = appointment },
            onFinish = { pendingFinish = appointment },
            onRegisterMaintenance = { onRegisterMaintenance(appointment) },
            onViewMaintenance = onViewMaintenance
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        AppointmentHeader(
            requestLabel = when {
                isWorkshop -> "Solicitudes"
                requestTabAvailable -> "Solicitar"
                else -> null
            },
            selectedSection = visibleSection,
            onSelect = { section ->
                if (section != visibleSection) {
                    if (section == AppointmentSection.REQUESTS) awaitingActiveAfterCalendarLink = false
                    selectedSection = if (section == AppointmentSection.CALENDAR && !requestTabAvailable) {
                        AppointmentSection.REQUESTS
                    } else {
                        section
                    }
                }
            }
        )

        tabStateHolder.SaveableStateProvider(visibleSection.name) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!message.isNullOrBlank()) {
                    item(key = "message") { AppointmentMessage(message, isError, onClearMessage) }
                }
                item(key = "sync") {
                    AppointmentSyncNotice(syncState, isWorkshop, orderedAppointments.isNotEmpty(), isProcessing, onRefresh)
                }

                if (isConfigured && visibleSection == AppointmentSection.CALENDAR) {
                    val highlightedAppointment = nextAppointment ?: activeAppointment
                    if (highlightedAppointment != null || syncState == AppointmentSyncState.UP_TO_DATE) {
                        item(key = "nextAppointment") {
                            val appointmentDay = highlightedAppointment?.let { appointmentDateUtcMillis(it.scheduledAtMillis, timeZone) }
                            UpcomingAppointmentSummary(
                                appointment = highlightedAppointment,
                                isUpcoming = nextAppointment != null,
                                showsItsDay = selectedDay == appointmentDay,
                                timeZone = timeZone,
                                onShowItsDay = {
                                    if (appointmentDay != null) {
                                        selectedDay = appointmentDay
                                        if (!requestTabAvailable) selectedSection = AppointmentSection.REQUESTS
                                    }
                                }
                            )
                        }
                    }
                    item(key = "calendar") {
                        AppointmentMonthCalendar(
                            selectedDay = selectedDay,
                            today = today,
                            appointmentsByDay = appointmentsByDay,
                            onSelectDay = { selectedDay = it }
                        )
                    }
                    val dayAppointments = appointmentsByDay[selectedDay].orEmpty()
                    item(key = "dayHeading") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        formatAppointmentCalendarDay(selectedDay),
                                        modifier = Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (dayAppointments.isNotEmpty()) {
                                        Text(
                                            if (dayAppointments.size == 1) "1 cita" else "${dayAppointments.size} citas",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (dayAppointments.isEmpty() && syncState == AppointmentSyncState.UP_TO_DATE) {
                        item(key = "emptyDay") {
                            EmptyAppointmentsMessage("No hay citas para este día.")
                        }
                    }
                    items(dayAppointments, key = { it.id }) { appointmentContent(it) }
                } else if (isConfigured && visibleSection == AppointmentSection.ALL) {
                    if (orderedAppointments.isEmpty() && syncState == AppointmentSyncState.UP_TO_DATE) {
                        item(key = "emptyAll") { EmptyAppointmentsMessage("Todavía no hay citas registradas.") }
                    }
                    items(orderedAppointments, key = { it.id }) { appointmentContent(it) }
                } else if (isConfigured) {
                    item(key = "intro") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (isWorkshop) "Solicitudes pendientes (${requests.size})" else "Solicita una cita",
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isWorkshop && requests.isNotEmpty()) {
                                Text(
                                    "Revisa las solicitudes y ajusta el horario antes de confirmarlas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OpenAgendaButton(
                                upcomingCount = upcoming.size,
                                onClick = {
                                    selectedDay = nextAppointment?.let { appointmentDateUtcMillis(it.scheduledAtMillis, timeZone) } ?: today
                                    selectedSection = AppointmentSection.CALENDAR
                                    coroutineScope.launch { calendarListState.scrollToItem(0) }
                                }
                            )
                        }
                    }
                    if (isWorkshop) {
                        if (requests.isEmpty() && syncState == AppointmentSyncState.UP_TO_DATE) {
                            item(key = "emptyRequests") {
                                EmptyAppointmentsMessage("No hay solicitudes pendientes de revisar.")
                            }
                        }
                        items(requests, key = { it.id }) { appointmentContent(it) }
                    } else {
                        item(key = "request") {
                            AppointmentRequestForm(
                                vehicles = vehicles,
                                nowMillis = nowMillis,
                                isProcessing = isProcessing,
                                timeZone = timeZone,
                                onCreateAppointment = onCreateAppointment
                            )
                        }
                    }
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
            timeZone = timeZone,
            onDismiss = { rescheduleId = null },
            onSave = { newTime ->
                rescheduleId = null
                onReschedule(appointmentToReschedule.id, newTime)
            }
        )
    }

    val appointmentToCancel = pendingCancel?.let { requested ->
        orderedAppointments.firstOrNull { it.identity() == requested.identity() && it.canBeChanged() }
    }
    if (appointmentToCancel != null) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.EventAvailable, contentDescription = null) },
            title = { Text("Cancelar cita") },
            text = { Text("Al cancelar se eliminará la cita del ${formatAppointmentDateTime(appointmentToCancel.scheduledAtMillis, timeZone)}.") },
            onDismissRequest = { pendingCancel = null },
            confirmButton = {
                Button(
                    enabled = canManage,
                    onClick = {
                        pendingCancel = null
                        onCancel(appointmentToCancel)
                    }
                ) { Text("Confirmar cancelación") }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("Conservar cita") } }
        )
    }

    val appointmentToFinish = pendingFinish?.let { selected ->
        appointments.firstOrNull {
            it.identity() == selected.identity() && it.status == AppointmentStatus.CONFIRMED
        }
    }
    if (isWorkshop && appointmentToFinish != null) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.EventAvailable, contentDescription = null) },
            title = { Text("Terminar la cita") },
            text = { Text("El cliente verá que su vehículo está listo para recoger. Después podrás registrar el mantenimiento realizado.") },
            onDismissRequest = { pendingFinish = null },
            confirmButton = {
                Button(
                    enabled = canManage,
                    onClick = {
                        pendingFinish = null
                        onFinish(appointmentToFinish)
                    }
                ) { Text("Confirmar finalización") }
            },
            dismissButton = { TextButton(onClick = { pendingFinish = null }) { Text("Volver") } }
        )
    }
}

@Composable
private fun AppointmentHeader(
    requestLabel: String?,
    selectedSection: AppointmentSection,
    onSelect: (AppointmentSection) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tabShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = tabShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.padding(9.dp).size(23.dp))
            }
            Text(
                "Citas",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceVariant,
            border = BorderStroke(1.dp, colors.outline)
        ) {
            Row(
                modifier = Modifier.padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOfNotNull(
                    requestLabel?.let { AppointmentSection.REQUESTS to it },
                    AppointmentSection.CALENDAR to "Calendario",
                    AppointmentSection.ALL to "Todas las citas"
                ).forEach { (section, label) ->
                    val selected = selectedSection == section
                    Tab(
                        selected = selected,
                        onClick = { onSelect(section) },
                        modifier = Modifier.weight(if (section == AppointmentSection.ALL) 1.15f else 1f).clip(tabShape)
                            .background(if (selected) colors.primary else colors.surfaceVariant),
                        selectedContentColor = colors.onPrimary,
                        unselectedContentColor = colors.onSurfaceVariant
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingAppointmentSummary(
    appointment: Appointment?,
    isUpcoming: Boolean,
    showsItsDay: Boolean,
    timeZone: TimeZone,
    onShowItsDay: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (appointment != null && !isUpcoming) "Cita activa" else "Próxima cita",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (appointment != null) {
                Text("Horario: ${formatAppointmentDateTime(appointment.scheduledAtMillis, timeZone)}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (appointment.status == AppointmentStatus.PENDING) "Pendiente de confirmación" else "Confirmada por el taller",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!showsItsDay) {
                    TextButton(onClick = onShowItsDay) { Text("Ver día de la cita") }
                }
            } else {
                Text("No hay citas próximas.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun OpenAgendaButton(upcomingCount: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.primary,
        contentColor = colors.onPrimary
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
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
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AppointmentMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isError) colors.errorContainer else colors.primaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
            TextButton(onClick = onRefresh, enabled = !isProcessing) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Actualizar citas", modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: Appointment,
    isWorkshop: Boolean,
    showsReview: Boolean,
    canManage: Boolean,
    timeZone: TimeZone,
    maintenance: MaintenanceWithParts?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    onRegisterMaintenance: () -> Unit,
    onViewMaintenance: ((Int) -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val (statusLabel, statusContainer, statusColor) = when (appointment.status) {
        AppointmentStatus.CONFIRMED -> Triple("Confirmada", colors.primaryContainer, colors.onPrimaryContainer)
        AppointmentStatus.COMPLETED -> Triple("Cita terminada", colors.tertiaryContainer, colors.onTertiaryContainer)
        AppointmentStatus.PENDING -> Triple("Pendiente", colors.secondaryContainer, colors.onSecondaryContainer)
        AppointmentStatus.REJECTED -> Triple("Rechazada", colors.surfaceVariant, colors.onSurfaceVariant)
        else -> Triple(appointment.status, colors.surfaceVariant, colors.onSurfaceVariant)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = statusContainer, contentColor = statusColor) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
                    Text(
                        formatAppointmentDateTime(appointment.scheduledAtMillis, timeZone),
                        color = colors.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                appointment.description,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (isWorkshop) {
                Text(
                    appointment.clientName.ifBlank { "Cliente" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                Text(
                    listOf(
                        "${appointment.vehicleBrand} ${appointment.vehicleModel}",
                        appointment.vehicleYear.toString(),
                        appointment.vehiclePlate
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }

            if (appointment.canBeChanged()) {
                HorizontalDivider(color = colors.outline)
                val requiresReview = showsReview && appointment.status == AppointmentStatus.PENDING
                val actionShape = RoundedCornerShape(12.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isWorkshop && appointment.status == AppointmentStatus.CONFIRMED) {
                        Button(onClick = onFinish, enabled = canManage, shape = actionShape) { Text("Terminar cita") }
                    }
                    if (requiresReview) {
                        Button(onClick = onAccept, enabled = canManage, shape = actionShape) { Text("Confirmar") }
                        OutlinedButton(onClick = onReject, enabled = canManage, shape = actionShape) { Text("Rechazar") }
                    }
                    OutlinedButton(onClick = onReschedule, enabled = canManage, shape = actionShape) {
                        Text(if (requiresReview) "Ajustar fecha y hora" else "Reagendar")
                    }
                    TextButton(onClick = onCancel, enabled = canManage) { Text("Cancelar cita") }
                }
            }

            if (appointment.status == AppointmentStatus.COMPLETED) {
                appointment.finishedAtMillis?.let { finishedAt ->
                    Text(
                        "Terminada el ${formatAppointmentDateTime(finishedAt, timeZone)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                if (!isWorkshop) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.EventAvailable, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("Listo para recoger", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (maintenance != null) {
                    AppointmentMaintenanceSummary(maintenance)
                    if (onViewMaintenance != null) {
                        OutlinedButton(
                            onClick = { onViewMaintenance(maintenance.record.vehicleId) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ver mantenimiento")
                        }
                    }
                } else if (isWorkshop) {
                    Button(onClick = onRegisterMaintenance, enabled = canManage, shape = RoundedCornerShape(12.dp)) {
                        Text("Registrar mantenimiento")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentMaintenanceSummary(maintenance: MaintenanceWithParts) {
    val numbers = remember { NumberFormat.getNumberInstance(Locale("es", "CO")) }
    val colors = MaterialTheme.colorScheme
    val record = maintenance.record
    val parts = maintenance.parts.filter { it.maintenanceRecordId == record.id }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Build, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Text("Mantenimiento realizado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(record.serviceType, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(record.description, style = MaterialTheme.typography.bodyMedium)
            if (parts.isNotEmpty()) {
                Text("Repuestos utilizados", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                parts.forEach { part ->
                    Text(
                        "${part.name} · ${numbers.format(part.quantity)} ${part.unit}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val details = listOf(part.brand, part.reference, part.specification).filter { it.isNotBlank() }
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (record.observations.isNotBlank()) {
                Text("Observaciones: ${record.observations}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptyAppointmentsMessage(message: String) {
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

private fun Appointment.identity() = Triple(clientAuthId, id, createdAtMillis)

internal fun formatAppointmentDateTime(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")).apply { this.timeZone = timeZone }.format(Date(millis))
