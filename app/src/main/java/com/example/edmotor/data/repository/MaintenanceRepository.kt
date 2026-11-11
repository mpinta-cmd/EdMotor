
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
    private val vehicleDao: VehicleDao
) {

    // =====================================================
    // VERIFICAR CUENTA DEL TALLER
    // =====================================================

    private suspend fun requireWorkshop(userId: Int) {

        if (userId <= 0) {
            throw EdMotorException.PermissionDenied()
        }

        val user = database.userDao().findById(userId)
            ?: throw EdMotorException.PermissionDenied()

        if (user.role != UserRole.WORKSHOP.name) {
            throw EdMotorException.PermissionDenied(
                "Solo el taller puede realizar esta operación."
            )
        }
    }


    // =====================================================
    // CONSULTAR HISTORIAL
    // =====================================================

    fun observeForVehicle(
        vehicleId: Int,
        requestingUserId: Int
    ): Flow<List<MaintenanceWithParts>> = flow {

        if (vehicleId <= 0 || requestingUserId <= 0) {
            emit(emptyList<MaintenanceWithParts>())
            return@flow
        }

        val user = database.userDao().findById(requestingUserId)

        val vehicle = vehicleDao.findById(vehicleId)

        val allowed = user != null &&
                vehicle != null &&
                (
                        user.role == UserRole.WORKSHOP.name ||
                                vehicle.ownerUserId == user.id
                        )

        if (allowed) {

            emitAll(
                maintenanceDao.observeByVehicle(vehicleId)
            )

        } else {

            emit(emptyList<MaintenanceWithParts>())
        }
    }


    // =====================================================
    // BUSCAR MANTENIMIENTO PARA EDICIÓN
    // =====================================================

    suspend fun findWithParts(
        recordId: Int,
        vehicleId: Int,
        requestingUserId: Int
    ): MaintenanceWithParts? {

        requireWorkshop(requestingUserId)

        if (recordId <= 0 || vehicleId <= 0) {
            throw EdMotorException.Validation(
                "Los datos del mantenimiento no son válidos."
            )
        }

        val record = maintenanceDao.findByIdForVehicle(
            recordId = recordId,
            vehicleId = vehicleId
        ) ?: return null

        return maintenanceDao.findWithParts(record.id)
    }


    // =====================================================
    // GUARDAR MANTENIMIENTO
    // =====================================================

    suspend fun saveMaintenance(
        workshopUserId: Int,
        vehicleId: Int,
        recordId: Int?,
        draft: MaintenanceDraft
    ) {

        requireWorkshop(workshopUserId)

        if (vehicleId <= 0) {
            throw EdMotorException.Validation(
                "Vehículo no válido."
            )
        }

        val validationError = EdMotorValidation.validateMaintenance(
            serviceType = draft.serviceType,
            serviceDate = draft.serviceDate,
            mileageKm = draft.mileageKm,
            laborCostCop = draft.laborCostCop,
            nextServiceMileageKm = draft.nextServiceMileageKm
        )

        if (validationError != null) {
            throw EdMotorException.Validation(validationError)
        }

        if (recordId != null && recordId <= 0) {
            throw EdMotorException.Validation(
                "El identificador del mantenimiento no es válido."
            )
        }

        val validParts = draft.parts.filter {
            it.name.isNotBlank()
        }

        validParts.forEach { part ->

            val error = EdMotorValidation.validateMaintenancePart(
                name = part.name,
                quantity = part.quantity,
                unitPriceCop = part.unitPriceCop
            )

            if (error != null) {
                throw EdMotorException.Validation(error)
            }
        }

        database.withTransaction {

            val vehicle = vehicleDao.findById(vehicleId)
                ?: throw EdMotorException.NotFound(
                    "No se encontró el vehículo."
                )

            val now = System.currentTimeMillis()

            val existing = recordId?.let { id ->

                maintenanceDao.findByIdForVehicle(
                    recordId = id,
                    vehicleId = vehicleId
                ) ?: throw EdMotorException.NotFound(
                    "El mantenimiento no pertenece a este vehículo."
                )
            }

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
                        nextServiceDate = draft.nextServiceDate
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        observations = draft.observations.trim(),
                        updatedAtMillis = now
                    )
                )

                partDao.deleteForRecord(existing.id)

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
                        nextServiceDate = draft.nextServiceDate
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        observations = draft.observations.trim(),
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                ).toInt()
            }


            // ---------------------------------------------
            // GUARDAR REPUESTOS
            // ---------------------------------------------

            val parts = validParts.map { part ->

                MaintenancePartEntity(
                    maintenanceRecordId = savedId,
                    catalogProductId = part.catalogProductId,
                    name = part.name.trim(),
                    brand = part.brand.trim(),
                    reference = part.reference.trim(),
                    specification = part.specification.trim(),
                    quantity = part.quantity,
                    unit = part.unit.trim().ifBlank {
                        "unidad"
                    },
                    unitPriceCop = part.unitPriceCop
                )
            }

            if (parts.isNotEmpty()) {
                partDao.insertAll(parts)
            }


            // ---------------------------------------------
            // ACTUALIZAR KILOMETRAJE
            // ---------------------------------------------

            val currentKm = vehicle.mileage
                .filter { it.isDigit() }
                .toIntOrNull() ?: 0

            if (draft.mileageKm >= currentKm) {

                vehicleDao.update(
                    vehicle.copy(
                        mileage = "${draft.mileageKm} km"
                    )
                )
            }
        }
    }


    // =====================================================
    // ELIMINAR MANTENIMIENTO
    // =====================================================

    suspend fun deleteMaintenance(
        recordId: Int,
        workshopUserId: Int
    ) {

        requireWorkshop(workshopUserId)

        if (recordId <= 0) {
            throw EdMotorException.Validation(
                "El mantenimiento seleccionado no es válido."
            )
        }

        database.withTransaction {

            val record = maintenanceDao.findById(recordId)
                ?: throw EdMotorException.NotFound(
                    "No se encontró el mantenimiento."
                )

            val deleted = maintenanceDao.deleteByIdForVehicle(
                recordId = recordId,
                vehicleId = record.vehicleId
            )

            if (deleted == 0) {
                throw EdMotorException.NotFound(
                    "No fue posible eliminar el mantenimiento."
                )
            }
        }
    }
}
