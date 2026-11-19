package com.example.edmotor.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.edmotor.model.AppointmentStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppointmentWorkMigrationTest {
    @Test
    fun migratesVersionTenAndKeepsExistingAppointmentsAndTimes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "appointment-migration-${UUID.randomUUID()}.db"

        try {
            val appointment = seedAppointment(context, name)
            val file = context.getDatabasePath(name)
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
                // Version 11 adds only this table and its indexes to the version 10 schema.
                sqlite.execSQL("DROP TABLE appointment_work")
                sqlite.execSQL("UPDATE room_master_table SET identity_hash = ? WHERE id = 42", arrayOf(V10_IDENTITY))
                sqlite.version = 10
            }

            val migrated = openDatabase(context, name)
            try {
                val appointmentId = appointment.id.toLong()
                assertEquals(appointment, migrated.appointmentDao().findLocalAppointment(appointmentId))
                assertEquals(11, migrated.openHelper.writableDatabase.version)

                val workDao = migrated.appointmentWorkDao()
                val work = AppointmentWorkEntity(
                    clientAuthId = appointment.clientAuthId,
                    appointmentId = appointmentId,
                    appointmentCreatedAtMillis = appointment.createdAtMillis
                )
                assertNull(workDao.findForAppointment(work.clientAuthId, appointmentId, appointment.createdAtMillis))
                val workId = workDao.insertIfAbsent(work)
                assertTrue(workId > 0L)
                assertEquals(work.copy(id = workId), workDao.findById(workId))
                assertEquals(-1L, workDao.insertIfAbsent(work))
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun seedAppointment(context: Context, name: String): AppointmentEntity {
        val database = openDatabase(context, name)
        try {
            val appointment = AppointmentEntity(
                clientLocalUserId = 12,
                clientAuthId = "local-account:migration-fixture",
                clientName = "Cliente de prueba",
                clientUsername = "cliente-prueba",
                vehicleBrand = "Marca",
                vehicleModel = "Modelo",
                vehicleYear = 2021,
                vehiclePlate = "",
                scheduledAtMillis = 1_800_000_000_000L,
                description = "Mantenimiento",
                status = AppointmentStatus.CONFIRMED,
                createdAtMillis = 1_799_000_000_000L,
                updatedAtMillis = 1_799_100_000_000L
            )
            val id = database.appointmentDao().insertLocalAppointment(appointment).toInt()
            return appointment.copy(id = id)
        } finally {
            database.close()
        }
    }

    private fun openDatabase(context: Context, name: String): EdMotorDatabase =
        Room.databaseBuilder(context, EdMotorDatabase::class.java, name)
            .addMigrations(*EdMotorMigrations.ALL)
            .build()

    private companion object {
        const val V10_IDENTITY = "0fc6e5e60234dc9058fd8f05d5af4199"
    }
}
