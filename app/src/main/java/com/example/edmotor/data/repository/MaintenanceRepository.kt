package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.core.common.EdMotorException
import com.example.edmotor.core.validation.EdMotorValidation
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.MaintenanceDao
import com.example.edmotor.data.local.MaintenancePartDao
import com.example.edmotor.data.local.MaintenancePartEntity
import com.example.edmotor.data.local.MaintenanceRecordEntity
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.data.local.VehicleDao
import com.example.edmotor.model.MaintenanceDraft
import com.example.edmotor.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class MaintenanceRepository(
    private val database: EdMotorDatabase,
    private val maintenanceDao: MaintenanceDao,
    private val partDao: MaintenancePartDao,
    private val vehicleDao: VehicleDao,
    private val productDao: ProductDao
) {

    private suspend fun requireWorkshop(userId: Int) {
        if (userId <= 0) throw EdMotorException.PermissionDenied()

        val user = database.userDao().findById(userId)
            ?: throw EdMotorException.PermissionDenied()

        if (user.role != UserRole.WORKSHOP.name) {
            throw EdMotorException.PermissionDenied(
                "Solo el taller puede realizar esta operación."
            )
        }
    }

    fun observeForVehicle(
        vehicleId: Int,
        requestingUserId: Int
    ): Flow<List<MaintenanceWithParts>> = flow {
        if (vehicleId <= 0 || requestingUserId <= 0) {
            emit(emptyList())
            return@flow
        }

        val user = database.userDao().findById(requestingUserId)
        val vehicle = vehicleDao.findById(vehicleId)
        val allowed = user != null && vehicle != null &&
                (user.role == UserRole.WORKSHOP.name || vehicle.ownerUserId == user.id)

        if (allowed) emitAll(maintenanceDao.observeByVehicle(vehicleId))
        else emit(emptyList())
    }

    suspend fun findWithParts(
        recordId: Int,
        vehicleId: Int,
        requestingUserId: Int
    ): MaintenanceWithParts? {
        requireWorkshop(requestingUserId)
        if (recordId <= 0 || vehicleId <= 0) {
            throw EdMotorException.Validation("Los datos del mantenimiento no son válidos.")
        }

        val record = maintenanceDao.findByIdForVehicle(recordId, vehicleId)
            ?: return null
        return maintenanceDao.findWithParts(record.id)
    }

    /** Restituye al inventario los productos que ya estaban asociados al mantenimiento. */
    private suspend fun restoreCatalogStock(parts: List<MaintenancePartEntity>) {
        parts.forEach { part ->
            val productId = part.catalogProductId ?: return@forEach
            val quantity = part.quantity.toInt()
            if (quantity <= 0 || quantity.toDouble() != part.quantity) {
                throw EdMotorException.Validation(
                    "No se puede restituir el stock de ${part.name}: la cantidad registrada no es entera."
                )
            }

            val product = productDao.findById(productId) ?: return@forEach
            val restoredStock = product.stock.toLong() + quantity
            if (restoredStock > Int.MAX_VALUE) {
                throw EdMotorException.Validation("Restituir ${part.name} supera el límite de existencias.")
            }
            val rows = productDao.updateStock(
                productId = productId,
                newStock = restoredStock.toInt()
            )
            if (rows != 1) {
                throw EdMotorException.Validation(
                    "No fue posible restituir las existencias de ${product.name}."
                )
            }
        }
    }

    suspend fun saveMaintenance(
        workshopUserId: Int,
        vehicleId: Int,
        recordId: Int?,
        draft: MaintenanceDraft
    ) {
        requireWorkshop(workshopUserId)

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

        val validParts = draft.parts.filter { it.name.isNotBlank() }
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
                if (part.quantity < 1.0 || part.quantity % 1.0 != 0.0) {
                    throw EdMotorException.Validation(
                        "La cantidad de un repuesto del inventario debe ser un número entero."
                    )
                }
            }
        }

        database.withTransaction {
            val vehicle = vehicleDao.findById(vehicleId)
                ?: throw EdMotorException.NotFound("No se encontró el vehículo.")

            val existing = recordId?.let { id ->
                maintenanceDao.findByIdForVehicle(id, vehicleId)
                    ?: throw EdMotorException.NotFound(
                        "El mantenimiento no pertenece a este vehículo."
                    )
            }

            // Si se edita un mantenimiento, primero se devuelve al inventario
            // lo que se había consumido en el registro anterior. La transacción
            // revierte esta restitución si falla cualquier validación posterior.
            if (existing != null) {
                val previousParts = maintenanceDao.findWithParts(existing.id)?.parts.orEmpty()
                restoreCatalogStock(previousParts)
                partDao.deleteForRecord(existing.id)
            }

            // Descontar el stock de los repuestos seleccionados del catálogo.
            // Los materiales escritos manualmente (catalogProductId = null)
            // quedan registrados, pero no afectan el inventario.
            validParts.forEach { part ->
                val productId = part.catalogProductId ?: return@forEach
                val quantity = part.quantity.toInt()
                val product = productDao.findById(productId)
                    ?: throw EdMotorException.NotFound(
                        "El repuesto seleccionado ya no existe en el inventario."
                    )

                if (!product.isActive) {
                    throw EdMotorException.Validation(
                        "${product.name} ya no está disponible en el inventario."
                    )
                }
                if (product.stock < quantity) {
                    throw EdMotorException.Validation(
                        "Stock insuficiente para ${product.name}. Necesitas $quantity y hay ${product.stock}."
                    )
                }

                val rows = productDao.updateStock(
                    productId = product.id,
                    newStock = product.stock - quantity
                )
                if (rows != 1) {
                    throw EdMotorException.Validation(
                        "No fue posible actualizar el stock de ${product.name}."
                    )
                }
            }

            val now = System.currentTimeMillis()
            val savedId = if (existing != null) {
                maintenanceDao.update(
                    existing.copy(
                        updatedByUserId = workshopUserId,
                        serviceType = draft.serviceType.trim(),
                        serviceDate = draft.serviceDate.trim(),
                        mileageKm = draft.mileageKm,
                        description = draft.description.trim(),
                        laborCostCop = draft.laborCostCop,
                        nextServiceMileageKm = draft.nextServiceMileageKm,
                        nextServiceDate = draft.nextServiceDate?.trim()?.takeIf { it.isNotEmpty() },
                        observations = draft.observations.trim(),
                        updatedAtMillis = now
                    )
                )
                existing.id
            } else {
                maintenanceDao.insert(
                    MaintenanceRecordEntity(
                        vehicleId = vehicleId,
                        createdByUserId = workshopUserId,
                        serviceType = draft.serviceType.trim(),
                        serviceDate = draft.serviceDate.trim(),
                        mileageKm = draft.mileageKm,
                        description = draft.description.trim(),
                        laborCostCop = draft.laborCostCop,
                        nextServiceMileageKm = draft.nextServiceMileageKm,
                        nextServiceDate = draft.nextServiceDate?.trim()?.takeIf { it.isNotEmpty() },
                        observations = draft.observations.trim(),
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                ).toInt()
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

            val currentKm = vehicle.mileage.filter { it.isDigit() }.toIntOrNull() ?: 0
            if (draft.mileageKm >= currentKm) {
                vehicleDao.update(vehicle.copy(mileage = "${draft.mileageKm} km"))
            }
        }
    }

    suspend fun deleteMaintenance(
        recordId: Int,
        workshopUserId: Int
    ) {
        requireWorkshop(workshopUserId)
        if (recordId <= 0) {
            throw EdMotorException.Validation("El mantenimiento seleccionado no es válido.")
        }

        database.withTransaction {
            val record = maintenanceDao.findById(recordId)
                ?: throw EdMotorException.NotFound("No se encontró el mantenimiento.")

            val withParts = maintenanceDao.findWithParts(recordId)
            restoreCatalogStock(withParts?.parts.orEmpty())

            val deleted = maintenanceDao.deleteByIdForVehicle(
                recordId = recordId,
                vehicleId = record.vehicleId
            )
            if (deleted == 0) {
                throw EdMotorException.NotFound("No fue posible eliminar el mantenimiento.")
            }
        }
    }
}
