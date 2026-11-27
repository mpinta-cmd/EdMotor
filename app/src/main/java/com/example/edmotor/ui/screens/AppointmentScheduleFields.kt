package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentServiceType
import com.example.edmotor.model.AppointmentSlot
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.Vehicle
import com.example.edmotor.model.appointmentDateUtcMillis
import com.example.edmotor.model.appointmentTimeSlots
import com.example.edmotor.model.firstAppointmentDateUtcMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun AppointmentRequestForm(
    vehicles: List<Vehicle>,
    nowMillis: Long,
    isProcessing: Boolean,
    timeZone: TimeZone = TimeZone.getDefault(),
    onOpenGarage: (() -> Unit)? = null,
    onCreateAppointment: (Vehicle, Long, String) -> Unit
) {
    var vehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    var service by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDate by rememberSaveable(timeZone.id) { mutableStateOf(firstAppointmentDateUtcMillis(nowMillis, timeZone)) }
    var selectedTime by rememberSaveable { mutableStateOf<String?>(null) }
    val minimumDate = firstAppointmentDateUtcMillis(nowMillis, timeZone)
    val selectedVehicle = vehicles.firstOrNull { it.id == vehicleId } ?: vehicles.firstOrNull()
    val slots = remember(selectedDate, nowMillis, timeZone) { appointmentTimeSlots(selectedDate, nowMillis, timeZone) }
    val selectedSlot = if (selectedTime == null) slots.firstOrNull() else slots.firstOrNull { it.time == selectedTime }
    val availableService = service?.takeIf(AppointmentServiceType.options::contains)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Datos de la cita",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            AppointmentOptionMenu(
                label = selectedVehicle?.let(::vehicleLabel) ?: "Seleccionar vehículo",
                options = vehicles,
                enabled = !isProcessing,
                labelFor = ::vehicleLabel,
                onSelect = { vehicleId = it.id },
                leadingIcon = { Icon(Icons.Outlined.DirectionsCar, contentDescription = null) }
            )
            if (vehicles.isEmpty()) {
                Text(
                    "Registra un vehículo en Mi Garaje para solicitar una cita.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (onOpenGarage != null) {
                    OutlinedButton(onClick = onOpenGarage, enabled = !isProcessing) {
                        Text("Ir a Mi Garaje")
                    }
                }
            }
            AppointmentOptionMenu(
                label = availableService ?: "Seleccionar tipo de servicio",
                options = AppointmentServiceType.options,
                enabled = !isProcessing,
                labelFor = { it },
                onSelect = { service = it }
            )
            AppointmentDateButton(
                date = selectedDate,
                minimumDate = minimumDate,
                enabled = !isProcessing,
                onSelect = { newDate ->
                    selectedDate = newDate
                    selectedTime = null
                }
            )
            AppointmentTimeMenu(
                slots = slots,
                selectedTime = selectedSlot?.time,
                enabled = !isProcessing,
                onSelect = { selectedTime = it }
            )
            if (selectedSlot == null) {
                Text(
                    if (slots.isEmpty()) "Elige otra fecha para encontrar horarios."
                    else "Elige una hora con al menos 15 minutos de anticipación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                enabled = !isProcessing && selectedVehicle != null && availableService != null && selectedSlot != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (selectedVehicle != null && availableService != null && selectedSlot != null) {
                        onCreateAppointment(selectedVehicle, selectedSlot.startsAtMillis, availableService)
                    }
                }
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text("Solicitar cita")
            }
        }
    }
}

