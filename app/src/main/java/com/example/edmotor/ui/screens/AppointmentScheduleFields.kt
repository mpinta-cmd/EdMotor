package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun AppointmentRequestForm(
    vehicles: List<Vehicle>,
    nowMillis: Long,
    isProcessing: Boolean,
    onCreateAppointment: (Vehicle, Long, String) -> Unit
) {
    var vehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    var service by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDate by rememberSaveable { mutableStateOf(firstAppointmentDateUtcMillis(nowMillis)) }
    var selectedTime by rememberSaveable { mutableStateOf<String?>(null) }
    val minimumDate = firstAppointmentDateUtcMillis(nowMillis)
    val selectedVehicle = vehicles.firstOrNull { it.id == vehicleId } ?: vehicles.firstOrNull()
    val slots = remember(selectedDate, nowMillis) { appointmentTimeSlots(selectedDate, nowMillis) }
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
                    val checkedSlot = appointmentTimeSlots(selectedDate, System.currentTimeMillis())
                        .firstOrNull { it.time == selectedSlot?.time }
                    if (selectedVehicle != null && availableService != null && checkedSlot != null) {
                        onCreateAppointment(selectedVehicle, checkedSlot.startsAtMillis, availableService)
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
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    val originalDate = appointmentDateUtcMillis(appointment.scheduledAtMillis)
    val minimumDate = if (isWorkshop) appointmentDateUtcMillis(nowMillis) else firstAppointmentDateUtcMillis(nowMillis)
    val originalTime = remember(appointment.scheduledAtMillis) {
        Calendar.getInstance().apply { timeInMillis = appointment.scheduledAtMillis }
    }
    var selectedDate by rememberSaveable(appointment.id) { mutableStateOf(maxOf(originalDate, minimumDate)) }
    var hour by rememberSaveable(appointment.id) {
        mutableStateOf(originalTime.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0'))
    }
    var minute by rememberSaveable(appointment.id) {
        mutableStateOf(originalTime.get(Calendar.MINUTE).toString().padStart(2, '0'))
    }
    var showCalendar by rememberSaveable(appointment.id) { mutableStateOf(false) }
    val slots = if (isWorkshop) emptyList() else remember(selectedDate, nowMillis) {
        appointmentTimeSlots(selectedDate, nowMillis)
    }
    var selectedTime by rememberSaveable(appointment.id) {
        mutableStateOf(slots.firstOrNull { it.time == "$hour:$minute" }?.time)
    }
    val selectedSlot = slots.firstOrNull { it.time == selectedTime }
    val proposedTime = if (isWorkshop) selectedAppointmentStart(selectedDate, hour, minute) else selectedSlot?.startsAtMillis
    val canSave = enabled && proposedTime != null && proposedTime > nowMillis && proposedTime != appointment.scheduledAtMillis

    if (showCalendar) {
        AppointmentDatePicker(
            date = selectedDate,
            minimumDate = minimumDate,
            onDismiss = { showCalendar = false },
            onSelect = {
                selectedDate = it
                selectedTime = null
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
                    Text("Horario actual: ${formatAppointmentDateTime(appointment.scheduledAtMillis)}")
                    if (!isWorkshop && appointment.status == AppointmentStatus.CONFIRMED) {
                        Text(
                            "EdMotor revisará el nuevo horario antes de confirmar la cita.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DateButton(selectedDate, enabled, onClick = { showCalendar = true })
                    if (isWorkshop) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppointmentTimeField(hour, "Hora", Modifier.weight(1f), enabled) { hour = it }
                            AppointmentTimeField(minute, "Minutos", Modifier.weight(1f), enabled) { minute = it }
                        }
                        Text(
                            when {
                                proposedTime == null -> "Escribe una hora de 00 a 23 y minutos de 00 a 59."
                                proposedTime <= nowMillis -> "Selecciona una fecha y una hora futuras."
                                else -> "Formato de 24 horas"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AppointmentTimeMenu(slots, selectedSlot?.time, enabled) { selectedTime = it }
                        if (selectedSlot == null) {
                            Text(
                                "Elige un horario con al menos 15 minutos de anticipación.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = canSave,
                    onClick = {
                        val checkedAt = System.currentTimeMillis()
                        if (proposedTime != null && proposedTime > checkedAt &&
                            (isWorkshop || appointmentTimeSlots(selectedDate, checkedAt).any { it.startsAtMillis == proposedTime })
                        ) {
                            onSave(proposedTime)
                        }
                    }
                ) { Text("Guardar horario") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } }
        )
    }
}

@Composable
private fun AppointmentTimeField(
    value: String,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { char -> char in '0'..'9' }.take(2)) },
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

private fun selectedAppointmentStart(date: Long, hour: String, minute: String): Long? {
    val parsedHour = hour.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val parsedMinute = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date }
    return runCatching {
        Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(utcDate.get(Calendar.YEAR), utcDate.get(Calendar.MONTH), utcDate.get(Calendar.DAY_OF_MONTH), parsedHour, parsedMinute)
        }.timeInMillis
    }.getOrNull()
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
