package com.example.edmotor.viewmodel

import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.session.PickupEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppointmentPickupNoticeTest {
    @Test
    fun onlyValidCompletionsForTheActiveClientCanBecomeTheNotice() {
        val first = completed(id = 1, createdAt = 100, finishedAt = 200)
        val newest = completed(id = 2, createdAt = 105, finishedAt = 300)
        val otherClient = completed(id = 3, createdAt = 110, finishedAt = 500)
            .copy(clientAuthId = "another-client")
        val confirmed = completed(id = 4, createdAt = 111, finishedAt = 600)
            .copy(status = AppointmentStatus.CONFIRMED)
        val reversedTimestamps = completed(id = 5, createdAt = 900, finishedAt = 800)
        val invalidId = completed(id = 0, createdAt = 113, finishedAt = 950)

        assertEquals(newest, latestPickupNotice(
            listOf(first, newest, otherClient, confirmed, reversedTimestamps, invalidId), CLIENT_KEY
        ))
        assertNull(latestPickupNotice(listOf(otherClient, confirmed), CLIENT_KEY))
    }

    @Test
    fun dismissalDoesNotRevealOlderAppointmentsAndAllowsANewCompletion() {
        val first = completed(id = 5, createdAt = 100, finishedAt = 200)
        val second = completed(id = 5, createdAt = 150, finishedAt = 300)
        val dismissed = checkNotNull(PickupEvent.from(second))

        assertNull(latestPickupNotice(listOf(first, second), CLIENT_KEY, dismissed))
        assertNull(latestPickupNotice(listOf(first), CLIENT_KEY, dismissed))
        assertNull(latestPickupNotice(listOf(second.copy(updatedAtMillis = 350)), CLIENT_KEY, dismissed))

        val third = completed(id = 8, createdAt = 170, finishedAt = 400)
        assertEquals(third, latestPickupNotice(listOf(first, second, third), CLIENT_KEY, dismissed))
    }

    @Test
    fun equalFinishTimesAreOrderedByAppointmentIdentity() {
        val earlier = completed(id = 90, createdAt = 100, finishedAt = 350)
        val smallerId = completed(id = 6, createdAt = 200, finishedAt = 350)
        val largerId = completed(id = 3_000_000_001, createdAt = 200, finishedAt = 350)
        val dismissed = checkNotNull(PickupEvent.from(smallerId))

        assertEquals(largerId, latestPickupNotice(listOf(largerId, earlier, smallerId), CLIENT_KEY, dismissed))
        assertEquals(largerId, latestPickupNotice(listOf(smallerId, earlier, largerId), CLIENT_KEY, dismissed))
    }

    private fun completed(id: Long, createdAt: Long, finishedAt: Long) = Appointment(
        id = id,
        clientAuthId = CLIENT_KEY,
        clientName = "Cliente",
        clientUsername = "cliente",
        clientPhone = null,
        vehicleBrand = "Mazda",
        vehicleModel = "2",
        vehicleYear = 2018,
        vehiclePlate = "TEST1",
        scheduledAtMillis = 150,
        description = "Cambio de aceite",
        status = AppointmentStatus.COMPLETED,
        createdAtMillis = createdAt,
        updatedAtMillis = finishedAt
    )

    private companion object {
        const val CLIENT_KEY = "client:test"
    }
}
