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
    fun profilesOnlySeeTheirAppointmentsAndOverlappingConfirmationsAreSerialized() = runBlocking {
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
            val repository = AppointmentRepository(database.appointmentDao(), null, session, database)
            val client = insertProfile(database, "clienta", "local-account:39b2f81e-a715-48d9-a739-265dc200a0db", UserRole.CLIENT)
            val other = insertProfile(database, "clientb", "local-account:aa3d8659-7763-4f4b-8675-31e9082db250", UserRole.CLIENT)
            val workshop = insertProfile(database, "owner", "local-account:owner", UserRole.WORKSHOP)
            val firstVehicle = insertVehicle(database, client.id, "Modelo A")
            val secondVehicle = insertVehicle(database, other.id, "Modelo B")
            val start = System.currentTimeMillis() + 24 * 60 * 60_000L
            val service = AppointmentServiceType.options.first()

            session.selectProfile(client)
            assertTrue(repository.createAppointment(client, firstVehicle, start, service) is AppointmentOperationResult.Success)
            val clientAccount = requireNotNull(repository.accountFor(client))
            val first = withTimeout(5_000L) { repository.observeAppointments(clientAccount).first().appointments.single() }
            assertEquals("Modelo A", first.vehicleModel)
            assertEquals(AppointmentStatus.PENDING, first.status)
            assertTrue(repository.acceptAppointment(client, first.id) is AppointmentOperationResult.Error)

            session.selectProfile(other)
            assertTrue(repository.createAppointment(other, firstVehicle.copy(ownerUserId = other.id), start, service) is AppointmentOperationResult.Error)
            assertTrue(repository.createAppointment(other, secondVehicle, start + 30 * 60_000L, service) is AppointmentOperationResult.Success)
            val otherAccount = requireNotNull(repository.accountFor(other))
            val second = withTimeout(5_000L) { repository.observeAppointments(otherAccount).first().appointments.single() }
            assertEquals("Modelo B", second.vehicleModel)
            assertTrue(withTimeout(5_000L) { repository.observeAppointments(clientAccount).first().appointments.isEmpty() })

            session.selectProfile(workshop)
            val workshopAccount = requireNotNull(repository.accountFor(workshop))
            val agenda = withTimeout(5_000L) { repository.observeAppointments(workshopAccount).first().appointments }
            assertEquals(listOf(first.id, second.id), agenda.map { it.id })
            val approvals = awaitAll(
                async { repository.acceptAppointment(workshop, first.id) },
                async { repository.acceptAppointment(workshop, second.id) }
            )
            assertEquals(1, approvals.count { it is AppointmentOperationResult.Success })
            val current = withTimeout(5_000L) { repository.observeAppointments(workshopAccount).first().appointments }
            assertEquals(1, current.count { it.status == AppointmentStatus.CONFIRMED })
            val pending = current.single { it.status == AppointmentStatus.PENDING }
            assertTrue(repository.rejectAppointment(workshop, pending.id) is AppointmentOperationResult.Success)
            assertTrue(repository.acceptAppointment(workshop, pending.id) is AppointmentOperationResult.Error)
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
