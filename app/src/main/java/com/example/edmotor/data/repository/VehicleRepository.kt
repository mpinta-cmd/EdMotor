
package com.example.edmotor.data.repository

import com.example.edmotor.core.validation.EdMotorValidation

import com.example.edmotor.data.local.VehicleDao
import com.example.edmotor.data.local.VehicleEntity

import com.example.edmotor.model.Vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VehicleRepository(
    private val dao: VehicleDao
) {

    // =====================================================
    // VEHÍCULOS DEL TALLER
    // =====================================================

    fun observeForWorkshop(): Flow<List<Vehicle>> =
        dao.observeAllForWorkshop().map { rows ->

            rows.map { row ->

                row.vehicle.toModel(
                    row.ownerName,
                    row.ownerUsername
                )
            }
        }


    // =====================================================
    // VEHÍCULOS DEL CLIENTE
    // =====================================================

    fun observeByOwner(
        ownerUserId: Int
    ): Flow<List<Vehicle>> {

        if (ownerUserId <= 0) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        return dao.observeByOwner(ownerUserId).map { entities ->

            entities.map {
                it.toModel()
            }
        }
    }


    // =====================================================
    // AGREGAR VEHÍCULO
    // =====================================================

    suspend fun addVehicle(
        ownerUserId: Int,
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String = "",
        mileage: String = "",
        notes: String = ""
    ): Long {
        require(ownerUserId > 0) { "Inicia sesión para registrar un vehículo." }

        EdMotorValidation.validateVehicle(
            brand = brand,
            model = model,
            year = year
        )?.let { throw IllegalArgumentException(it) }

        val mileageInput = mileage.trim()
        val cleanMileage = if (mileageInput.isBlank()) {
            ""
        } else {
            val mileageValue = mileageInput.replace(Regex("\\s*km$", RegexOption.IGNORE_CASE), "")
                .toIntOrNull()
            require(mileageValue != null && mileageValue >= 0) {
                "Escribe un kilometraje válido en kilómetros o deja el campo vacío."
            }
            "$mileageValue km"
        }

        return dao.insert(
            VehicleEntity(
                brand = brand.trim(),
                model = model.trim(),
                year = year,
                engine = engine.trim(),
                plate = plate.trim().uppercase(),
                mileage = cleanMileage,
                notes = notes.trim(),
                ownerUserId = ownerUserId
            )
        )
    }


    // =====================================================
    // ACTUALIZAR VEHÍCULO
    // =====================================================

    suspend fun updateVehicleForOwner(
        ownerUserId: Int,
        vehicle: Vehicle
    ) {

        if (ownerUserId <= 0 || vehicle.id <= 0) return

        val error = EdMotorValidation.validateVehicle(
            brand = vehicle.brand,
            model = vehicle.model,
            year = vehicle.year
        )

        if (error != null) return

        dao.updateForOwner(
            vehicleId = vehicle.id,
            ownerUserId = ownerUserId,
            brand = vehicle.brand.trim(),
            model = vehicle.model.trim(),
            year = vehicle.year,
            engine = vehicle.engine.trim(),
            plate = vehicle.plate.trim().uppercase(),
            notes = vehicle.notes.trim()
        )
    }


    // =====================================================
    // ACTUALIZAR KILOMETRAJE
    // =====================================================

    suspend fun updateMileageFromMaintenance(
        vehicleId: Int,
        mileageKm: Int
    ) {

        if (vehicleId <= 0 || mileageKm < 0) return

        val current = dao.findById(vehicleId) ?: return

        val previous = current.mileage
            .filter { it.isDigit() }
            .toIntOrNull() ?: 0

        if (mileageKm >= previous) {

            dao.update(
                current.copy(
                    mileage = "$mileageKm km"
                )
            )
        }
    }


    // =====================================================
    // ASIGNAR VEHÍCULO A CLIENTE
    // =====================================================

    suspend fun assignVehicleToClient(
        vehicleId: Int,
        clientUserId: Int
    ): Boolean {

        if (vehicleId <= 0 || clientUserId <= 0) {
            return false
        }

        return dao.assignOwner(
            vehicleId,
            clientUserId
        ) > 0
    }


    // =====================================================
    // CONVERTIR ENTITY A MODEL
    // =====================================================

    private fun VehicleEntity.toModel(
        ownerName: String? = null,
        ownerUsername: String? = null
    ) = Vehicle(

        id = id,
        brand = brand,
        model = model,
        year = year,
        engine = engine,
        plate = plate,
        mileage = mileage,
        notes = notes,
        ownerUserId = ownerUserId,
        ownerName = ownerName,
        ownerUsername = ownerUsername
    )
}
