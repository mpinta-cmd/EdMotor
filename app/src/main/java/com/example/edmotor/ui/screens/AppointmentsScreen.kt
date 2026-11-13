package com.example.edmotor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.Vehicle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    isWorkshop: Boolean,
    appointments: List<Appointment>,
    vehicles: List<Vehicle>,
    isProcessing: Boolean,
    message: String?,
    isError: Boolean,
    onCreateAppointment: (Vehicle, Long, String) -> Unit,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onClearMessage: () -> Unit
) {
    val dateFormatter = remember {
        SimpleDateFormat("EEEE, dd 'de' MMMM yyyy", Locale("es", "CO")).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val dateTimeFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO"))
    }

    var selectedVehicleId by rememberSaveable { mutableStateOf(-1) }
    var selectedDateMillis by rememberSaveable {
        mutableStateOf(
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var selectedTime by rememberSaveable { mutableStateOf("09:00") }
    var description by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimeMenu by remember { mutableStateOf(false) }
    var showVehicleMenu by remember { mutableStateOf(false) }

    LaunchedEffect(vehicles) {
        if (selectedVehicleId <= 0 && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().id
        }
    }

    val selectedVehicle = vehicles.firstOrNull { it.id == selectedVehicleId }
    val visibleAppointments = remember(appointments, isWorkshop) {
        if (isWorkshop) {
            appointments.sortedWith(
                compareBy<Appointment>(
                    { it.status != AppointmentStatus.PENDING },
                    { it.scheduledAtMillis }
                )
            )
        } else {
            appointments.sortedByDescending { it.createdAtMillis }
        }
    }
    val timeSlots = remember {
        buildList {
            for (hour in 8..17) {
                add("%02d:00".format(hour))
                if (hour != 17) add("%02d:30".format(hour))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isWorkshop) Modifier.verticalScroll(rememberScrollState())
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isWorkshop) "AGENDA DEL TALLER" else "AGENDA TU CITA",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isWorkshop) "Solicitudes de servicio" else "Reserva una revisión para tu vehículo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = if (isWorkshop) {
                "Consulta las citas por día y horario. Revisa las solicitudes y administra la agenda del taller."
            } else {
                "Selecciona tu vehículo, el día, la hora y cuéntanos qué está sucediendo."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        if (!message.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.weight(1f),
                        color = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                    TextButton(onClick = onClearMessage) { Text("Cerrar") }
                }
            }
        }

        if (isWorkshop) {
            WorkshopDailyAgenda(
                appointments = visibleAppointments,
                isProcessing = isProcessing,
                onAccept = onAccept,
                onReject = onReject,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "DATOS DE LA CITA",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { showVehicleMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = vehicles.isNotEmpty()
                                ) {
                                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        selectedVehicle?.let { vehicle ->
                                            "${vehicle.brand} ${vehicle.model} · ${vehicle.plate.ifBlank { vehicle.year.toString() }}"
                                        } ?: "Seleccionar vehículo"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showVehicleMenu,
                                    onDismissRequest = { showVehicleMenu = false }
                                ) {
                                    vehicles.forEach { vehicle ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${vehicle.brand} ${vehicle.model} · ${vehicle.year}")
                                            },
                                            onClick = {
                                                selectedVehicleId = vehicle.id
                                                showVehicleMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (vehicles.isEmpty()) {
                                Text(
                                    "Primero registra un vehículo en Mi Garaje para poder solicitar una cita.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(dateFormatter.format(Date(selectedDateMillis)))
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { showTimeMenu = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.AccessTime, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Hora: $selectedTime")
                                }
                                DropdownMenu(
                                    expanded = showTimeMenu,
                                    onDismissRequest = { showTimeMenu = false }
                                ) {
                                    timeSlots.forEach { slot ->
                                        DropdownMenuItem(
                                            text = { Text(slot) },
                                            onClick = {
                                                selectedTime = slot
                                                showTimeMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it.take(600) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("¿Qué le sucede al vehículo?") },
                                placeholder = {
                                    Text("Ej. Hace un ruido al frenar, vibra al acelerar...")
                                },
                                minLines = 3,
                                maxLines = 5
                            )

                            Text(
                                "${description.length}/600 caracteres",
                                modifier = Modifier.align(Alignment.End),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )

                            Button(
                                onClick = {
                                    selectedVehicle?.let { vehicle ->
                                        val scheduledAt = buildLocalDateTime(
                                            selectedDateMillis,
                                            selectedTime
                                        )
                                        onCreateAppointment(vehicle, scheduledAt, description)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isProcessing &&
                                        selectedVehicle != null &&
                                        description.trim().length >= 8
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.padding(4.dp))
                                }
                                Text("Solicitar cita")
                            }
                        }
                    }
                }

                item {
                    Text(
                        "MIS SOLICITUDES",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (appointments.isEmpty()) {
                    item {
                        EmptyAppointmentsMessage(
                            "Aquí aparecerán tus solicitudes y el estado de cada cita."
                        )
                    }
                } else {
                    items(visibleAppointments, key = { it.id }) { appointment ->
                        AppointmentCard(
                            appointment = appointment,
                            isWorkshop = false,
                            isProcessing = isProcessing,
                            dateTimeFormatter = dateTimeFormatter,
                            onAccept = {},
                            onReject = {}
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    return utcTimeMillis >= today
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateMillis =
                            datePickerState.selectedDateMillis ?: selectedDateMillis
                        showDatePicker = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkshopDailyAgenda(
    appointments: List<Appointment>,
    isProcessing: Boolean,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = remember { Locale("es", "CO") }
    val monthFormatter = remember { SimpleDateFormat("MMMM yyyy", locale) }
    val weekdayFormatter = remember { SimpleDateFormat("EEE", locale) }
    val selectedDateFormatter = remember { SimpleDateFormat("EEEE d 'de' MMMM", locale) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", locale) }

    var selectedDayMillis by rememberSaveable {
        mutableStateOf(startOfLocalDay(System.currentTimeMillis()))
    }
    var showCalendar by rememberSaveable { mutableStateOf(false) }

    val weekDays = remember(selectedDayMillis) {
        val monday = startOfLocalWeek(selectedDayMillis)
        (0..6).map { addLocalDays(monday, it) }
    }
    val dayAppointments = remember(appointments, selectedDayMillis) {
        appointments
            .filter { isSameLocalDay(it.scheduledAtMillis, selectedDayMillis) }
            .sortedBy { it.scheduledAtMillis }
    }
    val pendingCount = appointments.count { it.status == AppointmentStatus.PENDING }
    val confirmedCount = appointments.count { it.status == AppointmentStatus.CONFIRMED }
    val selectedMonth = monthFormatter.format(Date(selectedDayMillis))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "AGENDA DEL TALLER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "${appointments.size} ${if (appointments.size == 1) "cita registrada" else "citas registradas"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = "Calendario de citas",
                        modifier = Modifier.padding(11.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    selectedMonth,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Selecciona un día para ver sus horarios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { selectedDayMillis = addLocalDays(selectedDayMillis, -7) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Semana anterior")
            }
            IconButton(onClick = { showCalendar = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Elegir fecha")
            }
            IconButton(onClick = { selectedDayMillis = startOfLocalDay(System.currentTimeMillis()) }) {
                Icon(Icons.Outlined.Today, contentDescription = "Ir a hoy")
            }
            IconButton(onClick = { selectedDayMillis = addLocalDays(selectedDayMillis, 7) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Semana siguiente")
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weekDays, key = { it }) { dayMillis ->
                val isSelected = isSameLocalDay(dayMillis, selectedDayMillis)
                val dayCount = appointments.count { isSameLocalDay(it.scheduledAtMillis, dayMillis) }
                Surface(
                    onClick = { selectedDayMillis = dayMillis },
                    modifier = Modifier.width(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = if (isSelected) null else BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            weekdayFormatter.format(Date(dayMillis))
                                .replace(".", "")
                                .uppercase(locale)
                                .take(3),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            Calendar.getInstance().apply { timeInMillis = dayMillis }
                                .get(Calendar.DAY_OF_MONTH).toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (dayCount > 0) {
                            Text(
                                dayCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AgendaSummaryPill(
                label = "Pendientes",
                count = pendingCount,
                containerColor = Color(0xFFFFF1D6),
                contentColor = Color(0xFF8A5A00),
                modifier = Modifier.weight(1f)
            )
            AgendaSummaryPill(
                label = "Confirmadas",
                count = confirmedCount,
                containerColor = Color(0xFFE0F3E8),
                contentColor = Color(0xFF176B43),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Citas del día",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    selectedDateFormatter.format(Date(selectedDayMillis))
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "${dayAppointments.size}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (dayAppointments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyAppointmentsMessage("No hay citas para este día. Selecciona otra fecha para consultar la agenda.")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                dayAppointments.forEach { appointment ->
                    WorkshopAppointmentTimelineItem(
                        appointment = appointment,
                        timeLabel = timeFormatter.format(Date(appointment.scheduledAtMillis)),
                        isProcessing = isProcessing,
                        onAccept = { onAccept(appointment.id) },
                        onReject = { onReject(appointment.id) }
                    )
                }
            }
        }
    }

    if (showCalendar) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDayToUtcDateMillis(selectedDayMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            selectedDayMillis = utcDateToLocalDayMillis(picked)
                        }
                        showCalendar = false
                    }
                ) { Text("Ver día") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun AgendaSummaryPill(
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun WorkshopAppointmentTimelineItem(
    appointment: Appointment,
    timeLabel: String,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val statusColor = when (appointment.status) {
        AppointmentStatus.CONFIRMED -> Color(0xFF238653)
        AppointmentStatus.REJECTED -> MaterialTheme.colorScheme.error
        else -> Color(0xFFD28A16)
    }
    val statusBackground = when (appointment.status) {
        AppointmentStatus.CONFIRMED -> Color(0xFFE0F3E8)
        AppointmentStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
        else -> Color(0xFFFFF1D6)
    }
    val statusLabel = when (appointment.status) {
        AppointmentStatus.CONFIRMED -> "CONFIRMADA"
        AppointmentStatus.REJECTED -> "RECHAZADA"
        else -> "PENDIENTE"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusBackground,
                contentColor = statusColor
            ) {
                Text(
                    timeLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(3.dp)
                    .height(92.dp)
                    .background(statusColor.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            )
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        appointment.clientName.ifBlank { "Cliente" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Surface(shape = RoundedCornerShape(50), color = statusBackground) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "Fecha y hora: " + SimpleDateFormat(
                        "EEEE, dd 'de' MMMM yyyy · HH:mm",
                        Locale("es", "CO")
                    ).format(Date(appointment.scheduledAtMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Vehículo: ${appointment.vehicleBrand} ${appointment.vehicleModel} · ${appointment.vehicleYear}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (appointment.vehiclePlate.isNotBlank()) {
                    Text(
                        "Placa: ${appointment.vehiclePlate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Usuario: @${appointment.clientUsername}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Teléfono: ${appointment.clientPhone ?: "Sin teléfono registrado"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Descripción del servicio:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    appointment.description.ifBlank { "Sin descripción proporcionada." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (appointment.status == AppointmentStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAccept,
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) { Text("Confirmar") }
                        OutlinedButton(
                            onClick = onReject,
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) { Text("Rechazar") }
                    }
                }
            }
        }
    }
}

private fun startOfLocalDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfLocalWeek(millis: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun addLocalDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    add(Calendar.DAY_OF_MONTH, days)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun isSameLocalDay(firstMillis: Long, secondMillis: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = firstMillis }
    val second = Calendar.getInstance().apply { timeInMillis = secondMillis }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun localDayToUtcDateMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
    }.timeInMillis
}

private fun utcDateToLocalDayMillis(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
    }.timeInMillis
}

@Composable
private fun AppointmentCard(
    appointment: Appointment,
    isWorkshop: Boolean,
    isProcessing: Boolean,
    dateTimeFormatter: SimpleDateFormat,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val statusText = when (appointment.status) {
        AppointmentStatus.PENDING -> "PENDIENTE"
        AppointmentStatus.CONFIRMED -> "CONFIRMADA"
        AppointmentStatus.REJECTED -> "RECHAZADA"
        else -> appointment.status
    }
    val statusColor = when (appointment.status) {
        AppointmentStatus.CONFIRMED -> MaterialTheme.colorScheme.tertiary
        AppointmentStatus.REJECTED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cita #${appointment.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                dateTimeFormatter.format(Date(appointment.scheduledAtMillis)),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${appointment.vehicleBrand} ${appointment.vehicleModel} · ${appointment.vehicleYear}",
                fontWeight = FontWeight.SemiBold
            )
            if (appointment.vehiclePlate.isNotBlank()) {
                Text("Placa: ${appointment.vehiclePlate}")
            }

            if (isWorkshop) {
                Text("CLIENTE", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(appointment.clientName, fontWeight = FontWeight.SemiBold)
                Text("@${appointment.clientUsername}")
                Text("Teléfono: ${appointment.clientPhone ?: "No registrado"}")
            }

            Text(
                "Descripción del problema",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                appointment.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isWorkshop && appointment.status == AppointmentStatus.PENDING) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAccept,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aceptar cita")
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rechazar solicitud")
                }
            }
        }
    }
}

@Composable
private fun EmptyAppointmentsMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            message,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun buildLocalDateTime(dateUtcMillis: Long, time: String): Long {
    val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateUtcMillis
    }
    val pieces = time.split(":")
    val hour = pieces.getOrNull(0)?.toIntOrNull() ?: 9
    val minute = pieces.getOrNull(1)?.toIntOrNull() ?: 0

    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
        set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
