package com.example.edmotor.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.local.VehicleEntity
import com.example.edmotor.model.AppointmentOperationResult
import com.example.edmotor.model.AppointmentServiceType
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.session.SessionManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAppointmentsTest {
    @Test
    fun profilesManageTheirAppointmentsAndTheWorkshopCanConfirmOverlappingTimes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EdMotorDatabase::class.java).build()
        val sessionFile = File.createTempFile("local-appointments-", ".preferences_pb", context.cacheDir)
        sessionFile.delete()
        val storeJob = SupervisorJob()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(storeJob + Dispatchers.IO),
            produceFile = { sessionFile }
        )

        try {
            val session = SessionManager(dataStore)
            val dao = database.appointmentDao()
            val repository = AppointmentRepository(dao, null, session, database)
            val client = insertProfile(database, "clienta", "local-account:39b2f81e-a715-48d9-a739-265dc200a0db", UserRole.CLIENT)
            val other = insertProfile(database, "clientb", "local-account:aa3d8659-7763-4f4b-8675-31e9082db250", UserRole.CLIENT)
            val workshop = insertProfile(database, "owner", "local-account:owner", UserRole.WORKSHOP)
            val firstVehicle = insertVehicle(database, client.id, "Modelo A")
            val alternateVehicle = insertVehicle(database, client.id, "Modelo Alterno")
            val secondVehicle = insertVehicle(database, other.id, "Modelo B")
            val start = System.currentTimeMillis() + 24 * 60 * 60_000L
            val service = AppointmentServiceType.options.first()

            session.selectProfile(client)
            val submissions = awaitAll(
                async { repository.createAppointment(client, firstVehicle, start, service) },
                async { repository.createAppointment(client, alternateVehicle, start + 15 * 60_000L, service) }
            )
            assertEquals(1, submissions.count { it is AppointmentOperationResult.Success })
            val denied = submissions.single { it is AppointmentOperationResult.Error } as AppointmentOperationResult.Error
            assertTrue(denied.message.contains("cita activa"))
            val clientAccount = requireNotNull(repository.accountFor(client))
            val first = withTimeout(5_000L) { repository.observeAppointments(clientAccount).first().appointments.single() }
            assertEquals(AppointmentStatus.PENDING, first.status)
            assertTrue(repository.acceptAppointment(client, first.id) is AppointmentOperationResult.Error)

            session.selectProfile(other)
            assertTrue(repository.createAppointment(other, firstVehicle.copy(ownerUserId = other.id), start, service) is AppointmentOperationResult.Error)
            assertTrue(repository.createAppointment(other, secondVehicle, start + 30 * 60_000L, service) is AppointmentOperationResult.Success)
            val otherAccount = requireNotNull(repository.accountFor(other))
            val second = withTimeout(5_000L) { repository.observeAppointments(otherAccount).first().appointments.single() }
            assertEquals("Modelo B", second.vehicleModel)
            assertTrue(withTimeout(5_000L) { repository.observeAppointments(clientAccount).first().appointments.isEmpty() })
            assertTrue(repository.cancelAppointment(other, first.id) is AppointmentOperationResult.Error)
            assertTrue(repository.rescheduleAppointment(other, first.id, start + 60 * 60_000L) is AppointmentOperationResult.Error)

            session.selectProfile(workshop)
            val workshopAccount = requireNotNull(repository.accountFor(workshop))
            val agenda = withTimeout(5_000L) { repository.observeAppointments(workshopAccount).first().appointments }
            assertEquals(listOf(first.id, second.id), agenda.map { it.id })
            assertTrue(repository.rescheduleAppointment(workshop, first.id, second.scheduledAtMillis) is AppointmentOperationResult.Success)
            val adjustedRequest = requireNotNull(dao.findLocalAppointment(first.id))
            assertEquals(second.scheduledAtMillis, adjustedRequest.scheduledAtMillis)
            assertEquals(AppointmentStatus.PENDING, adjustedRequest.status)

            val approvals = awaitAll(
                async { repository.acceptAppointment(workshop, first.id) },
                async { repository.acceptAppointment(workshop, second.id) }
            )
            assertTrue(approvals.all { it is AppointmentOperationResult.Success })
            val current = withTimeout(5_000L) { repository.observeAppointments(workshopAccount).first().appointments }
            assertEquals(2, current.count { it.status == AppointmentStatus.CONFIRMED })
            assertTrue(current.all { it.scheduledAtMillis == second.scheduledAtMillis })

            val workshopStart = start + 60 * 60_000L
            assertTrue(repository.rescheduleAppointment(workshop, second.id, workshopStart) is AppointmentOperationResult.Success)
            val updatedBooking = requireNotNull(dao.findLocalAppointment(second.id))
            assertEquals(AppointmentStatus.CONFIRMED, updatedBooking.status)
            assertEquals(workshopStart, updatedBooking.scheduledAtMillis)

            val expired = System.currentTimeMillis() - 60_000L
            assertEquals(1, dao.updateLocalAppointment(
                appointmentId = first.id,
                previousStatus = AppointmentStatus.CONFIRMED,
                status = AppointmentStatus.CONFIRMED,
                scheduledAtMillis = expired,
                updatedAtMillis = System.currentTimeMillis()
            ))
            val expiredAppointment = requireNotNull(dao.findLocalAppointment(first.id))
            val legacyDuplicate = dao.insertLocalAppointment(expiredAppointment.copy(id = 0))

            session.selectProfile(client)
            assertTrue(repository.createAppointment(client, firstVehicle, workshopStart, service) is AppointmentOperationResult.Error)
            assertTrue(repository.cancelAppointment(client, legacyDuplicate) is AppointmentOperationResult.Success)
            assertTrue(repository.createAppointment(client, firstVehicle, workshopStart, service) is AppointmentOperationResult.Error)
            val soon = System.currentTimeMillis() + 5 * 60_000L
            assertTrue(repository.rescheduleAppointment(client, first.id, soon) is AppointmentOperationResult.Error)
            val clientStart = start + 90 * 60_000L
            assertTrue(repository.rescheduleAppointment(client, first.id, clientStart) is AppointmentOperationResult.Success)
            val rescheduled = withTimeout(5_000L) {
                repository.observeAppointments(clientAccount).first().appointments.single { it.id == first.id }
            }
            assertEquals(AppointmentStatus.PENDING, rescheduled.status)
            assertEquals(clientStart, rescheduled.scheduledAtMillis)
            assertEquals(first.createdAtMillis, rescheduled.createdAtMillis)
            assertTrue(repository.cancelAppointment(client, first.id) is AppointmentOperationResult.Success)
            assertEquals(AppointmentStatus.CANCELLED, dao.findLocalAppointment(first.id)?.status)
            assertTrue(repository.rescheduleAppointment(client, first.id, workshopStart) is AppointmentOperationResult.Error)

            assertTrue(repository.createAppointment(client, firstVehicle, workshopStart, service) is AppointmentOperationResult.Success)
            session.selectProfile(workshop)
            assertTrue(repository.cancelAppointment(workshop, second.id) is AppointmentOperationResult.Success)
            assertEquals(AppointmentStatus.CANCELLED, dao.findLocalAppointment(second.id)?.status)
            val updatedAgenda = withTimeout(5_000L) { repository.observeAppointments(workshopAccount).first().appointments }
            val pending = updatedAgenda.single { it.status == AppointmentStatus.PENDING }
            assertTrue(repository.rejectAppointment(workshop, pending.id) is AppointmentOperationResult.Success)
            assertEquals(AppointmentStatus.REJECTED, dao.findLocalAppointment(pending.id)?.status)
            assertTrue(repository.acceptAppointment(workshop, pending.id) is AppointmentOperationResult.Error)

            session.selectProfile(client)
            assertTrue(repository.createAppointment(client, firstVehicle, workshopStart, service) is AppointmentOperationResult.Success)
            val next = withTimeout(5_000L) {
                repository.observeAppointments(clientAccount).first().appointments.single { it.status == AppointmentStatus.PENDING }
            }
            session.selectProfile(workshop)
            assertTrue(repository.finishAppointment(workshop, next.id, next) is AppointmentOperationResult.Error)
            assertTrue(repository.acceptAppointment(workshop, next.id) is AppointmentOperationResult.Success)
            session.selectProfile(client)
            assertTrue(repository.finishAppointment(client, next.id, next) is AppointmentOperationResult.Error)
            session.selectProfile(workshop)
            val reusedId = next.copy(createdAtMillis = next.createdAtMillis + 1)
            assertTrue(repository.finishAppointment(workshop, next.id, reusedId) is AppointmentOperationResult.Error)

            val beforeFinish = System.currentTimeMillis()
            val result = repository.finishAppointment(workshop, next.id, next) as AppointmentOperationResult.Success
            val afterFinish = System.currentTimeMillis()
            val closed = withTimeout(5_000L) {
                repository.observeAppointments(workshopAccount).first().appointments.single { it.id == next.id }
            }
            assertEquals(AppointmentStatus.COMPLETED, closed.status)
            assertEquals(next.scheduledAtMillis, closed.scheduledAtMillis)
            val closedAt = requireNotNull(closed.finishedAtMillis)
            assertTrue(closedAt in beforeFinish..afterFinish)
            val work = requireNotNull(database.appointmentWorkDao().findById(requireNotNull(result.workId)))
            assertEquals(next.clientAuthId, work.clientAuthId)
            assertEquals(next.id, work.appointmentId)
            assertEquals(next.createdAtMillis, work.appointmentCreatedAtMillis)
            assertEquals(firstVehicle.id, work.vehicleId)
            assertEquals(closedAt, work.finishedAtMillis)

            delay(25L)
            val retry = repository.finishAppointment(workshop, next.id, closed) as AppointmentOperationResult.Success
            assertEquals(work.id, retry.workId)
            assertEquals(closedAt, dao.findLocalAppointment(next.id)?.updatedAtMillis)
            assertEquals(work, database.appointmentWorkDao().findById(work.id))
            assertTrue(repository.cancelAppointment(workshop, next.id) is AppointmentOperationResult.Error)

            session.selectProfile(client)
            assertTrue(repository.createAppointment(client, alternateVehicle, workshopStart, service) is AppointmentOperationResult.Success)
            val active = withTimeout(5_000L) {
                repository.observeAppointments(clientAccount).first().appointments.single { AppointmentStatus.isActive(it.status) }
            }
            assertEquals(alternateVehicle.model, active.vehicleModel)
        } finally {
            storeJob.cancelAndJoin()
            database.close()
            sessionFile.delete()
        }
    }

    private suspend fun insertProfile(database: EdMotorDatabase, username: String, key: String, role: UserRole): UserEntity {
        val user = UserEntity(
            username = username,
            displayName = username,
            passwordHash = "fixture-hash",
            salt = "fixture-salt",
            role = role.name,
            remoteAuthId = key
        )
        return user.copy(id = database.userDao().insert(user).toInt())
    }

    private suspend fun insertVehicle(database: EdMotorDatabase, ownerId: Int, model: String): Vehicle {
        val stored = VehicleEntity(brand = "Marca", model = model, year = 2020, engine = "Motor", ownerUserId = ownerId)
        val id = database.vehicleDao().insert(stored).toInt()
        return Vehicle(id = id, brand = stored.brand, model = stored.model, year = stored.year, engine = stored.engine, ownerUserId = ownerId)
    }
}
