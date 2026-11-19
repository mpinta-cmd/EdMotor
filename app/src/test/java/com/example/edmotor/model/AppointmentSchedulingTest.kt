package com.example.edmotor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AppointmentSchedulingTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
    private val bogota = TimeZone.getTimeZone("America/Bogota")

    @Test
    fun datePickerKeepsLocalDayWhenUtcIsAlreadyTomorrow() {
        val localNow = instant(losAngeles, day = 19, hour = 16, minute = 10)
        val localDayInPicker = instant(utc, day = 19, hour = 0, minute = 0)

        assertEquals(localDayInPicker, appointmentDateUtcMillis(localNow, losAngeles))
        assertEquals(localDayInPicker, firstAppointmentDateUtcMillis(localNow, losAngeles))
        assertEquals(listOf("16:30", "17:00"), appointmentTimeSlots(localDayInPicker, localNow, losAngeles).map { it.time })
    }

    @Test
    fun lastSlotAllowsExactlyFifteenMinutesAndRollsOverAfter() {
        val today = instant(utc, day = 19, hour = 0, minute = 0)
        val limit = instant(bogota, day = 19, hour = 16, minute = 45)

        assertEquals(listOf("17:00"), appointmentTimeSlots(today, limit, bogota).map { it.time })
        assertTrue(appointmentTimeSlots(today, limit + 1, bogota).isEmpty())
        assertEquals(instant(utc, day = 20, hour = 0, minute = 0), firstAppointmentDateUtcMillis(limit + 1, bogota))
    }

    private fun instant(zone: TimeZone, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        }.timeInMillis
}
