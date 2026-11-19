package com.example.edmotor.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal const val APPOINTMENT_MINIMUM_NOTICE_MILLIS = 15 * 60_000L
internal const val APPOINTMENT_DURATION_MILLIS = 60 * 60_000L
private const val UTC_DAY_MILLIS = 24 * 60 * 60_000L
private val utc = TimeZone.getTimeZone("UTC")

internal data class AppointmentSlot(val time: String, val startsAtMillis: Long)

internal fun isValidAppointmentStart(start: Long): Boolean = start in 1..(Long.MAX_VALUE - APPOINTMENT_DURATION_MILLIS)

internal fun appointmentDateUtcMillis(
    instantMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long {
    val local = Calendar.getInstance(timeZone).apply { timeInMillis = instantMillis }
    return Calendar.getInstance(utc).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

internal fun firstAppointmentDateUtcMillis(
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long {
    val today = appointmentDateUtcMillis(nowMillis, timeZone)
    return if (appointmentTimeSlots(today, nowMillis, timeZone).isNotEmpty()) today else today + UTC_DAY_MILLIS
}

internal fun appointmentTimeSlots(
    dateUtcMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): List<AppointmentSlot> {
    val date = Calendar.getInstance(utc).apply { timeInMillis = dateUtcMillis }
    val local = Calendar.getInstance(timeZone)
    val earliestStart = nowMillis + APPOINTMENT_MINIMUM_NOTICE_MILLIS
    val year = date.get(Calendar.YEAR)
    val month = date.get(Calendar.MONTH)
    val day = date.get(Calendar.DAY_OF_MONTH)
    return buildList {
        for (minutes in 8 * 60..17 * 60 step 30) {
            val hour = minutes / 60
            val minute = minutes % 60
            local.clear()
            local.set(year, month, day, hour, minute, 0)
            val startsAt = local.timeInMillis
            if (startsAt >= earliestStart && isValidAppointmentStart(startsAt) &&
                local.get(Calendar.DAY_OF_MONTH) == day && local.get(Calendar.HOUR_OF_DAY) == hour &&
                local.get(Calendar.MINUTE) == minute
            ) {
                add(AppointmentSlot(String.format(Locale.ROOT, "%02d:%02d", hour, minute), startsAt))
            }
        }
    }
}