@Composable
internal fun AppointmentRescheduleDialog(
    appointment: Appointment,
    isWorkshop: Boolean,
    nowMillis: Long,
    enabled: Boolean,
    timeZone: TimeZone = TimeZone.getDefault(),
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    val originalStart = appointment.scheduledAtMillis
    val originalDate = appointmentDateUtcMillis(originalStart, timeZone)
    val minimumDate = firstAppointmentDateUtcMillis(nowMillis, timeZone)
    var selectedDate by rememberSaveable(appointment.id, originalStart, timeZone.id) {
        mutableStateOf(maxOf(originalDate, minimumDate))
    }
    var selectedTime by rememberSaveable(appointment.id, originalStart) { mutableStateOf<String?>(null) }
    var showCalendar by rememberSaveable(appointment.id) { mutableStateOf(false) }
    val slots = remember(selectedDate, nowMillis, timeZone) { appointmentTimeSlots(selectedDate, nowMillis, timeZone) }
    val chosenTime = selectedTime
    val selectedSlot = if (chosenTime == null) {
        slots.firstOrNull { it.startsAtMillis == originalStart }
    } else {
        slots.firstOrNull { it.time == chosenTime }
    }
    val newStart = selectedSlot?.startsAtMillis?.takeIf { it != originalStart }

    if (showCalendar) {
        AppointmentDatePicker(
            date = selectedDate,
            minimumDate = minimumDate,
            onDismiss = { showCalendar = false },
            onSelect = { newDate ->
                if (newDate != selectedDate) {
                    selectedDate = newDate
                    selectedTime = null
                }
            }
        )
    } else {
        AlertDialog(
            title = { Text(if (isWorkshop && appointment.status == AppointmentStatus.PENDING) "Ajustar fecha y hora" else "Reagendar cita") },
            onDismissRequest = onDismiss,
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Horario actual: ${formatAppointmentDateTime(appointment.scheduledAtMillis, timeZone)}")
                    if (!isWorkshop && appointment.status == AppointmentStatus.CONFIRMED) {
                        Text(
                            "EdMotor revisará el nuevo horario antes de confirmar la cita.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DateButton(selectedDate, enabled, onClick = { showCalendar = true })
                    AppointmentTimeMenu(slots, selectedSlot?.time, enabled) { selectedTime = it }
                    if (selectedSlot == null) {
                        Text(
                            if (slots.isEmpty()) "Elige otra fecha para encontrar horarios."
                            else "Elige un horario con al menos 15 minutos de anticipación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = enabled && newStart != null,
                    onClick = {
                        if (newStart != null && appointmentTimeSlots(selectedDate, System.currentTimeMillis(), timeZone)
                                .any { it.startsAtMillis == newStart }
                        ) {
                            onSave(newStart)
                        }
                    }
                ) { Text("Guardar horario") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } }
        )
    }
}

@Composable
private fun AppointmentDateButton(
    date: Long,
    minimumDate: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit
) {
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    DateButton(date, enabled, onClick = { showCalendar = true })
    if (showCalendar) {
        AppointmentDatePicker(date, minimumDate, onDismiss = { showCalendar = false }, onSelect = onSelect)
    }
}

@Composable
private fun DateButton(date: Long, enabled: Boolean, onClick: () -> Unit) {
    val formattedDate = remember(date) {
        SimpleDateFormat("EEEE, dd 'de' MMMM yyyy", Locale("es", "CO")).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(date))
    }
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(formattedDate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppointmentDatePicker(
    date: Long,
    minimumDate: Long? = null,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val selectableDates = remember(minimumDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = minimumDate == null || utcTimeMillis >= minimumDate
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(date, minimumDate ?: date),
        selectableDates = selectableDates
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let(onSelect)
                    onDismiss()
                }
            ) { Text("Elegir fecha") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun AppointmentTimeMenu(
    slots: List<AppointmentSlot>,
    selectedTime: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) = AppointmentOptionMenu(
    label = selectedTime?.let { "Hora: $it" } ?: "Seleccionar hora",
    options = slots,
    enabled = enabled,
    labelFor = { it.time },
    onSelect = { onSelect(it.time) },
    leadingIcon = { Icon(Icons.Outlined.AccessTime, contentDescription = null) }
)

@Composable
private fun <T> AppointmentOptionMenu(
    label: String,
    options: List<T>,
    enabled: Boolean,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.size(8.dp))
            }
            Text(label)
        }
        DropdownMenu(expanded = expanded && enabled && options.isNotEmpty(), onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelFor(option)) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

private fun vehicleLabel(vehicle: Vehicle): String = "${vehicle.brand} ${vehicle.model} · ${vehicle.year}"
