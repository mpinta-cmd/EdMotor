package com.example.edmotor.online

import java.util.UUID
import retrofit2.HttpException

internal class OnlineAppointmentRepository(
    private val service: OnlineService,
    private val auth: String
) {
    private val pendingBookings = mutableMapOf<Booking, String>()

    suspend fun list(): List<OnlineAppointment> = service.appointments(auth)

    suspend fun create(vehicleId: Long, scheduledAtMillis: Long, serviceType: String): OnlineAppointment {
        val booking = Booking(vehicleId, scheduledAtMillis, serviceType)
        // A failed response can arrive after the booking was committed. Reuse the ID on retry.
        val requestId = pendingBookings.getOrPut(booking) { UUID.randomUUID().toString() }
        return try {
            service.createAppointment(
                auth, OnlineAppointmentWrite(requestId, vehicleId, scheduledAtMillis, serviceType)
            ).also { pendingBookings.remove(booking) }
        } catch (error: HttpException) {
            if (error.code() in 400..499 && error.code() != 408) pendingBookings.remove(booking)
            throw error
        }
    }

    suspend fun update(appointment: OnlineAppointment, action: String, scheduledAtMillis: Long?): OnlineAppointment =
        service.changeAppointment(
            auth, appointment.id, OnlineAppointmentChange(appointment.version, action, scheduledAtMillis)
        )

    suspend fun delete(appointment: OnlineAppointment) =
        service.cancelAppointment(auth, appointment.id, appointment.version)

    private data class Booking(val vehicleId: Long, val scheduledAtMillis: Long, val serviceType: String)
}
