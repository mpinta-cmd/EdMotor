package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.appointmentDateUtcMillis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val UTC_DAY_MS = 24 * 60 * 60_000L

@Composable
internal fun WorkshopAppointmentAgenda(
    appointments: List<Appointment>,
    nowMillis: Long,
    showEmptyDay: Boolean,
    appointmentContent: @Composable (Appointment) -> Unit
) {
    val today = appointmentDateUtcMillis(nowMillis)
    var selectedDay by rememberSaveable {
        val firstUpcoming = appointments.firstOrNull {
            it.status == AppointmentStatus.CONFIRMED && it.scheduledAtMillis >= nowMillis
        }
        mutableStateOf(firstUpcoming?.let { appointmentDateUtcMillis(it.scheduledAtMillis) } ?: today)
    }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val appointmentsByDay = remember(appointments) {
        appointments.groupBy { appointmentDateUtcMillis(it.scheduledAtMillis) }
    }
    val weekDays = remember(selectedDay) {
        val daysAfterMonday = (utcCalendar(selectedDay).get(Calendar.DAY_OF_WEEK) + 5) % 7
        val monday = selectedDay - daysAfterMonday * UTC_DAY_MS
        (0..6).map { monday + it * UTC_DAY_MS }
    }
    val dayAppointments = appointmentsByDay[selectedDay].orEmpty()
    val longDate = remember(selectedDay) { formatUtcDay("EEEE d 'de' MMMM yyyy", selectedDay) }
    val colors = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatUtcDay("MMMM yyyy", selectedDay),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { selectedDay -= 7 * UTC_DAY_MS }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Semana anterior")
            }
            IconButton(onClick = { showCalendar = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Elegir otro día")
            }
            IconButton(onClick = { selectedDay = today }) {
                Icon(Icons.Outlined.Today, contentDescription = "Ir a hoy")
            }
            IconButton(onClick = { selectedDay += 7 * UTC_DAY_MS }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Semana siguiente")
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weekDays, key = { it }) { day ->
                val selected = day == selectedDay
                val count = appointmentsByDay[day].orEmpty().count { it.status == AppointmentStatus.CONFIRMED }
                Surface(
                    selected = selected,
                    onClick = { selectedDay = day },
                    modifier = Modifier.widthIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) colors.primary else colors.surfaceVariant,
                    contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    border = if (selected) null else BorderStroke(1.dp, colors.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(formatUtcDay("EEE", day), style = MaterialTheme.typography.labelSmall)
                        Text(
                            utcCalendar(day).get(Calendar.DAY_OF_MONTH).toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(if (count > 0) "$count" else "–", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(longDate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (dayAppointments.isEmpty() && showEmptyDay) {
            EmptyAppointmentsMessage("No hay citas para este día. Elige otra fecha para consultar la agenda.")
        } else {
            dayAppointments.forEach { appointment -> appointmentContent(appointment) }
        }
    }

    if (showCalendar) {
        AppointmentDatePicker(date = selectedDay, onDismiss = { showCalendar = false }, onSelect = { selectedDay = it })
    }
}

private fun utcCalendar(millis: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    timeInMillis = millis
}

private fun formatUtcDay(pattern: String, millis: Long): String {
    val locale = Locale("es", "CO")
    return SimpleDateFormat(pattern, locale).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis)).replaceFirstChar { it.titlecase(locale) }
}
