package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val UTC_DAY_MILLIS = 24 * 60 * 60_000L
private val calendarLocale = Locale("es", "CO")
private val utc = TimeZone.getTimeZone("UTC")
private val weekdayLabels = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

@Composable
internal fun AppointmentMonthCalendar(
    selectedDay: Long,
    today: Long,
    appointmentsByDay: Map<Long, List<Appointment>>,
    onSelectDay: (Long) -> Unit
) {
    val monthStart = remember(selectedDay) {
        utcCalendar(selectedDay).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    }
    val monthTitle = remember(monthStart) { formatUtcDay("MMMM yyyy", monthStart) }
    val weeks = remember(monthStart) {
        val month = utcCalendar(monthStart)
        val leadingDays = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        (0 until ((leadingDays + daysInMonth + 6) / 7) * 7).chunked(7).map { week ->
            week.map { position ->
                val dayIndex = position - leadingDays
                if (dayIndex in 0 until daysInMonth) monthStart + dayIndex * UTC_DAY_MILLIS else null
            }
        }
    }
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelectDay(moveMonth(monthStart, -1)) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mes anterior")
                }
                Text(
                    monthTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { onSelectDay(moveMonth(monthStart, 1)) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Mes siguiente")
                }
                IconButton(onClick = { onSelectDay(today) }) {
                    Icon(Icons.Outlined.Today, contentDescription = "Ir a hoy")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                weekdayLabels.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        week.forEach { day ->
                            if (day == null) {
                                Box(Modifier.weight(1f))
                            } else {
                                val appointments = appointmentsByDay[day].orEmpty()
                                AppointmentCalendarDay(
                                    day = day,
                                    today = day == today,
                                    selected = day == selectedDay,
                                    hasActiveAppointments = appointments.any { AppointmentStatus.isActive(it.status) },
                                    count = appointments.size,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectDay(day) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentCalendarDay(
    day: Long,
    today: Boolean,
    selected: Boolean,
    hasActiveAppointments: Boolean,
    count: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dayNumber = remember(day) { utcCalendar(day).get(Calendar.DAY_OF_MONTH).toString() }
    val dateLabel = remember(day) { formatUtcDay("d 'de' MMMM 'de' yyyy", day) }
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = dateLabel
            stateDescription = when (count) {
                0 -> "Sin citas"
                1 -> "1 cita"
                else -> "$count citas"
            }
        },
        shape = RoundedCornerShape(10.dp),
        color = when {
            selected -> colors.primary
            hasActiveAppointments -> colors.primaryContainer
            else -> colors.surface
        },
        contentColor = when {
            selected -> colors.onPrimary
            hasActiveAppointments -> colors.onPrimaryContainer
            else -> colors.onSurface
        },
        border = if (today && !selected) BorderStroke(1.dp, colors.primary) else null
    ) {
        Column(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
        ) {
            Text(
                dayNumber,
                modifier = Modifier.semantics { hideFromAccessibility() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected || today || hasActiveAppointments) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            if (count > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveAppointments) colors.secondaryContainer else colors.surfaceVariant,
                    contentColor = if (hasActiveAppointments) colors.onSecondaryContainer else colors.onSurfaceVariant
                ) {
                    Text(
                        count.toString(),
                        modifier = Modifier.padding(horizontal = 3.dp).semantics { hideFromAccessibility() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

internal fun formatAppointmentCalendarDay(day: Long): String = formatUtcDay("EEEE d 'de' MMMM 'de' yyyy", day)

private fun moveMonth(monthStart: Long, offset: Int): Long = utcCalendar(monthStart).apply {
    add(Calendar.MONTH, offset)
}.timeInMillis

private fun utcCalendar(millis: Long): Calendar = Calendar.getInstance(utc).apply {
    timeInMillis = millis
}

private fun formatUtcDay(pattern: String, millis: Long): String = SimpleDateFormat(pattern, calendarLocale).apply {
    timeZone = utc
}.format(Date(millis)).replaceFirstChar { it.titlecase(calendarLocale) }
