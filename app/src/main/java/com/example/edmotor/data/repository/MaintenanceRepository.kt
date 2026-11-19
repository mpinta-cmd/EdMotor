package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.core.common.EdMotorException
import com.example.edmotor.core.validation.EdMotorValidation
import com.example.edmotor.data.local.AppointmentWorkWithMaintenance
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.MaintenanceDao
import com.example.edmotor.data.local.MaintenancePartDao
import com.example.edmotor.data.local.MaintenancePartEntity
import com.example.edmotor.data.local.MaintenanceRecordEntity
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.local.VehicleDao
import com.example.edmotor.model.MaintenanceDraft
import com.example.edmotor.model.MaintenancePartDraft
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MaintenanceRepository(
    private val database: EdMotorDatabase,
    private val maintenanceDao: MaintenanceDao,
    private val partDao: MaintenancePartDao,
    private val vehicleDao: VehicleDao,
    private val productDao: ProductDao,
    private val sessionManager: SessionManager
) {

    private fun requireActiveWorkshop(userId: Int): UserEntity =
        sessionManager.currentUser.value
            ?.takeIf { userId > 0 && it.id == userId && it.role == UserRole.WORKSHOP.name }
            ?: throw EdMotorException.PermissionDenied("Solo el taller puede realizar esta operación.")

    private suspend fun requireStoredWorkshop(workshop: UserEntity) {
        val stored = database.userDao().findById(workshop.id)
        if (stored == null || stored.role != UserRole.WORKSHOP.name ||
            stored.remoteAuthId != workshop.remoteAuthId ||
            stored.createdAtMillis != workshop.createdAtMillis
        ) {
            throw EdMotorException.PermissionDenied("Solo el taller puede realizar esta operación.")
        }
        requireCurrentSession(workshop)
    }

    private fun requireCurrentSession(workshop: UserEntity) {
        if (sessionManager.currentUser.value !== workshop) {
            throw EdMotorException.PermissionDenied("La sesión del taller cambió.")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeForVehicle(
        vehicleId: Int,
        requestingUserId: Int,
        requestingRole: String
    ): Flow<List<MaintenanceWithParts>> = sessionManager.currentUser.flatMapLatest { user ->
        if (vehicleId > 0 && requestingUserId > 0 && user?.id == requestingUserId && user.role == requestingRole) {
            maintenanceDao.observeByVehicle(vehicleId, requestingUserId, requestingRole).map { records ->
                if (sessionManager.currentUser.value === user) records else emptyList()
            }
        } else {
            flowOf(emptyList())
        }
    }.buffer(0).distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAppointmentWork(userId: Int, role: String): Flow<List<AppointmentWorkWithMaintenance>> =
        sessionManager.currentUser.flatMapLatest { user ->
            if (userId > 0 && user?.id == userId && user.role == role) {
                database.appointmentWorkDao().observeForViewer(userId, role).map { work ->
                    if (sessionManager.currentUser.value === user) work else emptyList()
                }
            } else {
                flowOf(emptyList())
            }
        }.buffer(0).distinctUntilChanged()

    suspend fun findWithParts(
        recordId: Int,
        vehicleId: Int,
        requestingUserId: Int
    ): MaintenanceWithParts? {
        val workshop = requireActiveWorkshop(requestingUserId)
        if (recordId <= 0 || vehicleId <= 0) {
            throw EdMotorException.Validation("Los datos del mantenimiento no son válidos.")
        }

        return database.withTransaction {
            requireStoredWorkshop(workshop)
            maintenanceDao.findWithParts(recordId, vehicleId).also { requireCurrentSession(workshop) }
        }
    }

    private suspend fun adjustCatalogStock(
        previousParts: List<MaintenancePartEntity>,
        newParts: List<MaintenancePartDraft>
    ) {
        val quantityChanges = mutableMapOf<Int, Long>()
        for (part in previousParts) {
            val productId = part.catalogProductId ?: continue
            if (!part.quantity.isFinite() || part.quantity < 1 || part.quantity % 1 != 0.0) {
                throw EdMotorException.Validation(
                    "No se puede ajustar el stock de ${part.name}: la cantidad registrada no es entera."
                )
            }

            // Las versiones anteriores descontaban con toInt(), que limita a Int.MAX_VALUE.
            quantityChanges[productId] = quantityChanges.getOrDefault(productId, 0L) - part.quantity.toInt()
        }
        for (part in newParts) {
            val productId = part.catalogProductId ?: continue
            quantityChanges[productId] = quantityChanges.getOrDefault(productId, 0L) + part.quantity.toInt()
        }

        for ((productId, quantityChange) in quantityChanges) {
            if (quantityChange == 0L) continue
            val product = productDao.findById(productId)
            if (product == null) {
                if (quantityChange < 0) continue
                throw EdMotorException.NotFound("El repuesto seleccionado ya no existe en el inventario.")
            }
            if (quantityChange > 0 && !product.isActive) {
                throw EdMotorException.Validation("${product.name} ya no está disponible en el inventario.")
            }
            val newStock = product.stock.toLong() - quantityChange
            if (newStock < 0) {
                throw EdMotorException.Validation(
                    "Stock insuficiente para ${product.name}. Necesitas $quantityChange unidades adicionales y hay ${product.stock}."
                )
            }
            if (newStock > Int.MAX_VALUE) {
                throw EdMotorException.Validation("Restituir ${product.name} supera el límite de existencias.")
            }
            if (productDao.updateStock(productId, newStock.toInt()) != 1) {
                throw EdMotorException.Validation(
                    "No fue posible actualizar el stock de ${product.name}."
                )
            }
        }
    }

    suspend fun saveMaintenance(
        workshopUserId: Int,
        vehicleId: Int,
        recordId: Int?,
        draft: MaintenanceDraft,
        appointmentWorkId: Long? = null
    ) {
        val workshop = requireActiveWorkshop(workshopUserId)

        if (vehicleId <= 0) {
            throw EdMotorException.Validation("Vehículo no válido.")
        }

        val validationError = EdMotorValidation.validateMaintenance(
            serviceType = draft.serviceType,
            serviceDate = draft.serviceDate,
            mileageKm = draft.mileageKm,
            laborCostCop = draft.laborCostCop,
            nextServiceMileageKm = draft.nextServiceMileageKm
        )
        if (validationError != null) throw EdMotorException.Validation(validationError)

        if (recordId != null && recordId <= 0) {
            throw EdMotorException.Validation("El identificador del mantenimiento no es válido.")
        }
        if (appointmentWorkId != null && appointmentWorkId <= 0L) {
            throw EdMotorException.Validation("La cita no es válida.")
        }

        val validParts = draft.parts.filter { it.name.isNotBlank() || it.catalogProductId != null }
        validParts.forEach { part ->
            val error = EdMotorValidation.validateMaintenancePart(
                name = part.name,
                quantity = part.quantity,
                unitPriceCop = part.unitPriceCop
            )
            if (error != null) throw EdMotorException.Validation(error)

            if (part.catalogProductId != null) {
                if (part.catalogProductId <= 0) {
                    throw EdMotorException.Validation("El repuesto seleccionado no es válido.")
                }
                if (part.quantity !in 1.0..Int.MAX_VALUE.toDouble() || part.quantity % 1.0 != 0.0) {
                    throw EdMotorException.Validation(
                        "La cantidad de un repuesto del inventario debe ser un entero entre 1 y ${Int.MAX_VALUE}."
                    )
                }
            }
        }

        database.withTransaction {
            requireStoredWorkshop(workshop)
            val vehicle = vehicleDao.findById(vehicleId)
                ?: throw EdMotorException.NotFound("No se encontró el vehículo.")

            val appointmentWork = appointmentWorkId?.let { workId ->
                val work = database.appointmentWorkDao().findById(workId)
                    ?: throw EdMotorException.NotFound("No se encontró la cita para este mantenimiento.")
                if (work.finishedAtMillis == null) {
                    throw EdMotorException.Validation("Finaliza la cita antes de registrar el mantenimiento.")
                }
                val client = vehicle.ownerUserId?.let { database.userDao().findById(it) }
                if (client == null || client.role != UserRole.CLIENT.name || client.remoteAuthId != work.clientAuthId) {
                    throw EdMotorException.PermissionDenied("El vehículo debe pertenecer al cliente de la cita.")
                }
                if (work.vehicleId != null && work.vehicleId != vehicleId) {
                    throw EdMotorException.Validation("El vehículo seleccionado no coincide con el de la cita.")
                }
                work
            }
            if (appointmentWork != null) {
                val linkedRecordId = appointmentWork.maintenanceRecordId
                if (recordId == null && linkedRecordId != null) {
                    requireCurrentSession(workshop)
                    return@withTransaction
                }
                if (recordId != linkedRecordId) {
                    throw EdMotorException.Validation("El mantenimiento seleccionado no coincide con el de la cita.")
                }
                if (linkedRecordId == null && draft.description.isBlank()) {
                    throw EdMotorException.Validation("Describe el trabajo realizado en el vehículo.")
                }
            }

            val existing = recordId?.let { id ->
                maintenanceDao.findWithParts(id, vehicleId)
                    ?: throw EdMotorException.NotFound(
                        "El mantenimiento no pertenece a este vehículo."
                    )
            }

            val serviceDate = draft.serviceDate.trim()
            val nextServiceDate = draft.nextServiceDate?.trim()?.takeIf { it.isNotEmpty() }
            if (serviceDate != existing?.record?.serviceDate && !EdMotorValidation.isValidMaintenanceDate(serviceDate)) {
                throw EdMotorException.Validation("Indica una fecha de mantenimiento válida (AAAA-MM-DD).")
            }
            if (nextServiceDate != null && nextServiceDate != existing?.record?.nextServiceDate &&
                !EdMotorValidation.isValidMaintenanceDate(nextServiceDate)
            ) {
                throw EdMotorException.Validation("Indica una próxima fecha válida (AAAA-MM-DD).")
            }

            adjustCatalogStock(existing?.parts.orEmpty(), validParts)

            val now = System.currentTimeMillis()
            val savedId = if (existing != null) {
                partDao.deleteForRecord(existing.record.id)
                maintenanceDao.update(
                    existing.record.copy(
                        updatedByUserId = workshopUserId,
                        serviceType = draft.serviceType.trim(),
                        serviceDate = serviceDate,
                        mileageKm = draft.mileageKm,
                        description = draft.description.trim(),
                        laborCostCop = draft.laborCostCop,
                        nextServiceMileageKm = draft.nextServiceMileageKm,
                        nextServiceDate = nextServiceDate,
                        observations = draft.observations.trim(),
                        updatedAtMillis = now
                    )
                )
                existing.record.id
            } else {
                val insertedId = maintenanceDao.insert(
                    MaintenanceRecordEntity(
                        vehicleId = vehicleId,
                        createdByUserId = workshopUserId,
                        serviceType = draft.serviceType.trim(),
                        serviceDate = serviceDate,
                        mileageKm = draft.mileageKm,
                        description = draft.description.trim(),
                        laborCostCop = draft.laborCostCop,
                        nextServiceMileageKm = draft.nextServiceMileageKm,
                        nextServiceDate = nextServiceDate,
                        observations = draft.observations.trim(),
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                Math.toIntExact(insertedId)
            }

            val parts = validParts.map { part ->
                MaintenancePartEntity(
                    maintenanceRecordId = savedId,
                    catalogProductId = part.catalogProductId,
                    name = part.name.trim(),
                    brand = part.brand.trim(),
                    reference = part.reference.trim(),
                    specification = part.specification.trim(),
                    quantity = part.quantity,
                    unit = part.unit.trim().ifBlank { "unidad" },
                    unitPriceCop = part.unitPriceCop
                )
            }
            if (parts.isNotEmpty()) partDao.insertAll(parts)

            if (appointmentWork != null && appointmentWork.maintenanceRecordId == null) {
                val linked = appointmentWork.copy(vehicleId = vehicle.id, maintenanceRecordId = savedId)
                if (database.appointmentWorkDao().update(linked) != 1) {
                    throw EdMotorException.NotFound("No fue posible vincular el mantenimiento con la cita.")
                }
            }

            val currentKm = vehicle.mileage.filter { it.isDigit() }.toIntOrNull() ?: 0
            if (draft.mileageKm >= currentKm) {
                vehicleDao.updateMileage(vehicle.id, "${draft.mileageKm} km")
            }
            requireCurrentSession(workshop)
        }
    }

    suspend fun deleteMaintenance(
        recordId: Int,
        vehicleId: Int,
        workshopUserId: Int
    ) {
        val workshop = requireActiveWorkshop(workshopUserId)
        if (recordId <= 0 || vehicleId <= 0) {
            throw EdMotorException.Validation("El mantenimiento seleccionado no es válido.")
        }

        database.withTransaction {
            requireStoredWorkshop(workshop)
            val existing = maintenanceDao.findWithParts(recordId, vehicleId)
                ?: throw EdMotorException.NotFound("El mantenimiento no pertenece a este vehículo.")
            adjustCatalogStock(existing.parts, emptyList())

            val deleted = maintenanceDao.deleteByIdForVehicle(
                recordId = recordId,
                vehicleId = vehicleId
            )
            if (deleted == 0) {
                throw EdMotorException.NotFound("No fue posible eliminar el mantenimiento.")
            }
            requireCurrentSession(workshop)
        }
    }
}
