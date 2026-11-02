
package com.example.edmotor.data.repository

import com.example.edmotor.data.local.VehicleDao
import com.example.edmotor.data.local.VehicleEntity
import com.example.edmotor.model.Vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class VehicleRepository(
    private val dao: VehicleDao
) {

    // ==========================================
    // 1. READ: OBTENER TODOS LOS VEHÍCULOS
    // ==========================================

    val vehicles: Flow<List<Vehicle>> =
        dao.observeAll().map { entities ->

            entities.map { entity ->
                entity.toModel()
            }

        }


    // ==========================================
    // 2. CREATE: AGREGAR VEHÍCULO
    // ==========================================

    suspend fun addVehicle(
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String = "",
        mileage: String = "",
        notes: String = ""
    ) {

        val cleanBrand = brand.trim()
        val cleanModel = model.trim()

        if (cleanBrand.isBlank() || cleanModel.isBlank()) {
            return
        }

        val entity = VehicleEntity(
            brand = cleanBrand,
            model = cleanModel,
            year = year,
            engine = engine.trim(),
            plate = plate.trim(),
            mileage = mileage.trim(),
            notes = notes.trim()
        )

        dao.insert(entity)
    }


    // ==========================================
    // 3. UPDATE: ACTUALIZAR VEHÍCULO COMPLETO
    // ==========================================

    suspend fun updateVehicle(vehicle: Vehicle) {

        val entity = dao.findById(vehicle.id) ?: return

        dao.update(
            entity.copy(
                brand = vehicle.brand,
                model = vehicle.model,
                year = vehicle.year,
                engine = vehicle.engine,
                plate = vehicle.plate,
                mileage = vehicle.mileage,
                notes = vehicle.notes
            )
        )
    }


    // ==========================================
    // 4. UPDATE: ACTUALIZAR NOTAS
    // ==========================================

    suspend fun updateVehicleNotes(
        vehicleId: Int,
        newNotes: String
    ) {

        val entity = dao.findById(vehicleId) ?: return

        dao.update(
            entity.copy(
                notes = newNotes.trim()
            )
        )
    }


    // ==========================================
    // 5. UPDATE: ACTUALIZAR KILOMETRAJE
    // ==========================================

    suspend fun updateVehicleMileage(
        vehicleId: Int,
        newMileage: String
    ) {

        val entity = dao.findById(vehicleId) ?: return

        val formattedMileage =
            if (
                newMileage.isNotBlank() &&
                !newMileage.contains("km", ignoreCase = true)
            ) {
                "${newMileage.trim()} km"
            } else {
                newMileage.trim()
            }

        dao.update(
            entity.copy(
                mileage = formattedMileage
            )
        )
    }


    // ==========================================
    // 6. DELETE: ELIMINAR VEHÍCULO POR ID
    // ==========================================

    suspend fun deleteVehicle(vehicleId: Int) {

        val entity = dao.findById(vehicleId) ?: return

        dao.delete(entity)
    }


    // ==========================================
    // 7. CONVERSIÓN ENTITY → MODEL
    // ==========================================

    private fun VehicleEntity.toModel(): Vehicle {

        return Vehicle(
            id = id,
            brand = brand,
            model = model,
            year = year,
            engine = engine,
            plate = plate,
            mileage = mileage,
            notes = notes
        )
    }

}
