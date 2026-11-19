package com.example.edmotor.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.edmotor.core.common.EdMotorException
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.MaintenancePartEntity
import com.example.edmotor.data.local.MaintenanceRecordEntity
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.local.VehicleEntity
import com.example.edmotor.model.MaintenanceDraft
import com.example.edmotor.model.MaintenancePartDraft
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaintenanceRepositoryTest {
    @Test
    fun retiredPartsRemainEditableAndStockChangesOnlyByNetQuantity() = runBlocking {
        withFixture {
            val client = insertUser("client", UserRole.CLIENT)
            val vehicle = insertVehicle(client.id)
            val otherVehicle = insertVehicle(client.id)
            val product = insertProduct("Bujía", stock = 10)
            val draft = maintenanceDraft().copy(parts = listOf(part(product, 2.0), part(product, 3.0)))

            repository.saveMaintenance(workshop.id, vehicle.id, null, draft)
            val id = records(vehicle.id, workshop).single().record.id
            assertEquals(5, stock(product))
            assertEquals("200 km", database.vehicleDao().findById(vehicle.id)?.mileage)
            database.productDao().deactivate(product)

            repository.saveMaintenance(workshop.id, vehicle.id, id, draft.copy(mileageKm = 150, description = "Revisado"))
            val edited = records(vehicle.id, workshop).single()
            assertEquals("Revisado", edited.record.description)
            assertEquals(listOf(2.0, 3.0), edited.parts.map { it.quantity }.sorted())
            assertEquals(5, stock(product))
            assertEquals("200 km", database.vehicleDao().findById(vehicle.id)?.mileage)

            expectFailure<EdMotorException.Validation> {
                repository.saveMaintenance(workshop.id, vehicle.id, id, draft.copy(parts = listOf(part(product, 6.0))))
            }
            assertEquals(5, stock(product))
            repository.saveMaintenance(workshop.id, vehicle.id, id, draft.copy(parts = listOf(part(product, 3.0))))
            assertEquals(7, stock(product))

            expectFailure<EdMotorException.NotFound> {
                repository.deleteMaintenance(id, otherVehicle.id, workshop.id)
            }
            assertEquals(7, stock(product))
            repository.deleteMaintenance(id, vehicle.id, workshop.id)
            assertEquals(10, stock(product))
            assertTrue(records(vehicle.id, workshop).isEmpty())
        }
    }

    @Test
    fun validationAndInventoryFailureLeaveRecordVehicleAndStockUnchanged() = runBlocking {
        withFixture {
            val vehicle = insertVehicle(insertUser("client", UserRole.CLIENT).id)
            val first = insertProduct("Bujía", stock = 5)
            val second = insertProduct("Filtro", stock = 1)
            val draft = maintenanceDraft()

            expectFailure<EdMotorException.Validation> {
                repository.saveMaintenance(workshop.id, vehicle.id, null, draft.copy(serviceDate = "2025-02-29"))
            }
            expectFailure<EdMotorException.Validation> {
                repository.saveMaintenance(workshop.id, vehicle.id, null, draft.copy(nextServiceDate = "2026-11-31"))
            }
            expectFailure<EdMotorException.Validation> {
                repository.saveMaintenance(
                    workshop.id, vehicle.id, null,
                    draft.copy(parts = listOf(part(first, Int.MAX_VALUE.toDouble() + 1)))
                )
            }
            expectFailure<EdMotorException.Validation> {
                repository.saveMaintenance(
                    workshop.id, vehicle.id, null,
                    draft.copy(parts = listOf(part(first, -1.0).copy(name = " ")))
                )
            }
            expectFailure<EdMotorException.Validation> {
                repository.saveMaintenance(
                    workshop.id, vehicle.id, null,
                    draft.copy(parts = listOf(part(first, 1.0), part(second, 2.0)))
                )
            }
            assertEquals(5, stock(first))
            assertEquals(1, stock(second))
            assertEquals("100 km", database.vehicleDao().findById(vehicle.id)?.mileage)
            assertTrue(records(vehicle.id, workshop).isEmpty())
        }
    }

    @Test
    fun legacyDateAndPreviouslyClampedInventoryCanBeRecovered() = runBlocking {
        withFixture {
            val vehicle = insertVehicle(insertUser("client", UserRole.CLIENT).id)
            val product = insertProduct("Bujía", stock = 0)
            val recordId = database.maintenanceDao().insert(
                MaintenanceRecordEntity(
                    vehicleId = vehicle.id,
                    createdByUserId = workshop.id,
                    serviceType = "Revisión",
                    serviceDate = "2025-02-30",
                    mileageKm = 150,
                    description = "Dato previo"
                )
            ).toInt()
            database.maintenancePartDao().insertAll(
                listOf(
                    MaintenancePartEntity(
                        maintenanceRecordId = recordId,
                        catalogProductId = product,
                        name = "Bujía",
                        quantity = Int.MAX_VALUE.toDouble() + 1
                    )
                )
            )

            repository.saveMaintenance(workshop.id, vehicle.id, recordId, maintenanceDraft().copy(serviceDate = "2025-02-30"))
            assertEquals("2025-02-30", records(vehicle.id, workshop).single().record.serviceDate)
            assertEquals(Int.MAX_VALUE, stock(product))
        }
    }

    @Test
    fun observationRevokesAccessWhenOwnerRoleOrActiveProfileChanges() = runBlocking {
        withFixture {
            val client = insertUser("client", UserRole.CLIENT)
            val other = insertUser("other", UserRole.CLIENT)
            val vehicle = insertVehicle(client.id)
            repository.saveMaintenance(workshop.id, vehicle.id, null, maintenanceDraft())
            val recordId = records(vehicle.id, workshop).single().record.id
            session.selectProfile(client)

            coroutineScope {
                val history = repository.observeForVehicle(vehicle.id, client.id, client.role).produceIn(this)
                try {
                    assertEquals(recordId, withTimeout(5_000L) { history.receive() }.single().record.id)
                    database.vehicleDao().update(vehicle.copy(ownerUserId = other.id))
                    assertTrue(withTimeout(5_000L) { history.receive() }.isEmpty())
                    assertTrue(records(vehicle.id, other).isEmpty())
                    expectFailure<EdMotorException.PermissionDenied> {
                        repository.deleteMaintenance(recordId, vehicle.id, workshop.id)
                    }

                    session.selectProfile(other)
                    assertEquals(recordId, records(vehicle.id, other).single().record.id)
                    session.selectProfile(workshop)
                    database.userDao().update(workshop.copy(role = UserRole.CLIENT.name))
                    assertTrue(records(vehicle.id, workshop).isEmpty())
                    expectFailure<EdMotorException.PermissionDenied> {
                        repository.findWithParts(recordId, vehicle.id, workshop.id)
                    }
                } finally {
                    history.cancel()
                }
            }
        }
    }

    private fun maintenanceDraft() = MaintenanceDraft(
        serviceType = "Revisión general",
        serviceDate = "2024-02-29",
        nextServiceDate = "2026-12-31",
        mileageKm = 200,
        description = "Se revisó el motor.",
        laborCostCop = 30_000L
    )

    private fun part(productId: Int, quantity: Double) =
        MaintenancePartDraft(catalogProductId = productId, name = "Repuesto", quantity = quantity)

    private suspend inline fun <reified T : Exception> expectFailure(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("Se esperaba ${T::class.java.simpleName} y se obtuvo $error", error is T)
    }

    private suspend fun withFixture(test: suspend Fixture.() -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EdMotorDatabase::class.java).build()
        val file = File.createTempFile("maintenance-repository-", ".preferences_pb", context.cacheDir)
        file.delete()
        val storeJob = SupervisorJob()
        val session = SessionManager(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(storeJob + Dispatchers.IO),
                produceFile = { file }
            )
        )

        try {
            val fixture = Fixture(database, session)
            fixture.workshop = fixture.insertUser("owner", UserRole.WORKSHOP)
            session.selectProfile(fixture.workshop)
            fixture.test()
        } finally {
            storeJob.cancelAndJoin()
            database.close()
            file.delete()
        }
    }

    private class Fixture(val database: EdMotorDatabase, val session: SessionManager) {
        lateinit var workshop: UserEntity
        val repository = MaintenanceRepository(
            database, database.maintenanceDao(), database.maintenancePartDao(),
            database.vehicleDao(), database.productDao(), session
        )

        suspend fun insertUser(username: String, role: UserRole): UserEntity {
            val profile = UserEntity(
                username = username, displayName = username, passwordHash = "fixture-hash",
                salt = "fixture-salt", role = role.name, remoteAuthId = "local-account:$username"
            )
            return profile.copy(id = database.userDao().insert(profile).toInt())
        }

        suspend fun insertVehicle(ownerId: Int): VehicleEntity {
            val vehicle = VehicleEntity(
                brand = "Marca", model = "Modelo", year = 2020, engine = "Motor",
                mileage = "100 km", ownerUserId = ownerId
            )
            return vehicle.copy(id = database.vehicleDao().insert(vehicle).toInt())
        }

        suspend fun insertProduct(name: String, stock: Int): Int = database.productDao().insert(
            ProductEntity(name = name, description = "", category = "Repuestos", priceCop = 1L,
                imageRes = 0, contentDescription = name, stock = stock)
        ).toInt()

        suspend fun stock(id: Int) = requireNotNull(database.productDao().findById(id)).stock

        suspend fun records(vehicleId: Int, user: UserEntity) = withTimeout(5_000L) {
            repository.observeForVehicle(vehicleId, user.id, user.role).first()
        }
    }
}
