package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 DAO: Data Access Object

 CRUD:
 C - CREATE
 R - READ
 U - UPDATE
 D - DELETE
*/

@Dao
interface VehicleDao {

    // READ: Obtener todos los vehículos
    @Query("SELECT * FROM vehicles ORDER BY id DESC")
    fun observeAll(): Flow<List<VehicleEntity>>

    // READ: Buscar un vehículo por su ID
    @Query("SELECT * FROM vehicles WHERE id = :vehicleId LIMIT 1")
    suspend fun findById(vehicleId: Int): VehicleEntity?

    // CREATE: Insertar un vehículo
    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun insert(vehicle: VehicleEntity)

    // UPDATE: Actualizar un vehículo
    @Update
    suspend fun update(vehicle: VehicleEntity)

    // DELETE: Eliminar un vehículo
    @Delete
    suspend fun delete(vehicle: VehicleEntity)
}